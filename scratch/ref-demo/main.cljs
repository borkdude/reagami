(ns main
  (:require
   ["../../reagami.mjs" :as reagami]))

(def state (atom {:counter 0 :show true}))

(defn sub-component []
  [:div "Hello there!"])

(defn ui []
  [:div#ui
   [:button {:on-click #(swap! state update :show not)}
    "Show? " (:show @state)]
   (when (:show @state)
     [:div {:style {:color :green
                    :background-color (if (even? (count (:input @state)))
                                        :black :green)}}
      [:pre (pr-str @state)]
      [:div#my-custom {:ref #(do
                               (println :ref-el %)
                               (when-let [el %]
                                 (js/console.log :el-mount el)
                                 (reagami/render el [sub-component])))}]
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
