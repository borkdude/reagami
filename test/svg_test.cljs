(ns svg-test
  (:require
   ["../reagami.mjs" :as reagami]
   ["./jsdom.mjs"]
   ["node:assert" :as assert]))

(defn deep-equal
  [expected actual & [msg]]
  (assert/deepStrictEqual actual expected (or msg "")))

(def state (atom 0))

(defn svg-click-test []
  [:div
   [:p "Clicked: " @state " times"]
   [:svg {:width 100 :height 100
          :style {:border "1px solid black"}}
    [:circle {:cx 50
              :cy 50
              :r 40
              :fill "tomato"
              :style {:color "blue"}
              :on-click #(swap! state inc)}]]])

(defn render [elt]
  (reagami/render elt [svg-click-test]))

(defn svg-test []
  (let [el (js/document.createElement "div")
        render #(render el)
        _ (add-watch state ::render (fn [_ _ _ _] (render)))]
    (reagami/render el [svg-click-test])
    (let [circle (.querySelector el "circle")]
      (.dispatchEvent circle (js/MouseEvent. "click" #js {:bubbles true :cancelable true})))
    (assert/ok (= 1 @state))
    (let [circle (.querySelector el "circle")]
      (.dispatchEvent circle (js/MouseEvent. "click" #js {:bubbles true :cancelable true})))
    (assert/ok (= 2 @state))
    (println "✓ render test passed")))

(svg-test)
