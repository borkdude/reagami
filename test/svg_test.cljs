(ns svg-test
  (:require
   ["node:assert" :as assert]
   [install-jsdom]
   [reagami.core :as reagami]))

(defn assert-equal
  [expected actual & [msg]]
  (assert/deepStrictEqual actual expected (or msg "")))

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

(defn render [elt]
  (reagami/render elt [svg-component]))

(defn svg-test []
  (let [el (js/document.createElement "div")
        render #(render el)
        _ (add-watch state ::render (fn [_ _ _ _] (render)))]
    (reagami/render el [svg-component])
    (let [circle (.querySelector el "circle")]
      (.dispatchEvent circle (js/MouseEvent. "click" #js {:bubbles true :cancelable true})))
    (assert-equal 1 @state)
    (let [circle (.querySelector el "circle")]
      (.dispatchEvent circle (js/MouseEvent. "click" #js {:bubbles true :cancelable true})))
    (assert-equal 2 @state)
    (let [text (.querySelector el "text")]
      (assert-equal "myclass other-class another-class" (.getAttribute text :class)))
    (println "✓ SVG tests passed")))
