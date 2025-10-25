(ns basic-test
  (:require
   ["../reagami.mjs" :as reagami]
   ["node:assert" :as assert]))

(defn render-test []
  (let [el (js/document.createElement "div")]
    (reagami/render el [:div "hello"])
    (assert/strictEqual (.-innerHTML el) "<div>hello</div>")
    (println "✓ render test passed")))
