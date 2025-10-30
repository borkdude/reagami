(ns reagami.bench
  (:require [reagami.core :as new]
            [reagami.old :as old]))

(defn measure [label f]
  (let [t0 (js/performance.now)
        _ (f)
        t1 (js/performance.now)]
    (js/console.log label ":" (- t1 t0) "ms")
    (- t1 t0)))

(def state (atom {:text "My text"}))

(defn hiccup []
  [:div.app
   (for [i (range 100)]
     [:div.item {:style {:color (if (odd? i) "red" "blue")}}
      "Item " i " " (:text @state)
      [:button {:on-click #(swap! state update :text str "x")}
       "Click me"]])])

(def container1 (js/document.createElement "div"))
(def container2 (js/document.createElement "div"))

(.appendChild (js/document.querySelector "#app") container1 container2)

(defn benchmark []
  (let []
    (js/console.log "Warming up...")
    (old/render container1 (hiccup))
    (new/render container2 (hiccup))

    (js/console.log "Benchmarking...")
    (let [old-time (measure "Old renderer" #(dotimes [_ 1000] (old/render container1 (hiccup))))
          new-time (measure "New renderer" #(dotimes [_ 1000] (new/render container2 (hiccup))))]
      (js/console.log "Speedup:" (/ old-time new-time) "× faster"))))

(add-watch state ::state benchmark)
