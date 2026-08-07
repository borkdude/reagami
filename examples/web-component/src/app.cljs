(ns app
  (:require
   [reagami.core :as r]
   [todo-list]))

;; Reagami uses <todo-list> like any other tag. It knows one attribute and
;; three events, and nothing else.

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
     ;; the tag holds a hyphen, so label reaches the element as an attribute.
     ;; the element has no onitemadded property, so Reagami listens with
     ;; addEventListener.
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
