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

;; Deterministic pseudo-shuffle so every trial sees the same ordering
(defn- shuffle-seed [xs seed]
  (let [arr (to-array xs)
        n (alength arr)]
    (loop [i (dec n) s seed]
      (if (<= i 0)
        (vec arr)
        (let [s' (mod (+ (* s 1103515245) 12345) 2147483648)
              j (mod s' (inc i))
              tmp (aget arr i)]
          (aset arr i (aget arr j))
          (aset arr j tmp)
          (recur (dec i) s'))))))

(def reorder-state (atom 0))

(defn hiccup-reorder []
  (let [seed @reorder-state
        order (shuffle-seed (range 100) seed)]
    [:ul
     (for [i order]
       [:li {:key i :style {:color (if (odd? i) "red" "blue")}}
        "Item " i])]))

(defn run-scenario [label iterations trials render-fn hiccup-fn]
  (let [container1 (js/document.createElement "div")
        container2 (js/document.createElement "div")]
    (js/console.log (str "=== " label " ==="))
    (js/console.log "Warming up...")
    (render-fn :old container1 (hiccup-fn))
    (render-fn :new container2 (hiccup-fn))
    (js/console.log "Benchmarking"
                    (str iterations " iterations × " trials " trials"))
    (dotimes [trial trials]
      (when (> trials 1)
        (js/console.log "--- trial" (inc trial) "---"))
      (reset! reorder-state 1)
      (let [old-time (measure "Old renderer"
                              #(dotimes [_ iterations]
                                 (swap! reorder-state inc)
                                 (render-fn :old container1 (hiccup-fn))))]
        (reset! reorder-state 1)
        (let [new-time (measure "New renderer"
                                #(dotimes [_ iterations]
                                   (swap! reorder-state inc)
                                   (render-fn :new container2 (hiccup-fn))))]
          (js/console.log "Speedup:" (/ old-time new-time) "× faster"))))))

(defn- dispatch-render [which container h]
  (case which
    :old (old/render container h)
    :new (new/render container h)))

(defn benchmark
  ([] (benchmark {:iterations 1000 :trials 1}))
  ([{:keys [iterations trials scenario]}]
   (case (or scenario :default)
     :default (run-scenario "static list" iterations trials dispatch-render hiccup)
     :reorder (run-scenario "reorder list" iterations trials dispatch-render hiccup-reorder)
     :all (do (run-scenario "static list" iterations trials dispatch-render hiccup)
              (run-scenario "reorder list" iterations trials dispatch-render hiccup-reorder)))))
