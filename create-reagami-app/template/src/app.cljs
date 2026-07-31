(ns app
  (:require [reagami.core :as r]))

(defonce !state (atom {:count 0}))

(def root (js/document.getElementById "app"))

(defn view [{:keys [count]}]
  [:div
   [:h1 "reagami"]
   [:p (str "count: " count)]
   [:button {:on-click (fn [_] (swap! !state update :count inc))} "+1"]])

(defn render! []
  (r/render root (view @!state)))

(add-watch !state ::render (fn [_ _ _ _] (render!)))
(render!)

;; the Squint Vite plugin calls this after a hot swap, so an edit repaints
;; and the atom keeps its state
(defn ^:dev/after-load re-render [] (render!))
