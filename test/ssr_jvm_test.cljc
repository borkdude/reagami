(ns ssr-jvm-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [reagami.ssr :as ssr]
   [ssr-corpus :as corpus]))

(deftest render-test
  (run! (fn [case]
          (testing (pr-str (:hiccup case))
            (is (= (:html case) (ssr/render (:hiccup case))))))
        corpus/cases))

(deftest escaping-test
  (run! (fn [case]
          (testing (pr-str (:hiccup case))
            (is (= (:html case) (ssr/render (:hiccup case))))))
        corpus/escaping-cases))

(deftest component-test
  (let [greet (fn [name] [:p "hi " name])]
    (is (= "<p>hi ann</p>" (ssr/render [greet "ann"])))))

(deftest integral-double-test
  (testing "integral doubles stringify like JS, not like Clojure"
    (is (= "<div width=\"1\"></div>" (ssr/render [:div {:width 1.0}])))))
