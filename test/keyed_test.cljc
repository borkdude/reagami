(ns keyed-test
  (:require
   ["node:assert" :as assert]
   [reagami.core :as reagami]))

(defn- child-at [^js el i]
  (aget (.-children el) i))

(defn key-not-leaked-test []
  (let [el (js/document.createElement "ul")]
    (reagami/render el [:ul [:li {:key "a"} "A"] [:li {:key "b"} "B"]])
    (assert/strictEqual (.-innerHTML el) "<ul><li>A</li><li>B</li></ul>")
    (println "✓ key not leaked as attribute")))

(defn reorder-test []
  (let [el (js/document.createElement "ul")]
    (reagami/render el [:ul [:li {:key "a"} "A"] [:li {:key "b"} "B"] [:li {:key "c"} "C"]])
    (let [ul (child-at el 0)
          a (child-at ul 0) b (child-at ul 1) c (child-at ul 2)]
      (reagami/render el [:ul [:li {:key "c"} "C"] [:li {:key "a"} "A"] [:li {:key "b"} "B"]])
      (assert/strictEqual (child-at ul 0) c "c kept identity")
      (assert/strictEqual (child-at ul 1) a "a kept identity")
      (assert/strictEqual (child-at ul 2) b "b kept identity")
      (assert/strictEqual (.-innerHTML el) "<ul><li>C</li><li>A</li><li>B</li></ul>")
      (println "✓ reorder preserves identity"))))

(defn insert-middle-test []
  (let [el (js/document.createElement "ul")]
    (reagami/render el [:ul [:li {:key "a"} "A"] [:li {:key "c"} "C"]])
    (let [ul (child-at el 0)
          a (child-at ul 0) c (child-at ul 1)]
      (reagami/render el [:ul [:li {:key "a"} "A"] [:li {:key "b"} "B"] [:li {:key "c"} "C"]])
      (assert/strictEqual (child-at ul 0) a "a kept")
      (assert/strictEqual (child-at ul 2) c "c kept")
      (assert/strictEqual (.-innerHTML el) "<ul><li>A</li><li>B</li><li>C</li></ul>")
      (println "✓ insert in middle"))))

(defn delete-middle-test []
  (let [el (js/document.createElement "ul")]
    (reagami/render el [:ul [:li {:key "a"} "A"] [:li {:key "b"} "B"] [:li {:key "c"} "C"]])
    (let [ul (child-at el 0)
          a (child-at ul 0) c (child-at ul 2)]
      (reagami/render el [:ul [:li {:key "a"} "A"] [:li {:key "c"} "C"]])
      (assert/strictEqual (child-at ul 0) a "a kept")
      (assert/strictEqual (child-at ul 1) c "c kept")
      (assert/strictEqual (.-innerHTML el) "<ul><li>A</li><li>C</li></ul>")
      (println "✓ delete from middle"))))

(defn tag-mismatch-test []
  (let [el (js/document.createElement "ul")]
    (reagami/render el [:ul [:li {:key "x"} "old"]])
    (let [ul (child-at el 0)
          old (child-at ul 0)]
      (reagami/render el [:ul [:span {:key "x"} "new"]])
      (assert/notStrictEqual (child-at ul 0) old "different node")
      (assert/strictEqual (.-innerHTML el) "<ul><span>new</span></ul>")
      (println "✓ tag mismatch forces recreate"))))

(defn mixed-keyed-unkeyed-test []
  (let [el (js/document.createElement "ul")]
    (reagami/render el [:ul [:li "header"] [:li {:key "a"} "A"] [:li {:key "b"} "B"]])
    (let [ul (child-at el 0)
          header (child-at ul 0) a (child-at ul 1) b (child-at ul 2)]
      (reagami/render el [:ul [:li "header"] [:li {:key "b"} "B"] [:li {:key "a"} "A"]])
      (assert/strictEqual (child-at ul 0) header "unkeyed header kept")
      (assert/strictEqual (child-at ul 1) b "b kept")
      (assert/strictEqual (child-at ul 2) a "a kept")
      (println "✓ mixed keyed/unkeyed"))))

(defn swap-test []
  (let [el (js/document.createElement "ul")]
    (reagami/render el (into [:ul] (mapv (fn [i] [:li {:key i} (str i)]) (range 6))))
    (let [ul (child-at el 0)
          n0 (child-at ul 0) n1 (child-at ul 1) n4 (child-at ul 4)]
      (reagami/render el [:ul
                          [:li {:key 0} "0"] [:li {:key 4} "4"] [:li {:key 2} "2"]
                          [:li {:key 3} "3"] [:li {:key 1} "1"] [:li {:key 5} "5"]])
      (assert/strictEqual (child-at ul 0) n0 "0 unmoved")
      (assert/strictEqual (child-at ul 1) n4 "4 moved, identity kept")
      (assert/strictEqual (child-at ul 4) n1 "1 moved, identity kept")
      (assert/strictEqual (.-innerHTML el)
                          "<ul><li>0</li><li>4</li><li>2</li><li>3</li><li>1</li><li>5</li></ul>")
      (println "✓ swap keeps identity"))))

(defn replace-all-keys-test []
  (let [el (js/document.createElement "ul")]
    (reagami/render el [:ul [:li {:key "a"} "A"] [:li {:key "b"} "B"]])
    (let [ul (child-at el 0)
          a (child-at ul 0)]
      (reagami/render el [:ul [:li {:key "c"} "C"] [:li {:key "d"} "D"]])
      (assert/notStrictEqual (child-at ul 0) a "all keys changed -> recreated")
      (assert/strictEqual (.-innerHTML el) "<ul><li>C</li><li>D</li></ul>")
      (println "✓ replace all keys"))))
