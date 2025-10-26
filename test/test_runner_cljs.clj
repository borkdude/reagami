(ns test-runner-cljs
  (:require [cljs.analyzer.api :as ana]))

(defmacro find-tests [ns]
  (mapv (fn [var]
          (list (symbol (str ns) (str var))))
        (filter #(.endsWith (str %) "-test")
                (keys (ana/ns-publics ns)))))
