(ns todo-list
  (:require
   [clojure.string :as str]
   [reagami.core :as r]
   [squint.core :refer [defclass]]))

;; A <todo-list> element. Reagami renders its shadow root. Nothing outside this
;; file knows that, and nothing in here knows who uses the element.

(def ^:private css "
  :host { display: block; font-family: system-ui, sans-serif; max-width: 22rem; }
  h3 { margin: 0 0 0.5rem; font-size: 1rem; }
  ul { list-style: none; margin: 0 0 0.5rem; padding: 0; }
  li { display: flex; align-items: center; gap: 0.5rem; padding: 0.25rem 0;
       border-bottom: 1px solid #eee; }
  .text { flex: 1; }
  .empty { color: #888; margin: 0 0 0.5rem; }
  form { display: flex; gap: 0.5rem; }
  input { flex: 1; padding: 0.3rem; }
  button { cursor: pointer; }
  .remove { border: none; background: none; font-size: 1.2rem; color: #c00; }
")

(defclass TodoList
  (extends js/HTMLElement)
  (^:static field observedAttributes #js ["label" "placeholder"])
  (field -shadow nil)
  (field -state (atom {:items [] :next-id 0 :draft ""}))

  (constructor [this]
    (super)
    (set! -shadow (.attachShadow this #js {:mode "open"}))
    (add-watch -state ::render (fn [_ _ _ _] (.render this))))

  Object
  ;; primitives travel as attributes, with a property that mirrors them
  (^:get label [this] (or (.getAttribute this "label") "To do"))
  (^:set label [this v] (.setAttribute this "label" (str v)))

  (^:get placeholder [this] (or (.getAttribute this "placeholder") "New item"))
  (^:set placeholder [this v] (.setAttribute this "placeholder" (str v)))

  ;; a squint map is a JS object and a vector is an array, so a caller reading
  ;; item.text needs no conversion
  (^:get items [this] (:items @-state))

  (upgradeProperty [this prop]
    ;; a property set before this element upgraded sits on the instance and
    ;; hides the accessor. take the value, drop the instance property, and set
    ;; it again so the setter runs.
    (when (.hasOwnProperty this prop)
      (let [v (aget this prop)]
        (js-delete this prop)
        (aset this prop v))))

  (syncAttributes [this]
    ;; read through the getters, so the defaults live in one place
    (swap! -state assoc
           :label (.-label this)
           :placeholder (.-placeholder this)))

  (connectedCallback [this]
    (.upgradeProperty this "label")
    (.upgradeProperty this "placeholder")
    (.syncAttributes this))

  (attributeChangedCallback [this _name _old _new]
    (.syncAttributes this))

  (emit [this event-name detail]
    ;; composed lets the event leave the shadow root, bubbles lets a parent
    ;; listen for every item at once
    (.dispatchEvent this (js/CustomEvent. event-name
                           #js {:detail detail :bubbles true :composed true})))

  (addItem [this text]
    (let [text (str/trim (str text))]
      (when-not (str/blank? text)
        (let [item {:id (:next-id @-state) :text text}]
          (swap! -state #(-> %
                             (update :items conj item)
                             (update :next-id inc)))
          (.emit this "item-added" item)))))

  (removeItem [this id]
    (when-let [item (first (filter #(= id (:id %)) (:items @-state)))]
      (swap! -state update :items #(vec (remove (fn [i] (= id (:id i))) %)))
      (.emit this "item-removed" item)))

  (submit [this e]
    (.preventDefault e)
    (.addItem this (:draft @-state))
    (swap! -state assoc :draft ""))

  (render [this]
    (let [{:keys [items draft label placeholder]} @-state]
      (r/render -shadow
        [:div
         [:style css]
         [:h3 label]
         (if (empty? items)
           [:p.empty "Nothing to do."]
           (into [:ul]
                 (map (fn [{:keys [id text]}]
                        [:li {:key id}
                         [:span.text text]
                         [:button.remove {:on-click (fn [_] (.removeItem this id))
                                          :aria-label (str "Remove " text)}
                          "×"]])
                      items)))
         [:form {:on-submit (fn [e] (.submit this e))}
          [:input {:type "text" :value draft
                   :on-input (fn [e] (swap! -state assoc :draft (.. e -target -value)))
                   :placeholder placeholder
                   :aria-label placeholder}]
          [:button {:type "submit"} "Add"]]]))))

;; defining twice throws, which happens when a page loads this module more than once
(when-not (.get js/customElements "todo-list")
  (.define js/customElements "todo-list" TodoList))
