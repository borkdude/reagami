(ns main
  (:require
   ["../../reagami.mjs" :as reagami]))

(def state (atom 0))

(defn svg-click-test []
  [:div
   [:p "Clicked: " @state " times"]
   [:svg {:width 100 :height 100
          :style {:border "1px solid black"}}
    [:style ".text { fill: blue; font-size: 20px; }"]
    [:g
     [:circle {:cx 50
               :cy 50
               :r 40
               :fill "tomato"
               :style {:color :blue}
               :on-click #(swap! state inc)}]
     [:text {:x "50%" :y "50%"
             :class "text"} "dude"]]]])

;; render
(defn render []
  (reagami/render (js/document.querySelector "#app") [svg-click-test]))

(add-watch state ::render (fn [_ _ _ _] (render)))

(render)
