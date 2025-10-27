(ns main
  (:require
   ["../../reagami.mjs" :as reagami]))

(def state (atom {:input ""}))

(defn ui []
  [:div
   (when true #_(:show? @state)
     [:div
      [:pre (pr-str @state)]
      [:input {:ref (fn [e]
                      (js/console.log :input-ref1 e)
                      (when e
                        (js/console.log :input1-ref e)
                        (js/console.log :focus (.focus e)) #_(reset! ref e)))
               :value (:value @state)
               :on-input #(swap! state assoc :input (-> % .-target .-value))}]
      (when (even? (count (:input @state)))
        [:input {:value "dude"
                 :ref #(js/console.log :input2-ref %)}])])
   [:button {:on-click #(swap! state update :show? not)}
    "Show"]])


(defn render []
  (reagami/render (js/document.querySelector "#app")
                  [ui]))

(add-watch state :state (fn [_ _ _ _]
                          (render)))

(render)
