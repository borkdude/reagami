(ns app
  (:require
   [reagami.core :as r]
   [todo-list]))

(defonce !state (atom {:log [] :label "Groceries"}))

(defn log! [line]
  (swap! !state update :log conj line))

(defn app []
  (let [{:keys [log label]} @!state]
    [:div
     [:h2 "A custom element, rendered by Reagami"]
     [:label "Heading: "
      [:input {:value label
               :on-input #(swap! !state assoc :label (.. % -target -value))}]]
     [:todo-list {:label label
                  :on-item-added #(log! (str "added " (.. % -detail -text)))
                  :on-item-changed #(log! (str "changed " (.. % -detail -text)
                                              (when (.. % -detail -done) " (done)")))
                  :on-item-removed #(log! (str "removed " (.. % -detail -text)))}]
     [:h3 "Events"]
     (into [:pre] (map (fn [line] [:code line "\n"]) log))]))

(defn render! []
  (r/render (js/document.getElementById "app") [app]))

(add-watch !state ::render (fn [_ _ _ _] (render!)))
(render!)
