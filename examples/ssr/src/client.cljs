(ns client
  (:require
   [app]
   [reagami.core :as r]))

(def root (js/document.getElementById "app"))

(defn render! []
  (let [result (r/render root [app/app @app/!state])]
    ;; :created is how many DOM nodes reagami had to build. zero means it
    ;; adopted every node the server sent instead of replacing them.
    (js/console.log (str "created " (:created result)
                         ", adopted " (:adopted result)))))

(reset! app/!state (js/JSON.parse (.-textContent (js/document.getElementById "state"))))
(add-watch app/!state ::render (fn [_ _ _ _] (render!)))

(render!)

;; an edit to app.cljc hot-swaps and repaints, keeping the atom's state
(defn ^:dev/after-load re-render [] (render!))
