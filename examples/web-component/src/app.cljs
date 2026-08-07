(ns app
  (:require
   [reagami.core :as r]
   [todo-list]))

;; Reagami uses <todo-list> like any other tag. It knows the element's two
;; attributes and its two events, and nothing else.

(defonce !state (atom {:log [] :label "Groceries"}))

(defn log! [line]
  (swap! !state update :log conj line))

(defn app []
  (let [{:keys [log label]} @!state]
    [:div
     [:h2 "A web component, rendered by Reagami"]
     [:label "Heading: "
      [:input {:value label
               :on-input #(swap! !state assoc :label (.. % -target -value))}]]
     ;; label reaches the element as an attribute, because the tag has a hyphen.
     ;; item-added has no on* property, so Reagami listens with addEventListener.
     [:todo-list {:label label
                  :placeholder "What needs doing?"
                  :on-item-added #(log! (str "added " (.. % -detail -text)))
                  :on-item-changed #(log! (str "changed " (.. % -detail -text)
                                              (when (.. % -detail -done) " (done)")))
                  :on-item-removed #(log! (str "removed " (.. % -detail -text)))}]
     [:h3 "What the app heard"]
     (if (empty? log)
       [:p "Nothing yet. Add an item above."]
       (into [:ol] (map (fn [line] [:li line]) log)))]))

(defn render! []
  (r/render (js/document.getElementById "app") [app]))

(add-watch !state ::render (fn [_ _ _ _] (render!)))
(render!)
