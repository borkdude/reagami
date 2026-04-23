(ns bench-runner
  (:require [install-jsdom]
            [reagami.bench :as bench]))

(bench/benchmark {:iterations 1000 :trials 5 :scenario :all})
