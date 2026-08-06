(ns basic-test
  (:require
   [clojure.test :refer [deftest is testing]]
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

(deftest nested-render-test
  (let [el (js/document.createElement "div")]
    (reagami/render el [:div [:div#inner "placeholder"] [:span "sibling"]])
    (reagami/render (.querySelector el "#inner") [:b "inner"])
    (testing "rendering into an already rendered node replaces its children"
      (is (= "<div><div id=\"inner\"><b>inner</b></div><span>sibling</span></div>"
             (.-innerHTML el))))
    (reagami/render el [:div [:div#inner "placeholder"] [:span "changed"]])
    (testing "the outer render leaves the nested root alone"
      (is (= "<div><div id=\"inner\"><b>inner</b></div><span>changed</span></div>"
             (.-innerHTML el))))
    (reagami/render (.querySelector el "#inner") [:i "again"])
    (testing "the nested root keeps patching its own children"
      (is (= "<div><div id=\"inner\"><i>again</i></div><span>changed</span></div>"
             (.-innerHTML el))))))

(deftest nested-render-after-patch-test
  (testing "a render takes over children that an earlier render already patched"
    (let [el (js/document.createElement "div")]
      (reagami/render el [:div [:div#inner [:ul [:li "a"] [:li "b"]]] [:span "s"]])
      (reagami/render el [:div [:div#inner [:ul [:li "a"]]] [:span "s"]])
      (reagami/render (.querySelector el "#inner") [:ul [:li "a"]])
      (is (= "<div><div id=\"inner\"><ul><li>a</li></ul></div><span>s</span></div>"
             (.-innerHTML el))))))

(deftest custom-element-attribute-test
  (testing "a hyphenated tag gets value, checked, selected and disabled as attributes"
    (let [el (js/document.createElement "div")]
      (reagami/render el [:my-widget {:value "3" :checked true :disabled true}])
      (let [w (.querySelector el "my-widget")]
        (is (= "3" (.getAttribute w "value")))
        (is (some? (.getAttribute w "checked")))
        (is (some? (.getAttribute w "disabled"))))))
  (testing "innerHTML stays a property on a custom element"
    (let [el (js/document.createElement "div")]
      (reagami/render el [:my-widget {:innerHTML "<b>hi</b>"}])
      (is (= "<b>hi</b>" (.-innerHTML (.querySelector el "my-widget"))))))
  (testing "a native input keeps value as a property, where it is the live value"
    (let [el (js/document.createElement "div")]
      (reagami/render el [:input {:type "text" :value "3"}])
      (let [i (.querySelector el "input")]
        (is (= "3" (.-value i)))
        (is (nil? (.getAttribute i "value")))))))

(deftest custom-event-test
  (testing "an event with no on* property on the element uses addEventListener"
    (let [el (js/document.createElement "div")
          seen (atom [])
          view (fn [tag] [:div {:on-rated (fn [_] (swap! seen conj tag))}])]
      (reagami/render el [view "first"])
      (let [node (.querySelector el "div")]
        (reagami/render el [view "second"])
        (.dispatchEvent node (js/CustomEvent. "rated"))
        (testing "the newest handler runs, exactly once"
          (is (= ["second"] @seen)))
        (reagami/render el [:div])
        (.dispatchEvent node (js/CustomEvent. "rated"))
        (testing "removing the handler stops it"
          (is (= ["second"] @seen))))))
  (testing "a dashed event name survives, unlike the on* property form"
    (let [el (js/document.createElement "div")
          seen (atom 0)]
      (reagami/render el [:div {:on-my-event (fn [_] (swap! seen inc))}])
      (.dispatchEvent (.querySelector el "div") (js/CustomEvent. "my-event"))
      (is (= 1 @seen))))
  (testing "a standard event still reaches the element as a property"
    (let [el (js/document.createElement "div")]
      (reagami/render el [:div {:on-mouse-enter (fn [_] nil)}])
      (is (fn? (.-onmouseenter (.querySelector el "div")))))))
(deftest indeterminate-test
  (testing "indeterminate is set as a property, because HTML has no such attribute.
  It is the third checkbox state, drawn as a dash, and it is independent of
  checked. A server render leaves it out and the client sets it."
    (let [el (js/document.createElement "div")]
      (reagami/render el [:input {:type "checkbox" :indeterminate true}])
      (let [cb (.querySelector el "input")]
        (is (true? (.-indeterminate cb)))
        (is (nil? (.getAttribute cb "indeterminate"))))
      (reagami/render el [:input {:type "checkbox"}])
      (is (false? (.-indeterminate (.querySelector el "input")))))))

(deftest media-property-test
  (testing "muted, volume and playbackRate are set as properties. The muted
  attribute only feeds defaultMuted, which a live element reads once when it is
  created, and the other two have no attribute at all."
    (let [el (js/document.createElement "div")]
      (reagami/render el [:video {:muted true :volume 0.5 :playbackRate 2}])
      (let [v (.querySelector el "video")]
        (is (true? (.-muted v)))
        (is (= 0.5 (.-volume v)))
        (is (= 2 (.-playbackRate v)))
        (is (nil? (.getAttribute v "volume")))
        (is (nil? (.getAttribute v "playbackrate"))))
      (reagami/render el [:video])
      (is (false? (.-muted (.querySelector el "video")))))))
