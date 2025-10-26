(ns event-listener-test
  (:require
   ["node:assert" :as assert]
   [reagami.core :as reagami]))

(defn deep-equal
  [expected actual & [msg]]
  (assert/deepStrictEqual actual expected (or msg "")))

(defn event-listener-test []
  (let [el (js/document.createElement "div")
        state (atom [])]
    (reagami/render el [:button {:on-click #(swap! state conj :click)}])
    (.click (.querySelector el "button"))
    (assert/ok (= [:click] @state ))
    (reagami/render el [:button {:on-click #(swap! state conj :clack)}])
    (.click (.querySelector el "button"))
    (deep-equal [:click :clack] @state)
    (println "✓ event listener tests passed")))
