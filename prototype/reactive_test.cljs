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
                        [:div "Count: " @counter]))]
    (r/render el (fn [] [:div [counter-view] [:span "static"]]))
    (is (str/includes? (.-innerHTML el) "Count: 0"))
    (is (= 1 @runs))
    (swap! counter inc)
    (is (str/includes? (.-innerHTML el) "Count: 1"))
    (is (= 2 @runs))
    (testing "container node reused, not remounted"
      (let [node (js/document.querySelector "[data-reactive]")]
        (swap! counter inc)
        (is (identical? node (js/document.querySelector "[data-reactive]")))))))

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
    (is (= [2 2] [@a-runs @b-runs]))))

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

(def ^:private old-fail (get-method t/report [:cljs.test/default :fail]))
(defmethod t/report [:cljs.test/default :fail] [m]
  (set! (.-exitCode js/process) 1)
  (old-fail m))

(def ^:private old-error (get-method t/report [:cljs.test/default :error]))
(defmethod t/report [:cljs.test/default :error] [m]
  (set! (.-exitCode js/process) 1)
  (old-error m))

(t/run-tests 'reactive-test)
