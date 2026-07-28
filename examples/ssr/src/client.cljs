(ns client
  (:require
   [app]
   [debug]
   [reagami.core :as r]))

(def root (js/document.getElementById "app"))
(def debug-root (js/document.getElementById "debug"))
(def sid (.. js/document -body -dataset -sid))

(defonce !bytes (atom 0))
(defonce !asked (atom nil))

(defn render! []
  (let [t0 (js/performance.now)
        result (r/render root [app/app @app/!state])
        ms (js/Math.round (- (js/performance.now) t0))]
    (r/render debug-root
              [debug/stats {:rows (count (:rows @app/!state))
                            :total (:total @app/!state)
                            :from (:from @app/!state)
                            :bytes @!bytes
                            :created (:created result)
                            :ms ms}])))

(defn dispatch-to-server [action]
  (js/fetch "/action"
            #js {:method "POST"
                 :headers #js {"Content-Type" "application/json"}
                 :body (js/JSON.stringify #js {:sid sid :action action})}))

(defn- want
  "The row range the viewport is over, padded so small scrolls do not refetch."
  [el]
  (let [top (.-scrollTop el)
        total (:total @app/!state)
        first-row (js/Math.floor (/ top app/row-height))
        last-row (js/Math.ceil (/ (+ top (.-clientHeight el)) app/row-height))]
    [(max 0 (- first-row app/overscan))
     (min total (+ last-row app/overscan))]))

(defn- covered?
  "True when the client already holds every row in [from to)."
  [[from to]]
  (let [state @app/!state
        have-from (:from state)
        have-to (+ have-from (count (:rows state)))]
    (and (>= from have-from) (<= to have-to))))

(defn on-scroll [e]
  (let [[from to] (want (.-target e))]
    (when-not (or (covered? [from to]) (= @!asked [from to]))
      (reset! !asked [from to])
      (swap! app/!view assoc :loading true)
      (app/dispatch! {:type "window" :from from :to to}))))

(defn listen! []
  (let [events (js/EventSource. (str "/state/" sid))]
    (set! (.-onmessage events)
          (fn [e]
            (reset! !bytes (.-length (.-data e)))
            (swap! app/!view assoc :loading false)
            (reset! app/!state (js/JSON.parse (.-data e)))))
    events))

;; the scroller is replaced on every render, so listen on the way up instead of
;; binding a handler to a node reagami owns
(defn watch-scroll! []
  (.addEventListener root "scroll" on-scroll true))

(reset! app/!dispatch dispatch-to-server)
(reset! app/!state (js/JSON.parse (.-textContent (js/document.getElementById "state"))))
(add-watch app/!state ::render (fn [_ _ _ _] (render!)))
(add-watch app/!view ::render (fn [_ _ _ _] (render!)))

(render!)
(watch-scroll!)
(listen!)
