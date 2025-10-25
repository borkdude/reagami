(ns test-runner
  {:clj-kondo/config '{:linters {:unresolved-symbol {:exclude [js-await]}}}}
  (:refer-clojure :exclude [munge])
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

(defn munge [x]
  (str/replace x "-" "_"))

(defn ^:async run-test-in-file [file var]
  (let [mod (js-await (js/import (str "./" file)))]
    (js/console.log "Running" var "from" file)
    ((aget mod (munge var)))))

(defn ^:async run-all-tests [dir args]
  (if-let [ns (-> args :values :ns)]
    (let [file (-> ns
                   (str/replace "-" "_")
                   (str ".mjs"))]
      (js-await (run-tests-in-file file))
      (println (str/replace "✓ All tests in %s finished" "%s" ns)))
    (if-let [var (-> args :values :var)]
      (let [[ns var-name] (str/split var "/")
            file (-> ns
                     (str/replace "-" "_")
                     (str ".mjs"))]
        (js-await (run-test-in-file file var-name))
        (println (str/replace "✓ All tests in %s finished" "%s" ns)))
      (do (doseq [file (find-test-files dir)]
            (js-await (run-tests-in-file file)))
          (println "✓ All tests finished")))))

(def args (parseArgs {:options {:ns {:type :string}
                                :var {:type :string}}}))

(run-all-tests "test" args)
