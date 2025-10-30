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
  (let [el (js/document.createElement "div")
        ref (atom nil)
        attrs-changed (atom [])
        attrs-removed (atom [])]
    (reagami/render el [:div#myid.class1.class2 {:class :myclass}
                        "hello"])
    (reset! ref (.querySelector el "#myid"))
    (let [old-attr (.-setAttribute @ref)]
      (set! (.-setAttribute @ref) (fn [attr v]
                                    (.call old-attr @ref attr v)
                                    (swap! attrs-changed conj attr))))
    (let [old-attr (.-removeAttribute @ref)]
      (set! (.-removeAttribute @ref) (fn [attr]
                                       (.call old-attr @ref attr )
                                       (swap! attrs-removed conj attr))))
    (assert/strictEqual (.-innerHTML el) "<div class=\"myclass class1 class2\" id=\"myid\">hello</div>")
    (reagami/render el [:div#myid.class1.class2
                        "hello"])
    (assert/deepEqual ["class"] @attrs-changed)
    (reset! attrs-changed [])
    (assert/equal @ref (.querySelector el "#myid"))
    (assert/strictEqual (.-innerHTML el) "<div class=\"class1 class2\" id=\"myid\">hello</div>")
    (reagami/render el [:div.class1.class2 "hello"])
    (assert/deepEqual ["id"] @attrs-removed)
    (assert/deepEqual [] @attrs-changed)
    (reset! attrs-removed [])
    (assert/equal @ref (.querySelector el ".class1"))
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

(defn input-range-test []
  (let [el (js/document.createElement "div")
        ui (fn [value min max]
             [:input {:value value
                      :type "range"
                      :min min
                      :max max}])]
    (reagami/render el [ui 150 100 200])
    (assert/strictEqual "150" (.-value (.querySelector el "input")))
    (reagami/render el [ui 140 101 200])
    (assert/strictEqual "140" (.-value (.querySelector el "input")))))

(defn button-test []
  (let [el (js/document.createElement "div")
        ui (fn [disabled?]
             [:button {:disabled disabled?}])]
    (reagami/render el [ui true])
    (assert/strictEqual (.-innerHTML el) "<button disabled=\"\"></button>")
    (reagami/render el [ui false])
    (assert/strictEqual (.-innerHTML el) "<button></button>")))
