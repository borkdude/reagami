(ns client
  (:require
   [app]
   [debug]
   [reagami.core :as r]))

(def root (js/document.getElementById "app"))
(def debug-root (js/document.getElementById "debug"))
(def sid (.. js/document -body -dataset -sid))

(defonce !bytes (atom 0))
;; what the last push cost on the wire, reported by the brotli proxy. absent
;; when the page is served straight from babashka on 8080.
(defonce !wire (atom nil))
(defonce !parse (atom nil))
(defonce !asked (atom nil))

(defonce !last (atom {}))

(defn- render-debug! []
  (r/render debug-root
            [debug/stats (assoc @!last :wire @!wire :bytes @!bytes :parse @!parse)]))

(defn- render-now! []
  (let [t0 (js/performance.now)
        result (r/render root [app/app @app/!state])
        ms (js/Math.round (- (js/performance.now) t0))]
    (reset! !last {:rows (count (:rows @app/!state))
                   :total (:total @app/!state)
                   :from (:from @app/!state)
                   :created (:created result)
                   :ms ms})
    (render-debug!)))

(defonce !rendering (atom false))
(defonce !again (atom false))

(defn render!
  "Renders, unless a render is already running. reagami walks a snapshot of the
  children while patching, so a render started from inside one, by a handler
  that a DOM change happens to fire, corrupts that walk. Anything asked for
  during a render runs after it instead."
  []
  (if @!rendering
    (reset! !again true)
    (do (reset! !rendering true)
        (try
          (render-now!)
          (finally (reset! !rendering false)))
        (when @!again
          (reset! !again false)
          (render!)))))

(defn dispatch-to-server [action]
  (js/fetch "/action"
            #js {:method "POST"
                 :headers #js {"Content-Type" "application/json"}
                 :body (js/JSON.stringify #js {:sid sid :action action})}))

(defn- visible
  "The rows actually on screen. Placeholders are drawn for these only, so the
  padding fetched around them never shows as a skeleton."
  [el]
  (let [top (.-scrollTop el)
        total (:total @app/!state)]
    [(max 0 (js/Math.floor (/ top app/row-height)))
     (min total (js/Math.ceil (/ (+ top (.-clientHeight el)) app/row-height)))]))

(defn- with-margin [[a b]]
  (let [total (:total @app/!state)]
    [(max 0 (- a app/overscan)) (min total (+ b app/overscan))]))

(defn- needs-fetch?
  "True once the viewport comes within half the margin of an edge of what the
  client holds, so the next window is asked for before a gap can show."
  [[a b]]
  (let [state @app/!state
        have-from (:from state)
        have-to (+ have-from (count (:rows state)))
        m (quot app/overscan 2)]
    (or (and (pos? have-from) (< (- a m) have-from))
        (and (< have-to (:total state)) (> (+ b m) have-to)))))

(defn on-scroll [e]
  (let [vis (visible (.-target e))
        fetch (with-margin vis)]
    ;; what is on screen drives the placeholders, a wider range drives the fetch
    (swap! app/!view assoc :want vis)
    (when (and (needs-fetch? vis) (not= @!asked fetch))
      (reset! !asked fetch)
      (app/dispatch! {:type "window" :from (nth fetch 0) :to (nth fetch 1)}))))

(defn listen! []
  (let [events (js/EventSource. (str "/state/" sid))]
    ;; the proxy reports each push's compressed size just after the push itself
    (.addEventListener events "wire"
                       (fn [e]
                         (reset! !wire (js/JSON.parse (.-data e)))
                         (render-debug!)))
    (set! (.-onmessage events)
          (fn [e]
            (let [data (.-data e)
                  t0 (js/performance.now)
                  state (js/JSON.parse data)
                  ;; microseconds: a window is a few thousand characters, which
                  ;; parses far below what a millisecond can show
                  us (js/Math.round (* 1000 (- (js/performance.now) t0)))
                  asked @!asked]
              (reset! !bytes (.-length data))
              (reset! !parse us)
              ;; jitter reorders responses, so a window requested earlier can
              ;; land after a later one. drop anything that is not what we asked
              ;; for last, and keep the placeholders up until that one arrives.
              (when (or (nil? asked) (= (:from state) (nth asked 0)))
                (reset! app/!state state)))))
    events))

;; the scroller is replaced on every render, so listen on the way up instead of
;; binding a handler to a node reagami owns
(defn watch-scroll! []
  (.addEventListener root "scroll" on-scroll true))

(defn- path-for [state]
  (if (= "row" (:page state))
    (str "/row/" (:id (:row state)))
    "/"))

;; the URL follows the state rather than driving it, so app.cljc needs no
;; browser history API and a direct hit still renders on the server
(defn- sync-url! [_ _ old new]
  (let [to (path-for new)]
    (when (not= (path-for old) to)
      (.pushState js/history nil "" to))))

(defn- open-from-url! []
  (let [p (.. js/window -location -pathname)]
    (if (.startsWith p "/row/")
      (app/dispatch! {:type "open" :id (parse-long (subs p 5))})
      (app/dispatch! {:type "close"}))))

(reset! app/!dispatch dispatch-to-server)
(reset! app/!state (js/JSON.parse (.-textContent (js/document.getElementById "state"))))
(add-watch app/!state ::render (fn [_ _ _ _] (render!)))
(add-watch app/!view ::render (fn [_ _ _ _] (render!)))

(add-watch app/!state ::url sync-url!)
(.addEventListener js/window "popstate" (fn [_] (open-from-url!)))

(render!)
(watch-scroll!)
(listen!)
