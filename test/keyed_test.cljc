(ns keyed-test
  (:require
   ["node:assert" :as assert]
   [reagami.core :as reagami]))

(defn- child-at [^js el i]
  (aget (.-children el) i))

(defn basic-test []
  (let [el (js/document.createElement "ul")]
    (reagami/render el [:ul
                        [:li {:key "a"} "A"]
                        [:li {:key "b"} "B"]])
    ;; key must not leak into HTML
    (assert/strictEqual (.-innerHTML el) "<ul><li>A</li><li>B</li></ul>")
    (println "✓ key not leaked as attribute")))

(defn reorder-test []
  (let [el (js/document.createElement "ul")]
    (reagami/render el [:ul
                        [:li {:key "a"} "A"]
                        [:li {:key "b"} "B"]
                        [:li {:key "c"} "C"]])
    (let [ul (child-at el 0)
          li-a (child-at ul 0)
          li-b (child-at ul 1)
          li-c (child-at ul 2)]
      (reagami/render el [:ul
                          [:li {:key "c"} "C"]
                          [:li {:key "a"} "A"]
                          [:li {:key "b"} "B"]])
      ;; Same DOM nodes, reordered
      (assert/strictEqual (child-at ul 0) li-c "c kept identity")
      (assert/strictEqual (child-at ul 1) li-a "a kept identity")
      (assert/strictEqual (child-at ul 2) li-b "b kept identity")
      (assert/strictEqual (.-innerHTML el) "<ul><li>C</li><li>A</li><li>B</li></ul>")
      (println "✓ reorder preserves identity"))))

(defn insert-middle-test []
  (let [el (js/document.createElement "ul")]
    (reagami/render el [:ul
                        [:li {:key "a"} "A"]
                        [:li {:key "c"} "C"]])
    (let [ul (child-at el 0)
          li-a (child-at ul 0)
          li-c (child-at ul 1)]
      (reagami/render el [:ul
                          [:li {:key "a"} "A"]
                          [:li {:key "b"} "B"]
                          [:li {:key "c"} "C"]])
      (assert/strictEqual (child-at ul 0) li-a "a kept")
      (assert/strictEqual (child-at ul 2) li-c "c kept")
      (assert/strictEqual (.-innerHTML el) "<ul><li>A</li><li>B</li><li>C</li></ul>")
      (println "✓ insert in middle"))))

(defn delete-middle-test []
  (let [el (js/document.createElement "ul")]
    (reagami/render el [:ul
                        [:li {:key "a"} "A"]
                        [:li {:key "b"} "B"]
                        [:li {:key "c"} "C"]])
    (let [ul (child-at el 0)
          li-a (child-at ul 0)
          li-c (child-at ul 2)]
      (reagami/render el [:ul
                          [:li {:key "a"} "A"]
                          [:li {:key "c"} "C"]])
      (assert/strictEqual (child-at ul 0) li-a "a kept")
      (assert/strictEqual (child-at ul 1) li-c "c kept")
      (assert/strictEqual (.-innerHTML el) "<ul><li>A</li><li>C</li></ul>")
      (println "✓ delete from middle"))))

(defn tag-mismatch-test []
  (let [el (js/document.createElement "ul")]
    (reagami/render el [:ul
                        [:li {:key "x"} "old"]])
    (let [ul (child-at el 0)
          li-old (child-at ul 0)]
      (reagami/render el [:ul
                          [:span {:key "x"} "new"]])
      ;; Tag changed: must recreate, not reuse
      (assert/notStrictEqual (child-at ul 0) li-old "different node")
      (assert/strictEqual (.-innerHTML el) "<ul><span>new</span></ul>")
      (println "✓ tag mismatch forces recreate"))))

(defn mixed-keyed-unkeyed-test []
  (let [el (js/document.createElement "ul")]
    (reagami/render el [:ul
                        [:li "header"]
                        [:li {:key "a"} "A"]
                        [:li {:key "b"} "B"]])
    (let [ul (child-at el 0)
          li-header (child-at ul 0)
          li-a (child-at ul 1)
          li-b (child-at ul 2)]
      ;; Reorder keyed items; unkeyed header stays positional
      (reagami/render el [:ul
                          [:li "header"]
                          [:li {:key "b"} "B"]
                          [:li {:key "a"} "A"]])
      (assert/strictEqual (child-at ul 0) li-header "unkeyed header kept")
      (assert/strictEqual (child-at ul 1) li-b "b kept")
      (assert/strictEqual (child-at ul 2) li-a "a kept")
      (println "✓ mixed keyed/unkeyed"))))
