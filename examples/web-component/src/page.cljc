(ns page)

;; Loaded twice: on Babashka to produce the HTML, and in the browser to adopt it.
;; A custom element's host tag and its attributes serialize. Its shadow root does
;; not, so the server sends an empty element and the browser fills it once the
;; class is defined.

(def label "Groceries")

(defn page []
  [:div
   [:h2 "Server rendered, then hydrated"]
   [:p "The server sent the tag and its attributes. Nothing inside it: a shadow
        root cannot be written as HTML. The element fills itself in the browser."]
   [:todo-list {:label label}]])
