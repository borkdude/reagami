(ns svg-test
  (:require
   [clojure.test :refer [deftest is]]
   [reagami.core :as reagami]))

(def state (atom 0))

(defn svg-component []
  [:div
   [:p "Clicked: " @state " times"]
   [:svg {:width 100 :height 100
          :style {:border "1px solid black"}}
    [:style ".myclass { fill: blue; font-size: 20px; }"]
    [:g
     [:circle {:cx 50
               :cy 50
               :r 40
               :fill "tomato"
               :style {:color :blue}
               :on-click #(swap! state inc)}]
     [:text.other-class.another-class
      {:x "50%" :y "50%"
       :class "myclass"} "dude"]]]])

(defn- render [elt]
  (reagami/render elt [svg-component]))

(deftest svg-test
  (let [el (js/document.createElement "div")
        render #(render el)
        _ (add-watch state ::render (fn [_ _ _ _] (render)))]
    (reagami/render el [svg-component])
    (let [circle (.querySelector el "circle")]
      (.dispatchEvent circle (js/MouseEvent. "click" #js {:bubbles true :cancelable true})))
    (is (= 1 @state))
    (let [circle (.querySelector el "circle")]
      (.dispatchEvent circle (js/MouseEvent. "click" #js {:bubbles true :cancelable true})))
    (is (= 2 @state))
    (let [text (.querySelector el "text")]
      (is (= "myclass other-class another-class" (.getAttribute text "class"))))))
