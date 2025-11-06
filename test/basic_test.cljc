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
    (assert/strictEqual (.-value (.querySelector el "input")) "k"))
  ;; input with default value
  (let [el (js/document.createElement "div")
        ui (fn []
             [:input#input {:default-value "Hello"}])]
    (reagami/render el [ui])
    (assert/strictEqual (.-innerHTML el) "<input value=\"Hello\" id=\"input\">")
    (assert/strictEqual (.-value (.querySelector el "input")) "Hello")
    (set! (.-value (.querySelector el "input")) "I typed")
    ;; render doesn't overwrite what you typed because of default value
    (reagami/render el [ui])
    (assert/strictEqual (.-value (.querySelector el "input")) "I typed")
    (assert/strictEqual (.getAttribute (.querySelector el "input") "value") "Hello")))

(defn get-value [node k]
  (let [k #?(:squint k :cljs (name k))]
    (if (= "value" k)
      (.-value node)
      (.getAttribute node "value"))))

(defn input-range-test []
  (doseq [k [:value :default-value]]
    (let [el (js/document.createElement "div")
          ui (fn [value min max]
               [:input {k value
                        :type "range"
                        :min min
                        :max max}])]
      (reagami/render el [ui 150 100 200])
      (assert/strictEqual (get-value (.querySelector el "input") k) "150")
      (reagami/render el [ui 140 101 200])
      (assert/strictEqual (get-value (.querySelector el "input") k) "140"))))

(defn button-test []
  (let [el (js/document.createElement "div")
        ui (fn [disabled?]
             [:button {:disabled disabled?}])]
    (reagami/render el [ui true])
    (assert/strictEqual (.-innerHTML el) "<button disabled=\"\"></button>")
    (reagami/render el [ui false])
    (assert/strictEqual (.-innerHTML el) "<button></button>")))

(defn table-test []
  (let [el (js/document.createElement "div")
        ui (fn [elts]
             [:table
              (for [e elts]
                [:tr [:td e]])])]
    (reagami/render el [ui [1]])
    (reagami/render el [ui [1 2]])
    (assert/equal 2 (count (seq (.querySelectorAll el "tr"))))
    (println "✓ table test passed")))

(defn hiccup-fn-test []
  (let [el (js/document.createElement "div")
        sub-ui (fn [x]
                 [:div x])
        ui (fn []
             [sub-ui "Hello world"])]
    (reagami/render el [ui])
    (assert/equal (.-innerHTML el) "<div>Hello world</div>")
    (println "✓ fn test passed")))
