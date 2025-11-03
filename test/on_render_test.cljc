(ns on-render-test
  (:require
   ["node:assert" :as assert]
   [clojure.string :as str]
   [reagami.core :as reagami]))

(def state (atom {:counter 0 :show true}))

(def events (atom #js []))

(defn sub-component [x]
  [:div "Counter in subcomponent: " x])

(defn ui []
  [:div#ui
   [:button#show {:on-click #(swap! state update :show not)}
    "Show? " (:show @state)]
   (when (:show @state)
     [:div
      [:div#my-custom {:on-render (fn [node lifecycle]
                                    (swap! events #(doto % (.push #?(:squint lifecycle
                                                                     :cljs (name lifecycle)))))
                                    (case lifecycle
                                      (:mount :update)
                                      (reagami/render node [sub-component (:counter @state)])
                                      :unmount nil))}]
      [:button#inc {:on-click #(swap! state update :counter inc)}
       "Click me!"]])])

(defn render-test []
  (let [el (js/document.createElement "div")]
    ;; connect to DOM, so .-isConnected returns true
    (js/document.body.appendChild el)
    (add-watch state ::render (fn [_ _ _ _] (reagami/render el [ui])))
    (reagami/render el [ui])
    (assert/ok (str/includes? (.-innerHTML el) "Counter in subcomponent: 0"))
    (assert/deepEqual @events #js ["mount"])
    (.click (js/document.querySelector "#inc"))
    (assert/ok (str/includes? (.-innerHTML el) "Counter in subcomponent: 1"))
    (assert/deepEqual @events #js ["mount" "update"])
    (.click (js/document.querySelector "#show"))
    (assert/deepEqual @events #js ["mount" "update" "unmount"])
    (.click (js/document.querySelector "#show"))
    (assert/deepEqual @events #js ["mount" "update" "unmount" "mount"])
    (assert/ok (str/includes? (.-innerHTML el) "Counter in subcomponent: 1"))
    (.click (js/document.querySelector "#inc"))
    (assert/ok (str/includes? (.-innerHTML el) "Counter in subcomponent: 2"))
    (println "✓ on-render-test passed")))
