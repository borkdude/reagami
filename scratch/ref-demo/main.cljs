(ns main
  (:require
   ["../../reagami.mjs" :as reagami]))

(def state (atom {:show true :counter 0}))

(defn install-clock!
  "Third party JS component"
  [node]
  (let [span (.createElement js/document "span")
        render-clock! #(set! (.-textContent span)
                         (.toLocaleTimeString (js/Date.)))
        timer (js/setInterval render-clock! 1000)]
    (render-clock!)
    (.appendChild node span)
    ;; return cleanup fn
    (fn [updates]
      (js/console.log "unmounting the clock, got updates:" updates)
      (js/clearInterval timer)
      (.removeChild node span))))

(defn update-clock! [node count]
  (.appendChild node (doto (js/document.createElement "span")
                       (set! -textContent (str " " count)))))

(defn ui
  "Main UI"
  []
  [:div#ui
   [:button {:on-click #(swap! state update :show not)}
    (if (:show @state) "Hide Clock" "Show Clock")]
   (when (:show @state)
     [:div
      [:button {:on-click #(swap! state update :counter inc)} "click"]
      [:div#clock
       {:on-render (fn [node lifecycle {:keys [unmount updates] :as data}]
                     (case lifecycle
                       :mount
                       ;; mount: start ticking
                       {:unmount (install-clock! node)
                        :updates 0}

                       :update
                       ;; optional: could update props here
                       (do (update-clock! node (:counter @state))
                           (update data :updates inc))

                       :unmount
                       ;; stop ticking
                       (unmount updates)))}]])])

(defn render []
  (reagami/render (js/document.querySelector "#app") [ui]))

(add-watch state ::render (fn [_ _ _ _] (render)))

(render)
