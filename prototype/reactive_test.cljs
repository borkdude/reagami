(ns reactive-test
  (:require
   [clojure.string :as str]
   [clojure.test :as t :refer [deftest is testing]]
   [install-jsdom]
   [reactive :as r]))

(defn fresh-el []
  (let [el (js/document.createElement "div")]
    (js/document.body.appendChild el)
    el))

(deftest ratom-basics-test
  (let [a (r/ratom 0)
        watched (atom nil)]
    (is (= 0 @a))
    (reset! a 1)
    (is (= 1 @a))
    (swap! a + 2 3)
    (is (= 6 @a))
    (add-watch a :w (fn [k _ref old new] (reset! watched [k old new])))
    (swap! a inc)
    (is (= [:w 6 7] @watched))
    (remove-watch a :w)
    (swap! a inc)
    (is (= [:w 6 7] @watched))))

(deftest auto-rerender-test
  (let [el (fresh-el)
        counter (r/ratom 0)
        runs (atom 0)
        counter-view (r/component
                      (fn []
                        (swap! runs inc)
                        [:div#counter "Count: " @counter]))]
    (r/render el (fn [] [:div [counter-view] [:span "static"]]))
    (is (str/includes? (.-innerHTML el) "Count: 0"))
    (is (= 1 @runs))
    (swap! counter inc)
    (is (str/includes? (.-innerHTML el) "Count: 1"))
    (is (= 2 @runs))
    (testing "component node reused, not remounted"
      (let [node (js/document.querySelector "#counter")]
        (swap! counter inc)
        (is (identical? node (js/document.querySelector "#counter")))))))

(deftest no-wrapper-element-test
  (let [el (fresh-el)
        items (r/ratom ["a" "b"])
        item-list (r/component
                   (fn []
                     (for [x @items]
                       [:li x])))]
    (r/render el (fn [] [:ul [item-list]]))
    (testing "component lis are direct children of ul"
      (let [ul (.querySelector el "ul")]
        (is (= 2 (.-length (.-children ul))))
        (is (every? #(= "LI" (.-tagName %)) (vec (js/Array.from (.-children ul)))))
        (is (some? (.querySelector el "ul > li")))))
    (testing "fragment re-render with more children"
      (swap! items conj "c")
      (let [ul (.querySelector el "ul")]
        (is (= 3 (.-length (.-children ul))))
        (is (str/includes? (.-innerHTML ul) "c"))))
    (testing "fragment re-render with fewer children"
      (reset! items ["z"])
      (let [ul (.querySelector el "ul")]
        (is (= 1 (.-length (.-children ul))))
        (is (str/includes? (.-innerHTML ul) "z"))))))

(deftest table-structure-test
  (let [el (fresh-el)
        rows (r/ratom [["x" 1] ["y" 2]])
        row-view (r/component
                  (fn []
                    (for [[nm v] @rows]
                      [:tr [:td nm] [:td (str v)]])))]
    (r/render el (fn [] [:table [:tbody [row-view]]]))
    (let [tbody (.querySelector el "tbody")]
      (is (= 2 (.-length (.-children tbody))))
      (is (every? #(= "TR" (.-tagName %)) (vec (js/Array.from (.-children tbody))))))))

(deftest sibling-isolation-test
  (let [el (fresh-el)
        a (r/ratom "a0")
        b (r/ratom "b0")
        a-runs (atom 0)
        b-runs (atom 0)
        view-a (r/component (fn [] (swap! a-runs inc) [:p "A: " @a]))
        view-b (r/component (fn [] (swap! b-runs inc) [:p "B: " @b]))]
    (r/render el (fn [] [:div [view-a] [view-b]]))
    (is (= [1 1] [@a-runs @b-runs]))
    (reset! a "a1")
    (is (str/includes? (.-innerHTML el) "A: a1"))
    (is (= [2 1] [@a-runs @b-runs]))
    (reset! b "b1")
    (is (str/includes? (.-innerHTML el) "B: b1"))
    (is (= [2 2] [@a-runs @b-runs]))
    (testing "sibling order survives component re-render"
      (is (str/includes? (.-innerHTML el) "A: a1"))
      (let [ps (js/Array.from (.querySelectorAll el "p"))]
        (is (= 2 (.-length ps)))
        (is (str/includes? (.-textContent (aget ps 0)) "A:"))
        (is (str/includes? (.-textContent (aget ps 1)) "B:"))))))

(deftest props-test
  (let [el (fresh-el)
        page (r/ratom {:n 1 :other 0})
        child-runs (atom 0)
        child (r/component
               (fn [n]
                 (swap! child-runs inc)
                 [:div "N: " n]))]
    (r/render el (fn [] [:div [child (:n @page)]]))
    (is (str/includes? (.-innerHTML el) "N: 1"))
    (is (= 1 @child-runs))
    (testing "new arg re-renders child"
      (swap! page assoc :n 2)
      (is (str/includes? (.-innerHTML el) "N: 2"))
      (is (= 2 @child-runs)))
    (testing "unrelated parent re-render skips child"
      (swap! page assoc :other 1)
      (is (= 2 @child-runs)))))

(deftest unmount-test
  (let [el (fresh-el)
        show (r/ratom true)
        inner (r/ratom 0)
        runs (atom 0)
        view (r/component (fn [] (swap! runs inc) [:div "Inner: " @inner]))]
    (r/render el (fn [] [:div (when @show [view])]))
    (is (= 1 @runs))
    (reset! show false)
    (is (not (str/includes? (.-innerHTML el) "Inner")))
    (swap! inner inc)
    (is (= 1 @runs))))

(deftest nested-unmount-test
  (let [el (fresh-el)
        show (r/ratom true)
        leaf-state (r/ratom 0)
        leaf-runs (atom 0)
        leaf (r/component (fn [] (swap! leaf-runs inc) [:em "Leaf: " @leaf-state]))
        branch (r/component (fn [] [:section [leaf]]))]
    (r/render el (fn [] [:div (when @show [branch])]))
    (is (str/includes? (.-innerHTML el) "Leaf: 0"))
    (is (= 1 @leaf-runs))
    (swap! leaf-state inc)
    (is (str/includes? (.-innerHTML el) "Leaf: 1"))
    (is (= 2 @leaf-runs))
    (reset! show false)
    (swap! leaf-state inc)
    (is (= 2 @leaf-runs))))

(deftest nested-toplevel-fragment-test
  (let [el (fresh-el)
        show (r/ratom true)
        leaf-state (r/ratom 0)
        leaf-runs (atom 0)
        leaf (r/component (fn [] (swap! leaf-runs inc) [:em "Leaf: " @leaf-state]))
        ;; leaf fragment is a top-level child of the branch fragment
        branch (r/component (fn [] [leaf]))]
    (r/render el (fn [] [:div (when @show [branch])]))
    (is (str/includes? (.-innerHTML el) "Leaf: 0"))
    (swap! leaf-state inc)
    (is (str/includes? (.-innerHTML el) "Leaf: 1"))
    (is (= 2 @leaf-runs))
    (reset! show false)
    (is (not (str/includes? (.-innerHTML el) "Leaf")))
    (swap! leaf-state inc)
    (is (= 2 @leaf-runs))))

(def ^:private old-fail (get-method t/report [:cljs.test/default :fail]))
(defmethod t/report [:cljs.test/default :fail] [m]
  (set! (.-exitCode js/process) 1)
  (old-fail m))

(def ^:private old-error (get-method t/report [:cljs.test/default :error]))
(defmethod t/report [:cljs.test/default :error] [m]
  (set! (.-exitCode js/process) 1)
  (old-error m))

(t/run-tests 'reactive-test)
