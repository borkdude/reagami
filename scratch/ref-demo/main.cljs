(ns main
  (:require
   ["../../reagami.mjs" :as reagami]))

(def state (atom {:counter 0 :show true}))

(defn sub-component [x]
  [:div "Counter in subcomponent " x])

(defn ui []
  [:div#ui
   [:button {:on-click #(swap! state update :show not)}
    "Show? " (:show @state)]
   (when (:show @state)
     [:div
      [:pre (pr-str @state)]
      [:div#my-custom {:on-render (fn [node lifecycle]
                                    (case lifecycle
                                      (:mount :update) (reagami/render node [sub-component (:counter @state)])
                                      :unmount (prn :unmount)))}]
      [:button {:on-click #(swap! state update :counter inc)}
       "Click me!"]])])

(defn render []
  (reagami/render (js/document.querySelector "#app") [ui]))

(add-watch state ::render (fn [_ _ _ _] (render)))

(render)
