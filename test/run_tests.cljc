(ns run-tests
  (:require
   [basic-test]
   [clojure.test :as t]
   [event-listener-test]
   [install-jsdom]
   [keyed-test]
   [on-render-test]
   [svg-test]
   [unkeyed-test]))

;; non-zero exit on failure or error so CI and shells detect it
(def ^:private old-fail (get-method t/report [:cljs.test/default :fail]))
(defmethod t/report [:cljs.test/default :fail] [m]
  (set! (.-exitCode js/process) 1)
  (old-fail m))

(def ^:private old-error (get-method t/report [:cljs.test/default :error]))
(defmethod t/report [:cljs.test/default :error] [m]
  (set! (.-exitCode js/process) 1)
  (old-error m))

(defn run []
  (t/run-tests 'basic-test
               'event-listener-test
               'keyed-test
               'on-render-test
               'svg-test
               'unkeyed-test))

(defn ^:export init [] (run))

#?(:squint (run))
