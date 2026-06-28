(ns lite-app
  (:require [reagami.core :as r]))

(defn ^:export main []
  (let [el (.getElementById js/document "app")
        rows (fn [ids] (into [:ul] (mapv (fn [i] [:li {:key i} (str "row " i)]) ids)))]
    (r/render el (rows (range 100)))
    (r/render el (rows (range 99 -1 -1)))
    (r/render el (rows (range 50)))))
