(ns client
  (:require
   [page]
   [reagami.core :as r]
   [todo-list]))

;; Same hiccup the server rendered. :created is how many nodes Reagami had to
;; build. Zero means it adopted the server's markup, custom element included.
(let [result (r/render (js/document.getElementById "app") [page/page])]
  (js/console.log (str "created " (:created result) ", adopted " (:adopted result)))
  (set! (.-textContent (js/document.getElementById "hydration"))
        (str "hydration built " (:created result) " nodes")))
