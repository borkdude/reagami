(ns server
  (:require
   [app]
   [babashka.nrepl.server :as nrepl]
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [org.httpkit.server :as http]
   [reagami.ssr :as ssr]))

(def total (or (some-> (System/getenv "ROWS") parse-long) 1000000))

;; only the first window is rendered and sent. the rest of the million rows are
;; produced on demand by app/row when the client scrolls to them.
(def initial-state
  (let [to (+ (quot app/viewport app/row-height) app/overscan)]
    {:total total :from 0 :rows (mapv app/row (range 0 to))}))

;; the slider sets this. LATENCY only picks the starting value. blocks an
;; http-kit worker, which is fine for one browser and wrong for anything real.
(defonce !latency (atom (or (some-> (System/getenv "LATENCY") parse-long) 20)))

(defn- slow! []
  (let [ms @!latency]
    (when (pos? ms)
      (Thread/sleep (+ (quot ms 2) (rand-int ms))))))

;; edits belong to the data, not to a tab, so they outlive any session. stands in
;; for a database. every session reads them, so a refresh still shows them.
(defonce db (atom {}))

(defn- apply-action
  "Runs an action against session state, with the shared edits laid in and
  written back. Sessions never carry :edits themselves. :latency is server state
  the client can see and set, so it rides along on every push."
  [state action]
  (when (= "latency" (:type action))
    (reset! !latency (min 100 (max 0 (:ms action)))))
  (let [next (app/handle (assoc state :edits @db) action)]
    (reset! db (:edits next))
    (assoc (dissoc next :edits) :latency @!latency)))

;; one entry per browser tab: {sid {:state ... :channel ...}}. the page render
;; creates it, the SSE stream attaches to it, closing the stream drops it. a
;; page fetched but never streamed leaks its entry, so a real app wants a TTL.
(defonce sessions (atom {}))

(def ^:private vite "http://localhost:5173")

(defn- client-tags
  ;; vite's own index.html gets these injected by the squint plugin. this page
  ;; comes from here instead, so it emits them: the plugin's REPL listener, and
  ;; the compiled entry namespace.
  [dev?]
  (if dev?
    (list [:script {:type "module" :src (str vite "/@vite/client")}]
          [:script {:type "module" :src (str vite "/@id/__x00__virtual:squint-repl-client")}]
          [:script {:type "module" :src (str vite "/out/client.mjs")}])
    [:script {:type "module" :src "/client.js"}]))

(def ^:private style
  (str ".row,.ghost{display:flex;gap:1rem;align-items:center;width:100%;"
       "box-sizing:border-box;font:13px ui-monospace,Menlo,monospace}"
       ".cell{flex:0 0 auto;overflow:hidden;white-space:nowrap}"
       "span.cell{cursor:text;border-bottom:1px dotted transparent}"
       "span.cell:hover{border-bottom-color:#bbb}"
       "input.cell{font:inherit;border:1px solid #666;padding:0;margin:0;"
       "background:#fff;color:inherit}"
       ".cell-name{width:7rem}.cell-owner{width:5rem}.cell-region{width:4rem}"
       ".cell-qty{width:2rem}.cell-price{width:4rem}.cell-score{width:3rem}"
       ".cell-tag{width:4rem}.cell-updated{width:6rem}.cell-status{width:4rem}"
       ".ghost{color:#bbb}"
       ".shimmer{flex:1;height:8px;border-radius:4px;"
       "background:linear-gradient(90deg,#eee,#f8f8f8,#eee);"
       "background-size:200% 100%;animation:sh 1.1s linear infinite}"
       "@keyframes sh{0%{background-position:200% 0}100%{background-position:-200% 0}}"
       ".overlay{position:sticky;top:0;height:0;z-index:2}"
       ".spinner{position:absolute;top:10px;right:14px;width:18px;height:18px;"
       "border:2px solid #ddd;border-top-color:#555;border-radius:50%;"
       "animation:spin .7s linear infinite}"
       "@keyframes spin{to{transform:rotate(360deg)}}"
       "@media(prefers-reduced-motion:reduce){.shimmer,.spinner{animation:none}}"
       ".row.done{color:#888}.row.new{font-weight:600}"
       "#scroller{border:1px solid #ccc}"
       "a.open{flex:0 0 auto;width:3rem;font-size:11px}"
       "#detail{display:grid;grid-template-columns:8rem 1fr;gap:.35rem 1rem;max-width:32rem}"
       "#detail .label{color:#888;font-size:12px}"
       ".field{display:contents}"))

(defn- state-json
  ;; </script> inside the data would end the tag early, and <\/ is the same
  ;; string once JSON is parsed
  [state]
  (str/replace (json/generate-string state) "</" "<\\/"))

;; the whole page goes through reagami.ssr, so the island is nested rather than
;; rendered separately and spliced in as a string. :innerHTML is the escape
;; hatch for content that must not be escaped, since script and style are raw
;; text to the HTML parser and entities inside them would not be decoded.
(defn page
  ([dev?] (page dev? nil))
  ([dev? open-id]
   (let [sid (str (random-uuid))
        to (+ (quot app/viewport app/row-height) app/overscan)
        state (apply-action initial-state {:type "window" :from 0 :to to})
        ;; a direct hit on /row/42 is rendered by the server, same components
        state (if open-id (apply-action state {:type "open" :id open-id}) state)]
    (swap! sessions assoc sid {:state state})
    (str "<!doctype html>"
         (ssr/render
          [:html {:lang "en"}
           [:head
            [:meta {:charset "utf-8"}]
            [:title "reagami ssr"]
            [:style {:innerHTML style}]]
           [:body {:data-sid sid}
            [:div#app [app/app state]]
            ;; the debug panel is client only, so the server leaves its root empty
            [:div#debug]
            [:script {:type "application/json" :id "state"
                      :innerHTML (state-json state)}]
            (client-tags dev?)]])))))

(defn- asset [path]
  (let [f (io/file "dist" (subs path 1))]
    (when (.isFile f)
      {:status 200
       :headers {"Content-Type" (if (.endsWith path ".js")
                                  "text/javascript"
                                  "text/plain")}
       :body (slurp f)})))

(defn- sse [state]
  ;; :edits is the server's record of what was changed. it only grows, and the
  ;; client already sees the result baked into :rows, so it does not go over.
  (str "data: " (json/generate-string (dissoc state :edits)) "\n\n"))

(defn- state-stream [req sid]
  (http/as-channel req
    {:on-open (fn [ch]
                (swap! sessions assoc-in [sid :channel] ch)
                (http/send! ch {:status 200
                                :headers {"Content-Type" "text/event-stream"
                                          "Cache-Control" "no-cache"}
                                :body (sse (get-in @sessions [sid :state]))}
                            false))
     :on-close (fn [_ _] (swap! sessions dissoc sid))}))

(defn- action [req]
  (slow!)
  (let [{:keys [sid action]} (json/parse-string (slurp (:body req)) true)
        session (get (swap! sessions update-in [sid :state] apply-action action) sid)]
    (when-let [ch (:channel session)]
      (http/send! ch (sse (:state session)) false))
    {:status 204}))

(defn- html [body]
  {:status 200 :headers {"Content-Type" "text/html"} :body body})

(defn handler [dev? req]
  (let [path (:uri req)]
    (cond
      (= "/" path) (html (page dev?))
      (str/starts-with? path "/row/") (html (page dev? (parse-long (subs path 5))))
      (str/starts-with? path "/state/") (state-stream req (subs path 7))
      (= "/action" path) (action req)
      (= "/favicon.ico" path) {:status 204}
      (not dev?) (or (asset path) {:status 404 :body "not found"})
      :else {:status 404 :body "not found"})))

(defn start! [{:keys [dev? port nrepl-port]
               :or {dev? true port 8080 nrepl-port 1667}}]
  (http/run-server (partial handler dev?) {:port port})
  (nrepl/start-server! {:port nrepl-port})
  (println (str "ssr server  http://localhost:" port))
  (println (str "nrepl       localhost:" nrepl-port))
  (when dev?
    (println "vite        http://localhost:5173 (assets only, do not open)")
    (println (str "open        http://localhost:" port
                  " , or http://localhost:8081 to go through brotli"))))

(defn -main [& args]
  (start! {:dev? (not= "prod" (first args))})
  @(promise))
