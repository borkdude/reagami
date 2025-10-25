(ns test-runner
  {:clj-kondo/config '{:linters {:unresolved-symbol {:exclude [js-await]}}}}
  (:require ["fs" :as fs]))

(defn ends-with? [s suffix]
  (.endsWith s suffix))

(defn is-test-file? [filename]
  (ends-with? filename "_test.mjs"))

(defn find-test-files [dir]
  (->> (fs/readdirSync dir)
       (filter is-test-file?)))

(defn ^:async run-tests-in-file [file]
  (let [mod (js-await (js/import (str "./" file)))]
    (doseq [key (js/Object.keys mod)]
      (when (ends-with? key "_test")
        (js/console.log "Running" key "from" file)
        ((aget mod key))))))

(defn ^:async run-all-tests [dir]
  (doseq [file (find-test-files dir)]
    (prn :file file)
    (js-await (run-tests-in-file file)))
  (println "✓ All tests finished"))

(run-all-tests "test")

;; Example usage:
;; (run-all-tests "./tests")
