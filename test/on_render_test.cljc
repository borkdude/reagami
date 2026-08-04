(ns on-render-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
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

(defn- mount-root []
  (let [el (js/document.createElement "div")]
    (js/document.body.appendChild el)
    el))

(deftest per-node-state-test
  (testing "nodes sharing one handler keep separate lifecycle state"
    (let [el (mount-root)
          seen (atom [])
          hook (fn [node lifecycle data]
                 (swap! seen conj [(.-textContent node)
                                   #?(:squint lifecycle :cljs (name lifecycle))
                                   (:n data)])
                 {:n (inc (or (:n data) 0))})
          items (fn [ks]
                  (into [:ul] (map (fn [k] [:li {:key k :on-render hook} (str "i" k)]) ks)))]
      (reagami/render el (items [1 2 3]))
      (is (= [["i1" "mount" nil] ["i2" "mount" nil] ["i3" "mount" nil]] @seen))
      (reset! seen [])
      (reagami/render el (items [1 2 3]))
      (is (= [["i1" "update" 1] ["i2" "update" 1] ["i3" "update" 1]] @seen)))))

(deftest latest-handler-test
  (testing "the handler of the newest render runs, not the first one"
    (let [el (mount-root)
          seen (atom [])
          view (fn [label]
                 [:div {:on-render (fn [_ _ _] (swap! seen conj label))} label])]
      (reagami/render el (view "a"))
      (reagami/render el (view "b"))
      (reagami/render el (view "c"))
      (is (= ["a" "b" "c"] @seen)))))

(deftest removed-hook-test
  (testing "dropping :on-render unmounts the hook and stops calling it"
    (let [el (mount-root)
          seen (atom [])
          hook (fn [_ lifecycle _]
                 (swap! seen conj #?(:squint lifecycle :cljs (name lifecycle))))]
      (reagami/render el [:div {:on-render hook} "x"])
      (reset! seen [])
      (reagami/render el [:div "x"])
      (reagami/render el [:div "x"])
      (is (= ["unmount"] @seen)))))
