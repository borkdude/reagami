(ns keyed-test
  (:require
   [clojure.test :refer [deftest is]]
   [reagami.core :as reagami]))

(defn- child-at [^js el i]
  (aget (.-children el) i))

(deftest key-not-leaked-test
  (let [el (js/document.createElement "ul")]
    (reagami/render el [:ul [:li {:key "a"} "A"] [:li {:key "b"} "B"]])
    (is (= "<ul><li>A</li><li>B</li></ul>" (.-innerHTML el)))))

(deftest reorder-test
  (let [el (js/document.createElement "ul")]
    (reagami/render el [:ul [:li {:key "a"} "A"] [:li {:key "b"} "B"] [:li {:key "c"} "C"]])
    (let [ul (child-at el 0)
          a (child-at ul 0) b (child-at ul 1) c (child-at ul 2)]
      (reagami/render el [:ul [:li {:key "c"} "C"] [:li {:key "a"} "A"] [:li {:key "b"} "B"]])
      (is (= c (child-at ul 0)) "c kept identity")
      (is (= a (child-at ul 1)) "a kept identity")
      (is (= b (child-at ul 2)) "b kept identity")
      (is (= "<ul><li>C</li><li>A</li><li>B</li></ul>" (.-innerHTML el))))))

(deftest insert-middle-test
  (let [el (js/document.createElement "ul")]
    (reagami/render el [:ul [:li {:key "a"} "A"] [:li {:key "c"} "C"]])
    (let [ul (child-at el 0)
          a (child-at ul 0) c (child-at ul 1)]
      (reagami/render el [:ul [:li {:key "a"} "A"] [:li {:key "b"} "B"] [:li {:key "c"} "C"]])
      (is (= a (child-at ul 0)) "a kept")
      (is (= c (child-at ul 2)) "c kept")
      (is (= "<ul><li>A</li><li>B</li><li>C</li></ul>" (.-innerHTML el))))))

(deftest delete-middle-test
  (let [el (js/document.createElement "ul")]
    (reagami/render el [:ul [:li {:key "a"} "A"] [:li {:key "b"} "B"] [:li {:key "c"} "C"]])
    (let [ul (child-at el 0)
          a (child-at ul 0) c (child-at ul 2)]
      (reagami/render el [:ul [:li {:key "a"} "A"] [:li {:key "c"} "C"]])
      (is (= a (child-at ul 0)) "a kept")
      (is (= c (child-at ul 1)) "c kept")
      (is (= "<ul><li>A</li><li>C</li></ul>" (.-innerHTML el))))))

(deftest tag-mismatch-test
  (let [el (js/document.createElement "ul")]
    (reagami/render el [:ul [:li {:key "x"} "old"]])
    (let [ul (child-at el 0)
          old (child-at ul 0)]
      (reagami/render el [:ul [:span {:key "x"} "new"]])
      (is (not= old (child-at ul 0)) "different node")
      (is (= "<ul><span>new</span></ul>" (.-innerHTML el))))))

(deftest mixed-keyed-unkeyed-test
  (let [el (js/document.createElement "ul")]
    (reagami/render el [:ul [:li "header"] [:li {:key "a"} "A"] [:li {:key "b"} "B"]])
    (let [ul (child-at el 0)
          header (child-at ul 0) a (child-at ul 1) b (child-at ul 2)]
      (reagami/render el [:ul [:li "header"] [:li {:key "b"} "B"] [:li {:key "a"} "A"]])
      (is (= header (child-at ul 0)) "unkeyed header kept")
      (is (= b (child-at ul 1)) "b kept")
      (is (= a (child-at ul 2)) "a kept"))))

(deftest swap-test
  (let [el (js/document.createElement "ul")]
    (reagami/render el (into [:ul] (mapv (fn [i] [:li {:key i} (str i)]) (range 6))))
    (let [ul (child-at el 0)
          n0 (child-at ul 0) n1 (child-at ul 1) n4 (child-at ul 4)]
      (reagami/render el [:ul
                          [:li {:key 0} "0"] [:li {:key 4} "4"] [:li {:key 2} "2"]
                          [:li {:key 3} "3"] [:li {:key 1} "1"] [:li {:key 5} "5"]])
      (is (= n0 (child-at ul 0)) "0 unmoved")
      (is (= n4 (child-at ul 1)) "4 moved, identity kept")
      (is (= n1 (child-at ul 4)) "1 moved, identity kept")
      (is (= "<ul><li>0</li><li>4</li><li>2</li><li>3</li><li>1</li><li>5</li></ul>"
             (.-innerHTML el))))))

(deftest replace-all-keys-test
  (let [el (js/document.createElement "ul")]
    (reagami/render el [:ul [:li {:key "a"} "A"] [:li {:key "b"} "B"]])
    (let [ul (child-at el 0)
          a (child-at ul 0)]
      (reagami/render el [:ul [:li {:key "c"} "C"] [:li {:key "d"} "D"]])
      (is (not= a (child-at ul 0)) "all keys changed -> recreated")
      (is (= "<ul><li>C</li><li>D</li></ul>" (.-innerHTML el))))))
