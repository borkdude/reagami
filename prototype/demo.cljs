(ns demo
  (:require [reactive :as r]))

(def render-counts #js {})

(defn- bump! [k]
  (aset render-counts k (inc (or (aget render-counts k) 0))))

(defn badge [k]
  [:span.badge "renders: " (bump! k)])

(def todos (r/ratom [{:id 1 :text "Try reagami" :done true}
                     {:id 2 :text "Build reagent-like layer" :done false}]))
(def next-todo-id (volatile! 2))
(def filter-mode (r/ratom :all))
(def new-text (r/ratom ""))
(def clock (r/ratom 0))

(def new-todo
  (r/component
   (fn []
     [:div.card
      [:h3 "New todo " [badge "input"]]
      [:input {:value @new-text
               :placeholder "What needs doing?"
               :on-input #(reset! new-text (.. % -target -value))}]
      [:button {:on-click
                (fn []
                  (let [t (.trim @new-text)]
                    (when (pos? (.-length t))
                      (swap! todos conj {:id (vswap! next-todo-id inc)
                                         :text t :done false})
                      (reset! new-text ""))))}
       "Add"]])))

(defn todo-item [{:keys [id text done]}]
  [:li
   [:label
    [:input {:type "checkbox" :checked done
             :on-change
             (fn []
               (swap! todos (fn [ts]
                              (mapv #(if (= id (:id %))
                                       (update % :done not)
                                       %)
                                    ts))))}]
    [:span {:style (when done "text-decoration: line-through; color: #888")}
     text]]
   [:button.del {:on-click
                 (fn [] (swap! todos (fn [ts]
                                       (vec (filter #(not= id (:id %)) ts)))))}
    "x"]])

(def todo-list
  (r/component
   (fn []
     (let [mode @filter-mode
           ts @todos
           shown (case mode
                   :all ts
                   :active (vec (filter (fn [t] (not (:done t))) ts))
                   ;; keywords are not callable in squint
                   :done (vec (filter #_{:clj-kondo/ignore [:redundant-fn-wrapper]}
                                      (fn [t] (:done t)) ts)))]
       [:div.card
        [:h3 "Todos " [badge "list"]]
        [:div.filters
         (for [m [:all :active :done]]
           [:button {:class (when (= m mode) "active")
                     :on-click #(reset! filter-mode m)}
            m])]
        [:ul
         (for [t shown]
           (todo-item t))]]))))

(def stats
  (r/component
   (fn []
     (let [ts @todos
           done (count (filter #_{:clj-kondo/ignore [:redundant-fn-wrapper]}
                               (fn [t] (:done t)) ts))]
       [:div.card
        [:h3 "Stats " [badge "stats"]]
        [:p (count ts) " todos, " done " done, " (- (count ts) done) " remaining"]]))))

(def clock-view
  (r/component
   (fn []
     [:div.card
      [:h3 "Clock " [badge "clock"]]
      [:p "Ticks: " @clock]])))

(defn app []
  [:main
   [:h1 "Reagami reactive prototype"]
   [:p "Each card is an independent component. Its render badge only
        increments when a ratom it derefs changes."]
   [new-todo]
   [todo-list]
   [stats]
   [clock-view]])

(r/render (js/document.getElementById "app") app)

(js/setInterval #(swap! clock inc) 1000)
