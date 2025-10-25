(ns main
  (:require
   ["../../reagami.mjs" :as reagami]))

(def state (atom {:counter 0 :show true}))

(defn ui []
  [:div
   [:button {:on-click #(swap! state update :show not)}
    "Show? " (:show @state)]
   (when (:show @state)
     [:div {:style {:color :green}}
      [:pre (pr-str @state)]
      [:input {:value (:input @state)
               :on-input #(swap! state assoc :input (-> % :target :value))}]
      [:button {:on-click #(swap! state update :counter inc)}
       "Click me!"]
      (if(even? (count (:input @state)))
        [:p "Even input!"]
        [:pre {:style {:color "red"}}
         "Not even!"])])])
;; render
(defn render []
  (reagami/render (js/document.querySelector "#app") [ui]))

(add-watch state ::render (fn [_ _ _ _] (render)))

(render)
