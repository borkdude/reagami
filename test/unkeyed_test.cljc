(ns unkeyed-test
  (:require
   ["node:assert" :as assert]
   [reagami.core :as reagami]))

(defn- child-at [^js el i]
  (aget (.-children el) i))

(defn grow-reuses-prefix-test []
  (let [el (js/document.createElement "ul")]
    (reagami/render el [:ul [:li "a"]])
    (let [ul (child-at el 0)
          a (child-at ul 0)]
      (reagami/render el [:ul [:li "a"] [:li "b"]])
      (assert/strictEqual (child-at ul 0) a "prefix node reused on grow")
      (assert/strictEqual (.-innerHTML el) "<ul><li>a</li><li>b</li></ul>")
      (println "✓ unkeyed grow reuses prefix"))))

(defn shrink-reuses-prefix-test []
  (let [el (js/document.createElement "ul")]
    (reagami/render el [:ul [:li "a"] [:li "b"] [:li "c"]])
    (let [ul (child-at el 0)
          a (child-at ul 0) b (child-at ul 1)]
      (reagami/render el [:ul [:li "a"] [:li "b"]])
      (assert/strictEqual (child-at ul 0) a "node 0 reused on shrink")
      (assert/strictEqual (child-at ul 1) b "node 1 reused on shrink")
      (assert/strictEqual (.-childElementCount ul) 2 "tail removed")
      (assert/strictEqual (.-innerHTML el) "<ul><li>a</li><li>b</li></ul>")
      (println "✓ unkeyed shrink reuses prefix"))))
