(ns ssr-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [reagami.core :as reagami]
   [reagami.ssr :as ssr]
   [ssr-corpus :as corpus]))

(deftest render-test
  (run! (fn [case]
          (testing (pr-str (:hiccup case))
            (is (= (:html case) (ssr/render (:hiccup case))))))
        corpus/cases))

(deftest matches-client-render-test
  (testing "server HTML matches what reagami renders in the DOM"
    (run! (fn [case]
            (testing (pr-str (:hiccup case))
              (let [el (js/document.createElement "div")]
                (reagami/render el (:hiccup case))
                (is (= (or (:dom case) (:html case)) (.-innerHTML el))))))
          corpus/cases)))

(deftest escaping-test
  (run! (fn [case]
          (testing (pr-str (:hiccup case))
            (is (= (:html case) (ssr/render (:hiccup case))))))
        corpus/escaping-cases))

(deftest inner-html-owns-subtree-test
  (testing "children alongside innerHTML are not rendered"
    (let [el (js/document.createElement "div")]
      (reagami/render el [:div {:innerHTML "<b>x</b>"} [:i "y"]])
      (is (= "<div><b>x</b></div>" (.-innerHTML el)))))
  (testing "innerHTML survives a re-render"
    (let [el (js/document.createElement "div")]
      (reagami/render el [:div {:innerHTML "<b>x</b>"}])
      (reagami/render el [:div {:innerHTML "<b>x</b>"}])
      (is (= "<div><b>x</b></div>" (.-innerHTML el)))))
  (testing "a changed innerHTML replaces the subtree"
    (let [el (js/document.createElement "div")]
      (reagami/render el [:div {:innerHTML "<b>x</b>"}])
      (reagami/render el [:div {:innerHTML "<i>z</i>"}])
      (is (= "<div><i>z</i></div>" (.-innerHTML el))))))

(deftest component-test
  (let [greet (fn [name] [:p "hi " name])]
    (is (= "<p>hi ann</p>" (ssr/render [greet "ann"])))
    (is (= "<div><p>hi ann</p></div>" (ssr/render [:div [greet "ann"]])))))

(defn- hydrate [hiccup]
  (let [el (js/document.createElement "div")]
    (set! (.-innerHTML el) (ssr/render hiccup))
    [el (reagami/render el hiccup)]))

(deftest hydrate-matches-fresh-render-test
  (run! (fn [case]
          (when-not (false? (:hydrate case))
            (testing (pr-str (:hiccup case))
              (let [fresh (js/document.createElement "div")
                    [el _] (hydrate (:hiccup case))]
                (reagami/render fresh (:hiccup case))
                (is (= (.-innerHTML fresh) (.-innerHTML el)))))))
        corpus/cases))

(deftest hydrate-reuses-server-nodes-test
  (let [hiccup [:div [:span "a"] [:span "b"]]
        el (js/document.createElement "div")]
    (set! (.-innerHTML el) (ssr/render hiccup))
    (let [span (.querySelector el "span")
          result (reagami/render el hiccup)]
      (testing "the server's node object is still the one in the tree"
        (is (identical? span (.querySelector el "span"))))
      (is (= 0 (:created result)))
      (is (pos? (:adopted result))))))

(deftest hydrate-attaches-handlers-test
  (let [clicks (atom 0)
        hiccup [:button {:on-click (fn [_] (swap! clicks inc))} "go"]
        [el _] (hydrate hiccup)]
    (.click (.querySelector el "button"))
    (is (= 1 @clicks))))

(deftest indeterminate-ssr-test
  (testing "no HTML can express indeterminate, so the server leaves it out and the
  client sets it on the first render"
    (let [hiccup [:input {:type "checkbox" :indeterminate true}]
          el (js/document.createElement "div")]
      (is (= "<input type=\"checkbox\">" (ssr/render hiccup)))
      (.appendChild js/document.body el)
      (set! (.-innerHTML el) (ssr/render hiccup))
      (let [result (reagami/render el hiccup)]
        (is (= 0 (:created result))))
      (is (true? (.-indeterminate (.querySelector el "input"))))
      (.remove el))))

(deftest hydrate-on-render-test
  (testing "an :on-render hook mounts on an adopted node and keeps its state"
    (let [calls (atom [])
          hook (fn [{:keys [lifecycle state save]}]
                 (swap! calls conj [lifecycle state])
                 (save :state))
          hiccup [:div [:span {:on-render hook} "x"]]
          el (js/document.createElement "div")]
      ;; the hook checks isConnected, so the container must be in the document
      (.appendChild js/document.body el)
      (set! (.-innerHTML el) (ssr/render hiccup))
      (let [result (reagami/render el hiccup)]
        (is (= 0 (:created result))))
      (is (= [[:mount nil]] @calls))
      (reagami/render el hiccup)
      (is (= [[:mount nil] [:update :state]] @calls))
      (.remove el))))

(deftest hydrate-mismatch-repairs-and-counts-test
  (let [el (js/document.createElement "div")]
    (set! (.-innerHTML el) (ssr/render [:div [:span "a"]]))
    (let [result (reagami/render el [:div [:p "a"]])]
      (is (= "<div><p>a</p></div>" (.-innerHTML el)))
      (is (pos? (:created result))))))

(deftest hydrate-conditional-children-test
  (testing "a nil child holds its slot on both sides, so siblings still line up"
    (let [hiccup [:ul (when false [:li "hidden"]) [:li#keep "keep"] (when false [:li])]
          el (js/document.createElement "div")]
      (set! (.-innerHTML el) (ssr/render hiccup))
      (let [li (.querySelector el "#keep")
            result (reagami/render el hiccup)]
        (is (= 0 (:created result)))
        (is (identical? li (.querySelector el "#keep"))))))
  (testing "toggling a conditional swaps one node and leaves its siblings alone"
    (let [el (js/document.createElement "div")
          ui (fn [show?] [:ul (when show? [:li "top"]) [:li#keep "keep"]])]
      (reagami/render el [ui false])
      (let [li (.querySelector el "#keep")]
        (reagami/render el [ui true])
        (is (identical? li (.querySelector el "#keep")))
        (is (= "<ul><li>top</li><li id=\"keep\">keep</li></ul>" (.-innerHTML el)))
        (reagami/render el [ui false])
        (is (identical? li (.querySelector el "#keep")))))))

(deftest hydrate-non-element-nodes-test
  (testing "comments and stray whitespace in the container do not break adoption"
    (run! (fn [markup]
            (testing (pr-str markup)
              (let [el (js/document.createElement "div")]
                (set! (.-innerHTML el) markup)
                (reagami/render el [:div "x"])
                (is (= "<div>x</div>" (.-innerHTML el))))))
          ["<!-- server comment --><div>x</div>"
           "<div>x</div><!-- trailing -->"
           "\n  <div>x</div>\n"])))

(deftest render-counts-test
  (let [el (js/document.createElement "div")]
    (testing "an empty root adopts nothing"
      (let [result (reagami/render el [:div [:span "a"]])]
        (is (= 0 (:adopted result)))
        (is (= 3 (:created result)))))
    (testing "a re-render of matching hiccup builds nothing"
      (is (= 0 (:created (reagami/render el [:div [:span "a"]])))))))

(deftest seq-component-test
  (testing "a component can return a seq"
    (let [el (js/document.createElement "div")]
      (reagami/render el [:ul [(fn [] (list [:li "one"] [:li "two"]))]])
      (is (= "<ul><li>one</li><li>two</li></ul>" (.-innerHTML el))))))
