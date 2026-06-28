(ns basic-test
  (:require
   [clojure.test :refer [deftest is]]
   [reagami.core :as reagami]))

(deftest render-test
  (let [el (js/document.createElement "div")]
    (reagami/render el [:div "hello"])
    (is (= "<div>hello</div>" (.-innerHTML el)))))

(deftest class-test
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
                                       (.call old-attr @ref attr)
                                       (swap! attrs-removed conj attr))))
    (is (= "<div class=\"myclass class1 class2\" id=\"myid\">hello</div>" (.-innerHTML el)))
    (reagami/render el [:div#myid.class1.class2
                        "hello"])
    (is (= ["class"] @attrs-changed))
    (reset! attrs-changed [])
    (is (= @ref (.querySelector el "#myid")))
    (is (= "<div class=\"class1 class2\" id=\"myid\">hello</div>" (.-innerHTML el)))
    (reagami/render el [:div.class1.class2 "hello"])
    (is (= ["id"] @attrs-removed))
    (is (= [] @attrs-changed))
    (reset! attrs-removed [])
    (is (= @ref (.querySelector el ".class1")))
    (is (= "<div class=\"class1 class2\">hello</div>" (.-innerHTML el)))))

(deftest style-test
  (let [el (js/document.createElement "div")]
    (reagami/render el [:div {:style {:color :blue}}
                        "hello"])
    (is (= "<div style=\"color: blue;\">hello</div>" (.-innerHTML el)))
    (reagami/render el [:div {:style {:border "1px solid black"}}
                        "hello"])
    (is (= "<div style=\"border: 1px solid black;\">hello</div>" (.-innerHTML el)))))

(deftest input-test
  (let [el (js/document.createElement "div")
        state (atom {})
        ui (fn []
             [:input {:value (:input @state)}])]
    (reagami/render el [ui])
    (is (= "<input>" (.-innerHTML el)))
    (swap! state assoc :input "")
    (reagami/render el [ui])
    (is (= "" (.-value (.querySelector el "input"))))
    (swap! state assoc :input "k")
    (reagami/render el [ui])
    (is (= "k" (.-value (.querySelector el "input")))))
  ;; input with default value
  (let [el (js/document.createElement "div")
        ui (fn []
             [:input#input {:default-value "Hello"}])]
    (reagami/render el [ui])
    (is (= "<input value=\"Hello\" id=\"input\">" (.-innerHTML el)))
    (is (= "Hello" (.-value (.querySelector el "input"))))
    (set! (.-value (.querySelector el "input")) "I typed")
    ;; render doesn't overwrite what you typed because of default value
    (reagami/render el [ui])
    (is (= "I typed" (.-value (.querySelector el "input"))))
    (is (= "Hello" (.getAttribute (.querySelector el "input") "value")))))

(defn- get-value [node k]
  (let [k #?(:squint k :cljs (name k))]
    (if (= "value" k)
      (.-value node)
      (.getAttribute node "value"))))

(deftest input-range-test
  (doseq [k [:value :default-value]]
    (let [el (js/document.createElement "div")
          ui (fn [value min max]
               [:input {k value
                        :type "range"
                        :min min
                        :max max}])]
      (reagami/render el [ui 150 100 200])
      (is (= "150" (get-value (.querySelector el "input") k)))
      (reagami/render el [ui 140 101 200])
      (is (= "140" (get-value (.querySelector el "input") k))))))

(deftest button-test
  (let [el (js/document.createElement "div")
        ui (fn [disabled?]
             [:button {:disabled disabled?}])]
    (reagami/render el [ui true])
    (is (= "<button disabled=\"\"></button>" (.-innerHTML el)))
    (reagami/render el [ui false])
    (is (= "<button></button>" (.-innerHTML el)))))

(deftest table-test
  (let [el (js/document.createElement "div")
        ui (fn [elts]
             [:table
              (for [e elts]
                [:tr [:td e]])])]
    (reagami/render el [ui [1]])
    (reagami/render el [ui [1 2]])
    (is (= 2 (count (seq (.querySelectorAll el "tr")))))))

(deftest hiccup-fn-test
  (let [el (js/document.createElement "div")
        sub-ui (fn [x]
                 [:div x])
        ui (fn []
             [sub-ui "Hello world"])]
    (reagami/render el [ui])
    (is (= "<div>Hello world</div>" (.-innerHTML el)))))
