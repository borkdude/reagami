(ns test-runner-cljs
  (:require [basic-test]
            [install-jsdom]))

(defn init []
  (basic-test/render-test)
  (basic-test/class-test)
  (basic-test/style-test)
  (basic-test/input-test)
  (basic-test/button-test)
  )
