(ns ssr-nbb-test
  "SSR under nbb, which expands reagami.ssr's macros in sci rather than
  compiling them. Run with: nbb --classpath src:test test/ssr_nbb_test.cljs"
  (:require
   [clojure.test :as t :refer [deftest is testing]]
   [reagami.ssr :as ssr]
   [ssr-corpus :as corpus]))

;; non-zero exit on failure or error so CI and shells detect it
(def ^:private old-fail (get-method t/report [:cljs.test/default :fail]))
(defmethod t/report [:cljs.test/default :fail] [m]
  (set! (.-exitCode js/process) 1)
  (old-fail m))

(def ^:private old-error (get-method t/report [:cljs.test/default :error]))
(defmethod t/report [:cljs.test/default :error] [m]
  (set! (.-exitCode js/process) 1)
  (old-error m))

(deftest render-test
  (run! (fn [case]
          (testing (pr-str (:hiccup case))
            (is (= (:html case) (ssr/render (:hiccup case))))))
        corpus/cases))

(deftest escaping-test
  (run! (fn [case]
          (testing (pr-str (:hiccup case))
            (is (= (:html case) (ssr/render (:hiccup case))))))
        corpus/escaping-cases))

(deftest component-test
  (let [greet (fn [name] [:p "hi " name])]
    (is (= "<p>hi ann</p>" (ssr/render [greet "ann"])))))

(t/run-tests 'ssr-nbb-test)
