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

(deftest fragment-test
  (testing "a component can return multiple roots"
    (let [multi (fn [] [:<> [:li "one"] [:li "two"]])
          el (js/document.createElement "div")]
      (reagami/render el [:ul [multi]])
      (is (= "<ul><!----><li>one</li><li>two</li></ul>" (.-innerHTML el)))))
  (testing "keyed children inside a fragment move by key"
    (let [el (js/document.createElement "div")]
      (reagami/render el [:ul [:<> [:li {:key "a"} "a"] [:li {:key "b"} "b"]]])
      (let [la (.querySelector el "li")]
        (reagami/render el [:ul [:<> [:li {:key "b"} "b"] [:li {:key "a"} "a"]]])
        (is (identical? la (aget (.querySelectorAll el "li") 1)))
        (is (= "ba" (.-textContent el)))))))

(deftest fragment-no-attrs-test
  (testing "a map on a fragment is an invalid child"
    (is (thrown? js/Error (ssr/render [:div [:<> {:key "a"} [:p "x"]]])))
    (is (thrown? js/Error
                 (reagami/render (js/document.createElement "div")
                                 [:div [:<> {:key "a"} [:p "x"]]])))))

(deftest seq-component-test
  (testing "a component can return a seq"
    (let [el (js/document.createElement "div")]
      (reagami/render el [:ul [(fn [] (list [:li "one"] [:li "two"]))]])
      (is (= "<ul><li>one</li><li>two</li></ul>" (.-innerHTML el))))))

(deftest hydrate-fragment-test
  (testing "a fragment page is adopted, anchor comment included"
    (let [hiccup [:div [:<> [:p "a"] [:p "b"]] [:span "c"]]
          el (js/document.createElement "div")]
      (set! (.-innerHTML el) (ssr/render hiccup))
      (let [p (.querySelector el "p")
            span (.querySelector el "span")
            result (reagami/render el hiccup)]
        (is (= 0 (:created result)))
        (is (identical? p (.querySelector el "p")))
        (is (identical? span (.querySelector el "span")))
        (is (= (ssr/render hiccup) (.-innerHTML el)))))))

(deftest hydrate-sibling-fragment-components-test
  (testing "sibling components that each return a fragment hydrate by adoption"
    (let [head (fn [] [:<> [:h1 "title"] [:p "intro"]])
          body (fn [] [:<> [:ul [:li "a"]] [:footer "end"]])
          hiccup [:div [head] [body]]
          el (js/document.createElement "div")]
      (set! (.-innerHTML el) (ssr/render hiccup))
      (let [h1 (.querySelector el "h1")
            li (.querySelector el "li")
            footer (.querySelector el "footer")
            result (reagami/render el hiccup)]
        (is (= 0 (:created result)))
        (is (identical? h1 (.querySelector el "h1")))
        (is (identical? li (.querySelector el "li")))
        (is (identical? footer (.querySelector el "footer")))
        (is (= (ssr/render hiccup) (.-innerHTML el)))
        (testing "and a re-render after hydration still patches in place"
          (let [hiccup2 [:div [head] [(fn [] [:<> [:ul [:li "a2"]] [:footer "end"]])]]
                result2 (reagami/render el hiccup2)]
            (is (= 0 (:created result2)))
            (is (identical? li (.querySelector el "li")))
            (is (= "a2" (.-textContent (.querySelector el "li"))))))))))
