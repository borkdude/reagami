(ns on-render-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [reagami.core :as reagami]))

(def state (atom {:counter 0 :show true}))

(def events (atom #js []))

(defn sub-component [x]
  [:div#sub "Counter in subcomponent: " x])

(def end-state (atom nil))

(defn ui []
  [:div#ui
   [:button#show {:on-click #(swap! state update :show not)}
    "Show? " (:show @state)]
   (when (:show @state)
     [:div
      [:div#my-custom {:on-render
                       (fn [node lifecycle data]
                         (when-not (:data @state)
                           (swap! events #(doto % (.push #?(:squint lifecycle
                                                            :cljs (name lifecycle)))))
                           (case lifecycle
                             (:mount :update)
                             (do (reagami/render node [sub-component (:counter @state)])
                                 (update data :updates (fnil inc 0)))
                             :unmount (swap! end-state assoc :data data))))}]
      [:button#inc {:on-click #(swap! state update :counter inc)}
       "Click me!"]])])

(deftest render-test
  (let [el (js/document.createElement "div")
        !div (atom nil)]
    ;; adding unexpected element crashes if we don't clear it beforehand
    (.appendChild el (doto (js/document.createElement "div")
                       (set! -id "loading")))
    ;; connect to DOM, so .-isConnected returns true
    (js/document.body.appendChild el)
    (add-watch state ::render (fn [_ _ _ _] (reagami/render el [ui])))
    (reagami/render el [ui])
    (is (= ["mount"] (vec @events)))
    (is (str/includes? (.-innerHTML el) "Counter in subcomponent: 0"))
    (reset! !div (js/document.querySelector "#sub"))
    (reagami/render el [ui])
    (is (identical? @!div (js/document.querySelector "#sub")))
    (is (= ["mount" "update"] (vec @events)))
    (.click (js/document.querySelector "#inc"))
    (is (str/includes? (.-innerHTML el) "Counter in subcomponent: 1"))
    (is (= ["mount" "update" "update"] (vec @events)))
    (.click (js/document.querySelector "#show"))
    (is (= ["mount" "update" "update" "unmount"] (vec @events)))
    (.click (js/document.querySelector "#show"))
    (is (= ["mount" "update" "update" "unmount" "mount"] (vec @events)))
    (is (str/includes? (.-innerHTML el) "Counter in subcomponent: 1"))
    (.click (js/document.querySelector "#inc"))
    (is (str/includes? (.-innerHTML el) "Counter in subcomponent: 2"))
    (is (= {:updates 3} (:data @end-state)))))
