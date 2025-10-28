(ns test-runner-cljs
  (:require
   [basic-test]
   [event-listener-test]
   [install-jsdom]
   [svg-test])
  #?@(:squint []
      :cljs [(:require-macros [test-runner-cljs :refer [find-tests]])]))

#?(:squint (defn find-tests [_]))

;; NOTE: these must be at the top level, or exit code won't be 1 on failure!
(find-tests basic-test)
(find-tests event-listener-test)
(find-tests svg-test)

(defn init []
  )
