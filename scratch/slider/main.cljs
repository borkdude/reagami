(ns main
  (:require
   ["../../reagami.mjs" :as reagami]))

(def state (atom {:value 0 :value-2 0}))

(defn slider []
  [:div
   [:pre (pr-str @state)]
   [:input {:type "range"
            :value (:value @state)
            :min 0
            :max 100
            :step 0.5
            :style {:width "50%"}
            :on-input (fn [e]
                        (let [new-value (parse-double
                                         (aget (aget e "target") "value"))]
                          (swap! state assoc :value new-value)))}]
   [:input {:type "range"
            :value (:value-2 @state)
            :min 0
            :max 200
            :step 10
            :style {:width "50%"}
            :on-input (fn [e]
                        (let [new-value (parse-double
                                         (aget (aget e "target") "value"))]
                          (swap! state assoc :value (* 2 new-value) :value-2 new-value)))}]])
(defn render []
  (reagami/render (js/document.querySelector "#app")
                  [slider]))

(add-watch state :state (fn [_ _ _ _]
                          (render)))

(render)
