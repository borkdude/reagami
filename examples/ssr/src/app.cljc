(ns app)

;; This namespace runs twice: on babashka to produce the HTML, and in the
;; browser to take that HTML over. Nothing in it is specific to either.

(defonce !state (atom {:count 0}))

(defn inc-count! [_]
  (swap! !state update :count inc))

(defn app [state]
  [:div
   [:h1 "reagami ssr"]
   [:p "This came from babashka. The browser adopted it rather than rebuilding it."]
   [:p#count (str "count: " (:count state))]
   [:button#inc {:on-click inc-count!} "+1"]])
