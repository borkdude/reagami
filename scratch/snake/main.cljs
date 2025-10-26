(ns main
  (:require
   ["../../reagami.mjs" :as reagami]))

(def size 20) ;; grid size (20x20)
(def cell 10) ;; each cell = 10px

(defonce state
  (atom {:snake [[5 10] [4 10] [3 10]] ; initial snake body
         :dir [1 0] ; moving right
         :food [15 10] ; initial food
         :alive? true}))

(defn random-food []
  [(rand-int size) (rand-int size)])

(defn move-snake []
  (swap! state
    (fn [{:keys [snake dir food alive?] :as st}]
      (if (not alive?)
        st
        (let [head (vec [(+ (nth (first snake) 0) (nth dir 0))
                              (+ (nth (first snake) 1) (nth dir 1))])
                   ate? (= head food)
                   new-snake (vec (cons head (if ate? snake (butlast snake))))
                   x (nth head 0)
                   y (nth head 1)
                   hit-wall? (or (< x 0) (< y 0)
                               (>= x size) (>= y size))
                   hit-self? (some #(= head %) (rest new-snake))]
               (cond
                 hit-wall? (assoc st :alive? false)
                 hit-self? (assoc st :alive? false)
                 ate? (assoc st :snake new-snake :food (random-food))
                 :else (assoc st :snake new-snake)))))))

;; Timer loop
(js/setInterval move-snake 200)

;; Keyboard controls
(.addEventListener js/window "keydown"
  (fn [e]
    (let [key (.-key e)]
      (swap! state update :dir
                         (fn [dir]
                           (case key
                             "ArrowUp" (if (= dir [0 1]) dir [0 -1])
                             "ArrowDown" (if (= dir [0 -1]) dir [0 1])
                             "ArrowLeft" (if (= dir [1 0]) dir [-1 0])
                             "ArrowRight" (if (= dir [-1 0]) dir [1 0])
                             dir))))))

(defn cell-rect [[x y] color]
  [:rect {:x (* x cell) :y (* y cell)
          :width cell :height cell
          :fill color}])

(defn game-board []
  (let [{:keys [snake food alive?]} @state]
    [:div
     [:svg {:width (* size cell) :height (* size cell)
            :style {:border "1px solid black"
                    :background "#eef"}}
      ;; snake
      (for [part snake]
        ^{:key (str part)} [cell-rect part "green"])
      ;; food
      [cell-rect food "red"]
      ;; game over overlay
      (when (not alive?)
        [:text {:x 50 :y 100 :font-size 20 :fill "black"} "Game Over!"])]]))

(def root
  (or (js/document.querySelector "#app")
    (doto (js/document.createElement "div")
      (set! -id "app")
      (js/document.body.prepend))))

(defn render []
  (reagami/render root [game-board]))

(add-watch state ::render
  (fn [_ _ _ _] (render)))

(render)
