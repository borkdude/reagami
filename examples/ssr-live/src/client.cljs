(ns client
  (:require
   [app]
   [reagami.core :as r]))

(def root (js/document.getElementById "app"))
(def status-root (js/document.getElementById "status"))
(def sid (.. js/document -body -dataset -sid))

;; the last push: its size as JSON, and what it cost on the wire. the proxy
;; reports the compressed size, so this is empty on port 8080.
(defonce !chars (atom 0))
(defonce !wire (atom nil))
(defonce !created (atom nil))
(defonce !asked (atom nil))

(defn- render-status! []
  (r/render status-root
            [:p (str "push " @!chars " chars of state"
                     (when-let [w @!wire]
                       (str ", " (:br w) " B on the wire ("
                            (js/Math.round (/ (:raw w) (max 1 (:br w)))) "x smaller)"))
                     " | created " @!created)]))

(defn render! []
  (let [result (r/render root [app/app @app/!state])]
    (reset! !created (:created result))
    (render-status!)))

(defn dispatch-to-server [action]
  (-> (js/fetch "/action"
                #js {:method "POST"
                     :headers #js {"Content-Type" "application/json"}
                     :body (js/JSON.stringify #js {:sid sid :action action})})
      ;; read the empty body: devtools logs an unread response as
      ;; "Fetch failed loading"
      (.then (fn [r] (.text r)))))

(defn- viewport
  "Which rows are on screen, and where to draw the first of them. Below the
  canvas cap the scale is 1 and this is scrollTop divided by row height."
  [el]
  (let [total (:total @app/!state)
        top (.-scrollTop el)
        virtual (* top (app/scale total))
        first-row (js/Math.floor (/ virtual app/row-height))
        rows-shown (js/Math.ceil (/ (.-clientHeight el) app/row-height))]
    {:want [(max 0 first-row) (min total (+ first-row rows-shown))]
     ;; the canvas y of the first visible row. the part scrolled past stays
     ;; off the top edge.
     :base (- top (mod virtual app/row-height))
     :anchor first-row}))

(defn- with-margin [[a b]]
  (let [total (:total @app/!state)]
    [(max 0 (- a app/overscan)) (min total (+ b app/overscan))]))

(defn- needs-fetch?
  "True when the viewport comes within half the margin of an edge of what the
  client holds. The client then asks before a gap shows."
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
    ;; the rows on screen drive the placeholders, a wider range drives the fetch
    (swap! app/!view assoc :want want :base base :anchor anchor)
    (when (and (needs-fetch? want) (not= @!asked fetch))
      (reset! !asked fetch)
      (app/dispatch! {:type "window" :from (nth fetch 0) :to (nth fetch 1)}))))

(defn listen! []
  (let [events (js/EventSource. (str "/state/" sid))]
    ;; the proxy reports the compressed size of each push, just after it
    (.addEventListener events "wire"
                       (fn [e]
                         (reset! !wire (js/JSON.parse (.-data e)))
                         (render-status!)))
    (set! (.-onmessage events)
          (fn [e]
            (let [data (.-data e)
                  state (js/JSON.parse data)
                  asked @!asked]
              (reset! !chars (.-length data))
              ;; responses can arrive out of order, so drop anything that is
              ;; not the window asked for last
              (when (or (nil? asked) (= (:from state) (nth asked 0)))
                (reset! app/!state state)))))
    events))

;; the scroller is replaced on every render, so listen during capture instead
(defn watch-scroll! []
  (.addEventListener root "scroll" on-scroll true))

(reset! app/!dispatch dispatch-to-server)
(reset! app/!state (js/JSON.parse (.-textContent (js/document.getElementById "state"))))
(add-watch app/!state ::render (fn [_ _ _ _] (render!)))
(add-watch app/!view ::render (fn [_ _ _ _] (render!)))

(render!)
(watch-scroll!)
(listen!)

;; the Squint Vite plugin calls this after a hot swap, so an edit repaints
;; from the atoms instead of a page reload
(defn ^:dev/after-load re-render [] (render!))
