(ns basic-test
  (:require
   ["node:assert" :as assert]
   [reagami.core :as reagami]))

(defn render-test []
  (let [el (js/document.createElement "div")]
    (reagami/render el [:div "hello"])
    (assert/strictEqual (.-innerHTML el) "<div>hello</div>")
    (println "✓ render test passed")))

(defn class-test []
  (let [el (js/document.createElement "div")]
    (reagami/render el [:div#myid.class1.class2 {:class :myclass}
                        "hello"])
    (assert/strictEqual (.-innerHTML el) "<div class=\"myclass class1 class2\" id=\"myid\">hello</div>")
    (reagami/render el [:div#myid.class1.class2
                        "hello"])
    (assert/strictEqual (.-innerHTML el) "<div class=\"class1 class2\" id=\"myid\">hello</div>")
    (reagami/render el [:div.class1.class2
                        "hello"])
    (assert/strictEqual (.-innerHTML el) "<div class=\"class1 class2\">hello</div>")
    (println "✓ class test passed")))

(defn style-test []
  (let [el (js/document.createElement "div")]
    (reagami/render el [:div {:style {:color :blue}}
                        "hello"])
    (assert/strictEqual (.-innerHTML el) "<div style=\"color: blue;\">hello</div>")
    (reagami/render el [:div {:style {:border "1px solid black"}}
                        "hello"])
    (assert/strictEqual (.-innerHTML el) "<div style=\"border: 1px solid black;\">hello</div>")))

(defn input-test []
  (let [el (js/document.createElement "div")
        state (atom {})
        ui (fn []
             [:input {:value (:input @state)}])]
    (reagami/render el [ui])
    (assert/strictEqual (.-innerHTML el) "<input>")
    (swap! state assoc :input "")
    (reagami/render el [ui])
    (assert/strictEqual (.-value (.querySelector el "input")) "")
    (swap! state assoc :input "k")
    (reagami/render el [ui])
    (assert/strictEqual (.-value (.querySelector el "input")) "k")))

(defn button-test []
  (let [el (js/document.createElement "div")
        ui (fn [disabled?]
             [:button {:disabled disabled?}])]
    (reagami/render el [ui true])
    (assert/strictEqual (.-innerHTML el) "<button disabled=\"\"></button>")
    (reagami/render el [ui false])
    (assert/strictEqual (.-innerHTML el) "<button></button>")))
