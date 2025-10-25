(ns test-runner
  {:clj-kondo/config '{:linters {:unresolved-symbol {:exclude [js-await]}}}}
  (:require
   ["./jsdom.mjs"]
   ["node:fs" :as fs]
   ["node:util" :refer [parseArgs]]
   [clojure.string :as str]))

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

(defn ^:async run-all-tests [dir args]
  (if-let [ns (-> args :values :ns)]
    (let [file (-> ns
                   (str/replace "-" "_")
                   (str ".mjs"))]
      (js-await (run-tests-in-file file))
      (println (str/replace "✓ All tests in %s finished" "%s" ns)))
    (do (doseq [file (find-test-files dir)]
          (js-await (run-tests-in-file file)))
        (println "✓ All tests finished"))))

(def args (parseArgs {:options {:ns {:type :string}
                                :var {:type :string}}}))

(run-all-tests "test" args)
