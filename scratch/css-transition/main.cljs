(ns main
  (:require
   ["../../reagami.mjs" :as reagami]))

(def state (atom {:hover false}))

(defn box-component []
  [:div {:style {:width "100px"
                 :height "100px"
                 :background-color (if (:hover @state) "red" "blue")
                 :transition "background-color 0.5s ease, transform 0.5s ease"
                 :transform (if (:hover @state) "scale(1.5)" "scale(1)")
                 :display "flex"
                 :align-items "center"
                 :justify-content "center"
                 :color "white"
                 :cursor "pointer"}
         :on-mouseenter #(swap! state update :hover not)
         :on-mouseleave #(swap! state update :hover not)}
   "Hover me"])

(defn root-component []
  [:div
   [:pre (pr-str @state)]
   [box-component]])

(def root
  (or (js/document.querySelector "#app")
      (doto (js/document.createElement "div")
        (set! -id "app")
        (js/document.body.prepend))))

(defn render []
  (reagami/render root [root-component]))

(add-watch state ::render
           (fn [_ _ _ _] (render)))

(render)
