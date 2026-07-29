(ns client
  (:require
   [app]
   [debug]
   [reagami.core :as r]))

(def root (js/document.getElementById "app"))
(def debug-root (js/document.getElementById "debug"))
(def sid (.. js/document -body -dataset -sid))

;; the JSON the client last parsed into state: from the document at load, then
;; from each push. every push carries the whole state, so this is its size.
(defonce !bytes (atom 0))
;; every push since the page loaded, so the stream can be judged as a whole
(defonce !pushes (atom 0))
(defonce !chars (atom 0))
;; what the last push cost on the wire, reported by the proxy. absent on 8080.
(defonce !wire (atom nil))
(defonce !parse (atom nil))
(defonce !asked (atom nil))

;; when the first render finished
(defonce !hydrated (atom nil))
(defonce !last (atom {}))

(defn- render-debug! []
  (r/render debug-root
            [:div
             [debug/stats (assoc @!last
                                 :wire @!wire :bytes @!bytes :parse @!parse
                                 :pushes @!pushes :chars @!chars
                                 :load (debug/first-load) :hydrated @!hydrated)]
             [debug/state-view @app/!state]]))

(defn- render-now! []
  (let [t0 (js/performance.now)
        result (r/render root [app/app @app/!state])
        ms (js/Math.round (- (js/performance.now) t0))]
    (when-not @!hydrated
      (reset! !hydrated (js/Math.round (js/performance.now))))
    (reset! !last {:rows (count (:rows @app/!state))
                   :total (:total @app/!state)
                   :from (:from @app/!state)
                   :created (:created result)
                   :ms ms})
    (render-debug!)))

(defonce !rendering (atom false))
(defonce !again (atom false))

(defn render!
  "Renders, unless a render is already running. A render started from inside
  another corrupts the patch, so it is deferred until the first one finishes."
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

(defn- inflight! [n]
  (swap! app/!view update :inflight (fn [c] (+ (or c 0) n))))

(defn dispatch-to-server [action]
  ;; counted rather than a flag, so overlapping requests keep the bar up until
  ;; the last one answers
  (inflight! 1)
  (-> (js/fetch "/action"
                #js {:method "POST"
                     :headers #js {"Content-Type" "application/json"}
                     :body (js/JSON.stringify #js {:sid sid :action action})})
      ;; read the body, empty as it is: devtools logs a response nobody consumed
      ;; as "Fetch failed loading"
      (.then (fn [r] (.text r)))
      (.catch (fn [e] (js/console.warn "action failed" e)))
      (.finally (fn [] (inflight! -1)))))

(defn- viewport
  "Which rows are on screen, and where to draw the first of them. Below the
  canvas cap the scale is 1 and this is just scrollTop divided by row height."
  [el]
  (let [total (:total @app/!state)
        top (.-scrollTop el)
        virtual (* top (app/scale total))
        first-row (js/Math.floor (/ virtual app/row-height))
        rows-shown (js/Math.ceil (/ (.-clientHeight el) app/row-height))]
    {:want [(max 0 first-row) (min total (+ first-row rows-shown))]
     ;; the canvas y the first visible row is drawn at, keeping the part of it
     ;; that is scrolled past off the top edge
     :base (- top (mod virtual app/row-height))
     :anchor first-row}))

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
  (let [{:keys [want base anchor]} (viewport (.-target e))
        fetch (with-margin want)]
    ;; what is on screen drives the placeholders, a wider range drives the fetch
    (swap! app/!view assoc :want want :base base :anchor anchor)
    (when (and (needs-fetch? want) (not= @!asked fetch))
      (reset! !asked fetch)
      (app/dispatch! {:type "window" :from (nth fetch 0) :to (nth fetch 1)}))))

(defn listen! []
  (let [events (js/EventSource. (str "/state/" sid))]
    ;; EventSource retries a dropped connection but gives up for good on an
    ;; HTTP error, which is what nginx answers while the server restarts
    (set! (.-onerror events)
          (fn [_]
            (when (= 2 (.-readyState events))
              (js/setTimeout listen! 2000))))
    ;; the proxy reports each push's compressed size just after the push
    (.addEventListener events "wire"
                       (fn [e]
                         (reset! !wire (js/JSON.parse (.-data e)))
                         (render-debug!)))
    (set! (.-onmessage events)
          (fn [e]
            (let [data (.-data e)
                  t0 (js/performance.now)
                  state (js/JSON.parse data)
                  ;; microseconds: a window parses well below a millisecond
                  us (js/Math.round (* 1000 (- (js/performance.now) t0)))
                  asked @!asked]
              (reset! !bytes (.-length data))
              (swap! !pushes inc)
              (swap! !chars + (.-length data))
              (reset! !parse us)
              ;; any push means the server has answered, so release the edit
              ;; lock even for a window this client no longer wants
              (when (:pending @app/!view)
                (swap! app/!view assoc :pending nil))
              ;; responses can arrive out of order, so drop anything that is
              ;; not the window asked for last. a push without rows is not a
              ;; state this app can render, so it never replaces the one we hold.
              (when (and (:rows state)
                         (or (nil? asked) (= (:from state) (nth asked 0))))
                (reset! app/!state state)))))
    events))

;; the scroller is replaced on every render, so listen during capture instead
(defn watch-scroll! []
  (.addEventListener root "scroll" on-scroll true))

;; the row a page was opened from, so returning to the table lands on it
(defonce !opened (atom nil))

(defn- track-page! [_ _ old new]
  (let [was (= "row" (:page old))
        now (= "row" (:page new))]
    (cond
      (and (not was) now) (reset! !opened (:id (:row new)))
      (and was (not now))
      (when-let [id @!opened]
        ;; after the table has rendered
        (js/queueMicrotask
         (fn []
           (when-let [el (js/document.getElementById "scroller")]
             (set! (.-scrollTop el)
                   (/ (* id app/row-height) (app/scale (:total @app/!state)))))))))))

(defn- path-for [state]
  (if (= "row" (:page state))
    (str "/row/" (:id (:row state)))
    "/"))

;; the URL follows the state, so app.cljc needs no browser history API
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
(let [json (.-textContent (js/document.getElementById "state"))]
  (reset! !bytes (.-length json))
  (reset! app/!state (js/JSON.parse json)))
(add-watch app/!state ::render (fn [_ _ _ _] (render!)))
(add-watch app/!view ::render (fn [_ _ _ _] (render!)))

(add-watch app/!state ::url sync-url!)
(add-watch app/!state ::page track-page!)
;; a direct hit on /row/42 has no transition to observe, so seed it
(when (= "row" (:page @app/!state))
  (reset! !opened (:id (:row @app/!state))))
(.addEventListener js/window "popstate" (fn [_] (open-from-url!)))

(render!)
(watch-scroll!)
(listen!)

;; the Squint Vite plugin calls this after a hot swap, so an edit repaints from
;; the atoms rather than reloading
(defn ^:dev/after-load re-render [] (render!))
