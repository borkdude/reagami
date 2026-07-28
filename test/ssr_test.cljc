(ns ssr-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [reagami.core :as reagami]
   [reagami.ssr :as ssr]
   [ssr-corpus :as corpus]))

(deftest render-test
  (run! (fn [case]
          (testing (pr-str (:hiccup case))
            (is (= (:html case) (ssr/render (:hiccup case))))))
        corpus/cases))

(deftest matches-client-render-test
  (testing "server HTML matches what reagami renders in the DOM"
    (run! (fn [case]
            (testing (pr-str (:hiccup case))
              (let [el (js/document.createElement "div")]
                (reagami/render el (:hiccup case))
                (is (= (or (:dom case) (:html case)) (.-innerHTML el))))))
          corpus/cases)))

(deftest component-test
  (let [greet (fn [name] [:p "hi " name])]
    (is (= "<p>hi ann</p>" (ssr/render [greet "ann"])))
    (is (= "<div><p>hi ann</p></div>" (ssr/render [:div [greet "ann"]])))))
