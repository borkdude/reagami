(ns event-listener-test
  (:require
   [clojure.test :refer [deftest is]]
   [reagami.core :as reagami]))

(deftest event-listener-test
  (let [el (js/document.createElement "div")
        state (atom [])]
    (reagami/render el [:button {:on-click #(swap! state conj :click)}])
    (.click (.querySelector el "button"))
    (is (= [:click] @state))
    (reagami/render el [:button {:on-click #(swap! state conj :clack)}])
    (.click (.querySelector el "button"))
    (is (= [:click :clack] @state))))
