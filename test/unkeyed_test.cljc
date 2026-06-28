(ns unkeyed-test
  (:require
   [clojure.test :refer [deftest is]]
   [reagami.core :as reagami]))

(defn- child-at [^js el i]
  (aget (.-children el) i))

(deftest grow-reuses-prefix-test
  (let [el (js/document.createElement "ul")]
    (reagami/render el [:ul [:li "a"]])
    (let [ul (child-at el 0)
          a (child-at ul 0)]
      (reagami/render el [:ul [:li "a"] [:li "b"]])
      (is (= a (child-at ul 0)) "prefix node reused on grow")
      (is (= "<ul><li>a</li><li>b</li></ul>" (.-innerHTML el))))))

(deftest shrink-reuses-prefix-test
  (let [el (js/document.createElement "ul")]
    (reagami/render el [:ul [:li "a"] [:li "b"] [:li "c"]])
    (let [ul (child-at el 0)
          a (child-at ul 0) b (child-at ul 1)]
      (reagami/render el [:ul [:li "a"] [:li "b"]])
      (is (= a (child-at ul 0)) "node 0 reused on shrink")
      (is (= b (child-at ul 1)) "node 1 reused on shrink")
      (is (= 2 (.-childElementCount ul)) "tail removed")
      (is (= "<ul><li>a</li><li>b</li></ul>" (.-innerHTML el))))))
