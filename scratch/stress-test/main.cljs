(ns main
  (:require
   ["../../lib/reagami/core.mjs" :as r]))

(defn rand
  "Returns a random floating point number between 0 (inclusive) and
  n (default 1) (exclusive)."
  ([] (rand 1))
  ([n] (* (Math/random) n)))

;; state: large table of rows
(def state (atom {:rows [] :next-id 1 :running false :interval-id nil}))

(defn rand-name []
  (let [chars "abcdefghijklmnopqrstuvwxyz"]
    (apply str (repeatedly (+ 3 (rand-int 6)) #(nth chars (rand-int (count chars)))))))

(defn mk-row [id]
  {:id id
   :name (rand-name)
   :score (rand-int 1000)
   :flag (odd? (rand-int 2))})

(defn add-rows! [n]
  (swap! state
    (fn [{:keys [rows next-id] :as s}]
      (let [new-rows (map mk-row (range next-id (+ next-id n)))]
        (-> s
          (assoc :rows (into rows new-rows))
          (update :next-id + n))))))

(defn random-update! []
  ;; change random cells in random rows
  (swap! state
    (fn [{:keys [rows] :as s}]
      (if (empty? rows) s
        (let [i (rand-int (count rows))
              row (nth rows i)
              new-row (cond-> row
                        true (assoc :score (+ (:score row) (rand-int 50) -25))
                        (< (rand) 0.2) (assoc :name (rand-name))
                        (< (rand) 0.1) (update :flag not))]
          (assoc s :rows (assoc (vec rows) i new-row)))))))

(defn shuffle-rows! []
  (swap! state update :rows (fn [rs] (vec (shuffle rs)))))

(defn replace-every-other-element-kind! []
  ;; This toggles a "mode" which forces different element shapes (td vs div) for cells,
  ;; so the patcher must replace nodes rather than just changing text
  (swap! state update :rows
    (fn [rs]
      (vec
        (map-indexed
          (fn [i r]
            (if (even? i)
                   (assoc r :shape :block) ;; will render as a div
                   (assoc r :shape :table))) ;; will render as table cells
          rs)))))

(defn clear-rows! []
  (swap! state assoc :rows [] :next-id 1))

(defn start-fast-loop! []
  (when-not (:running @state)
    (let [id (js/setInterval
               (fn []
                 ;; many mutations per tick
                 (dotimes [_ 10] (random-update!))
                 (when (< (rand) 0.2) (shuffle-rows!))
                 (when (< (rand) 0.05) (add-rows! 5))
                 )
               60)] ;; ~60ms
      (swap! state assoc :running true :interval-id id))))

(defn stop-fast-loop! []
  (let [{:keys [interval-id]} @state]
    (prn :stop interval-id)
    (when interval-id
      (js/clearInterval interval-id)
      (swap! state assoc :running false :interval-id nil))))

;; Component that renders a large table and also forces node-shape replacements
(defn row-view [{:keys [id name score flag shape]}]
  (if (= shape :block)
    ;; render as a block-ish representation (forces different node types)
    [:div.row {:key id :style {:display "flex" :gap "8px" :padding "6px" :border-bottom "1px solid #eee"}}
     [:div.id (str id)]
     [:div.name name]
     [:div.score (str score)]
     [:div.flag (if flag "✓" "·")]]
    ;; regular table-row-like hiccup
    [:div.row-inline {:key id :style {:display "grid" :gridTemplateColumns "50px 1fr 90px 30px" :padding "6px" :border-bottom "1px solid #eee"}}
     [:div.id (str id)]
     [:div.name name]
     [:div.score (str score)]
     [:div.flag (if flag "✓" "·")]]))

(defn big-table []
  (let [{:keys [rows running]} @state]
    [:div
     [:pre (pr-str (dissoc @state :rows))]
     [:div.controls {:style {:marginBottom "12px" :display "flex" :gap "8px"}}
      [:button {:on-click #(add-rows! 100)} "Add 100 rows"]
      [:button {:on-click #(add-rows! 1000)} "Add 1000 rows"]
      [:button {:on-click shuffle-rows!} "Shuffle"]
      [:button {:on-click replace-every-other-element-kind!} "Toggle shapes"]
      [:button {:on-click clear-rows!} "Clear"]
      (if running
        [:button {:on-click stop-fast-loop!} "Stop fast loop"]
        [:button {:on-click start-fast-loop!} "Start fast loop"])]
     ;; summary
     [:div.summary {:style {:marginBottom "8px"}}
      (str "Rows: " (count rows))]
     ;; large container
     [:div.table {:style {:height "60vh" :overflow "auto" :border "1px solid #ddd" :padding "6px"}}
      ;; render rows (vector-of-components)
      (into [:div.rows]
        (map row-view rows))]]))

;; render
(defn render []
  (let [t0 (js/performance.now)]
    (r/render (js/document.querySelector "#app") [big-table])
    (js/requestAnimationFrame
      (fn []
        (js/console.log "frame paint delay"
          (- (js/performance.now) t0) "ms")))))

(add-watch state ::render (fn [_ _ _ _] (render)))

;; seed with some rows initially
(add-rows! 10000)
(render)
