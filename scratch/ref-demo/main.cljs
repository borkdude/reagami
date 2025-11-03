(ns main
  (:require
   ["../../reagami.mjs" :as reagami]))

(defn other-component [state]
  [:button {:on-click #(swap! state inc)}
   "Click!" @state])

(def state (atom {:show true}))

(defn ui
  "Main UI" 
  []
  [:div#ui
   [:button {:on-click #(swap! state update :show not)}
    (if (:show @state) "Hide Clock" "Show Clock")]
   (when true #_(:show @state)
     [:div
      [:div#clock
       {:on-render (fn [node lifecycle]
                     (prn :lifecycle lifecycle)
                     (case lifecycle
                       :mount
                       (let [_ (prn :mount)
                             state (atom 0)
                             render #(reagami/render node [other-component state])]
                         (add-watch state ::other-component render)
                         (render))

                       :update
                       ;; optional: could update props here
                       (prn :update)

                       :unmount
                       (prn :unmount)))}]])])

(defn render []
  (reagami/render (js/document.querySelector "#app") [ui]))

(add-watch state ::render (fn [_ _ _ _] (render)))

(render)
