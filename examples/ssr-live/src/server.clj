(ns server
  (:require
   [app]
   [babashka.fs :as fs]
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

;; edits belong to the data, not to a tab, so they outlive a session. this
;; atom is the database of this demo.
(defonce db (atom {}))

(defn- apply-action
  "Runs an action against session state. The shared edits merge in before and
  write back after, so sessions never carry :edits."
  [state action]
  (let [next (app/handle (assoc state :edits @db) action)]
    (reset! db (:edits next))
    (dissoc next :edits)))

;; one entry per browser tab: {sid {:state ... :channel ... :created ...}}
(defonce sessions (atom {}))

;; a page that is fetched but never opens its event stream leaves an entry
;; behind, and a crawler makes one per visit. only entries without a stream
;; need sweeping.
(def ^:private session-ttl-ms (* 5 60 1000))
(def ^:private sweep-every-ms 60000)

(defn- expired? [now session]
  (and (nil? (:channel session))
       (< (:created session 0) (- now session-ttl-ms))))

(defn sweep-sessions!
  "Drops sessions that never streamed and are past the TTL. Returns the count."
  []
  (let [now (System/currentTimeMillis)
        before (count @sessions)]
    (swap! sessions #(into {} (remove (fn [[_ s]] (expired? now s))) %))
    (- before (count @sessions))))

(defonce ^:private sweeper
  (delay (future (loop []
                   (Thread/sleep sweep-every-ms)
                   (try (sweep-sessions!) (catch Exception _ nil))
                   (recur)))))

(def ^:private vite "http://localhost:5173")

(defn- client-src
  "Path of the built client. Vite puts a content hash in the name, so it is
  looked up rather than hard coded."
  []
  (if-let [f (first (fs/glob "dist" "client-*.js"))]
    (str "/" (fs/file-name f))
    "/client.js"))

(defn- client-tags
  ;; Vite injects these into its own index.html. this page is rendered here
  ;; and emits them itself.
  [dev?]
  (if dev?
    (list [:script {:type "module" :src (str vite "/@vite/client")}]
          [:script {:type "module" :src (str vite "/@id/__x00__virtual:squint-repl-client")}]
          [:script {:type "module" :src (str vite "/out/client.mjs")}])
    [:script {:type "module" :src (client-src)}]))

(defn- state-json
  ;; </script> inside the data ends the tag early. <\/ is the same string
  ;; once JSON is parsed.
  [state]
  (str/replace (json/generate-string state) "</" "<\\/"))

(defn- fresh-state []
  (let [to (+ (quot app/viewport app/row-height) app/overscan)]
    (apply-action initial-state {:type "window" :from 0 :to to})))

;; the whole page goes through reagami.ssr. :innerHTML is the raw output for
;; content that must not be escaped: the HTML parser never decodes entities
;; inside script and style.
(defn page [dev?]
  (let [sid (str (random-uuid))
        state (fresh-state)]
    (swap! sessions assoc sid {:state state :created (System/currentTimeMillis)})
    (str "<!doctype html>"
         (ssr/render
          [:html {:lang "en"}
           [:head
            [:meta {:charset "utf-8"}]
            [:meta {:name "viewport"
                    :content "width=device-width, initial-scale=1"}]
            [:title "reagami ssr"]
            ;; read per request, so editing the css only needs a reload
            [:style {:innerHTML (slurp (io/resource "app.css"))}]]
           [:body {:data-sid sid}
            [:div#app [app/app state]]
            ;; the status line is client only, so the server leaves its root empty
            [:div#status]
            [:script {:type "application/json" :id "state"
                      :innerHTML (state-json state)}]
            (client-tags dev?)]]))))

(defn- asset [path]
  ;; the name only: a crafted path must not reach outside dist/
  (let [f (fs/file "dist" (fs/file-name path))]
    (when (fs/regular-file? f)
      {:status 200
       :headers {"Content-Type" (if (.endsWith path ".js")
                                  "text/javascript"
                                  "text/plain")
                 ;; the name carries a content hash and can be kept forever
                 "Cache-Control" "public, max-age=31536000, immutable"}
       :body (slurp f)})))

(defn- sse [state]
  ;; :edits is the server's record of what changed. it only grows, and the
  ;; client already sees the result in :rows.
  (str "data: " (json/generate-string (dissoc state :edits)) "\n\n"))

(defn- state-stream [req sid]
  (http/as-channel req
    {:on-open (fn [ch]
                ;; EventSource reconnects with the same sid after a drop,
                ;; and the session can be gone by then. Seed a new one rather
                ;; than stream null, because null wipes the client.
                (let [session (-> (swap! sessions update sid
                                         (fn [s]
                                           (-> (or s {:state (fresh-state)
                                                      :created (System/currentTimeMillis)})
                                               (assoc :channel ch))))
                                  (get sid))]
                  (http/send! ch {:status 200
                                  :headers {"Content-Type" "text/event-stream"
                                            "Cache-Control" "no-cache"}
                                  :body (sse (:state session))}
                              false)))
     ;; keep the state so a reconnect finds it. the sweeper reclaims it once the
     ;; tab stays gone.
     :on-close (fn [_ _]
                 (swap! sessions
                        (fn [m]
                          (if (contains? m sid)
                            (update m sid #(-> (dissoc % :channel)
                                               (assoc :created (System/currentTimeMillis))))
                            m))))}))

(defn- push! [session]
  (when-let [ch (:channel session)]
    (http/send! ch (sse (:state session)) false)))

(defn- apply-shared
  "Runs another tab's action against this session. The edit is already in db,
  so this only brings the rows of that session up to date."
  [state action]
  (dissoc (app/handle (assoc state :edits @db) action) :edits))

(defn- broadcast!
  "Sends an action from one tab to every other tab. Each tab holds a different
  window, so every session is patched and pushed its own state rather than one
  shared blob."
  [from-sid action]
  (doseq [sid (keys @sessions)
          :when (not= sid from-sid)]
    (push! (get (swap! sessions update-in [sid :state] apply-shared action) sid))))

(defn- action [req]
  (let [{:keys [sid action]} (json/parse-string (slurp (:body req)) true)
        ;; a tab that was open across a restart posts a sid this process has
        ;; never seen. seed a session rather than applying the action to nil.
        session (get (swap! sessions update sid
                            (fn [s]
                              (update (or s {:state (fresh-state)
                                             :created (System/currentTimeMillis)})
                                      :state apply-action action)))
                     sid)]
    (push! session)
    ;; edits are shared data, so the other tabs see them without a reload
    (when (= "edit" (:type action))
      (broadcast! sid action))
    ;; nothing to return: the new state arrives over the event stream
    {:status 204}))

(defn- html [body]
  ;; never cache the page: it names the hashed client and carries a session id,
  ;; so a stale copy points at an asset that is gone and shares its session
  {:status 200
   :headers {"Content-Type" "text/html"
             "Cache-Control" "no-store"}
   :body body})

(defn handler [dev? req]
  (let [path (:uri req)
        resp (cond
               (= "/" path) (html (page dev?))
               (str/starts-with? path "/state/") (state-stream req (subs path 7))
               (= "/action" path) (action req)
               (= "/favicon.ico" path) {:status 204}
               (not dev?) (or (asset path) {:status 404 :body "not found"})
               :else {:status 404 :body "not found"})]
    ;; http-kit writes the body even for HEAD. HTTP does not allow that, and
    ;; strict clients reject it.
    (if (and (= :head (:request-method req)) (string? (:body resp)))
      (dissoc resp :body)
      resp)))

(defn start! [{:keys [dev? port nrepl-port ip]
               :or {dev? true port 8080 nrepl-port 1667 ip "127.0.0.1"}}]
  (http/run-server (partial handler dev?) {:port port :ip ip})
  @sweeper
  (println (str "server      http://localhost:" port))
  ;; the nrepl accepts eval from anyone who can reach it, so it is dev only
  (when dev?
    (nrepl/start-server! {:port nrepl-port})
    (println (str "nREPL       localhost:" nrepl-port))
    (println "Vite        http://localhost:5173 (assets only, do not open)")
    (println (str "open        http://localhost:" port
                  " , or http://localhost:8081 to go through Brotli"))))

(defn -main [& args]
  (start! {:dev? (not= "prod" (first args))
           :port (or (some-> (System/getenv "PORT") parse-long) 8080)})
  @(promise))
