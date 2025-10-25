(ns basic-test
  (:require ["node:assert" :as assert]
            ["../reagami.mjs" :as reagami]
            ["./jsdom.mjs" :as jsdom]))

(defn render-test []
  (let [el (js/document.createElement "div")]
    (reagami/render el [:div "hello"])
    (assert/strictEqual (.-innerHTML el) "<div>hello</div>")
    (println "✓ render test passed")))

(render-test)
