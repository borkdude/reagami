(ns app
  (:require [reagami.core :as r]))

(def N 2000)

(defn now [] (js/performance.now))

(defn rows [n start]
  (mapv (fn [i] {:id (+ start i)}) (range n)))

(defn row [it]
  ;; :key drives keyed reconciliation in the NEW reagami. The OLD reagami has no
  ;; key support, so it diffs positionally and rebuilds on count changes.
  ;; Each row has an uncontrolled <input> and an animated dot, so node reuse vs
  ;; recreation is visible: typed text survives + the dot keeps spinning when the
  ;; node is reused; both reset when it is recreated.
  [:div.row {:key (:id it)}
   [:span.id (str "#" (:id it))]
   [:input.field {:placeholder "type here"}]
   [:span.dot]])

(defn view [state]
  (into [:div.list] (mapv row (:rows state))))

(defn start [side]
  (let [state (atom {:rows (rows N 0)})
        next-id (atom N)
        el (js/document.getElementById (str "app-" side))
        time-el (js/document.getElementById (str "time-" side))
        render! (fn []
                  (let [t0 (now)]
                    (r/render el (view @state))
                    (set! (.-textContent time-el)
                          (str (.toFixed (- (now) t0) 1) " ms"))))]
    (render!)
    (aset js/window side
          #js {:prepend (fn []
                          (swap! state update :rows
                                 #(into [{:id (swap! next-id inc)}] %))
                          (render!))
               :removeMiddle (fn []
                               (swap! state update :rows
                                      (fn [rs]
                                        (let [m (quot (count rs) 2)]
                                          (into (subvec rs 0 m) (subvec rs (inc m))))))
                               (render!))
               :shuffle (fn []
                          (swap! state update :rows
                                 #(vec (sort-by (fn [_] (js/Math.random)) %)))
                          (render!))
               :reset (fn []
                        (reset! state {:rows (rows N 0)})
                        (reset! next-id N)
                        (render!))})))
