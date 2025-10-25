(ns basic-test
  (:require
   ["../reagami.mjs" :as reagami]
   ["node:assert" :as assert]))

(defn render-test []
  (let [el (js/document.createElement "div")]
    (reagami/render el [:div "hello"])
    (assert/strictEqual (.-innerHTML el) "<div>hello</div>")
    (println "✓ render test passed")))

(defn class-test []
  (let [el (js/document.createElement "div")]
    (reagami/render el [:div#myid.class1.class2 {:class :myclass}
                        "hello"])
    (assert/strictEqual (.-innerHTML el) "<div class=\"myclass class1 class2\" id=\"myid\">hello</div>")
    (reagami/render el [:div#myid.class1.class2
                        "hello"])
    (assert/strictEqual (.-innerHTML el) "<div class=\"class1 class2\" id=\"myid\">hello</div>")
    (println "✓ class test passed")))
