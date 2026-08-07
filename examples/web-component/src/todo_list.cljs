(ns todo-list
  (:require
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
  (field -items #js [])
  (field -next-id 0)

  (constructor [this]
    (super)
    (set! -shadow (.attachShadow this #js {:mode "open"})))

  Object
  ;; primitives travel as attributes, with a property that mirrors them
  (^:get label [this] (or (.getAttribute this "label") "To do"))
  (^:set label [this v] (.setAttribute this "label" (str v)))

  (^:get placeholder [this] (or (.getAttribute this "placeholder") "New item"))
  (^:set placeholder [this v] (.setAttribute this "placeholder" (str v)))

  ;; the list is the element's own state. a copy goes out, so a caller cannot
  ;; reach in and change it
  (^:get items [this] (.slice -items))

  (upgradeProperty [this prop]
    ;; a property set before this element upgraded sits on the instance and
    ;; hides the accessor. take the value, drop the instance property, and set
    ;; it again so the setter runs.
    (when (.hasOwnProperty this prop)
      (let [v (aget this prop)]
        (js-delete this prop)
        (aset this prop v))))

  (connectedCallback [this]
    (.upgradeProperty this "label")
    (.upgradeProperty this "placeholder")
    (.render this))

  (attributeChangedCallback [this _name _old _new]
    (when -shadow (.render this)))

  (emit [this event-name detail]
    ;; composed lets the event leave the shadow root, bubbles lets a parent
    ;; listen for every item at once
    (.dispatchEvent this (js/CustomEvent. event-name
                           #js {:detail detail :bubbles true :composed true})))

  (addItem [this text]
    (let [t (.trim (str text))]
      (when (pos? (.-length t))
        (set! -next-id (inc -next-id))
        (let [item #js {:id -next-id :text t}]
          (.push -items item)
          (.render this)
          (.emit this "item-added" item)))))

  (removeItem [this id]
    (let [idx (.findIndex -items (fn [item] (identical? id (.-id item))))]
      (when-not (identical? -1 idx)
        (let [item (aget (.splice -items idx 1) 0)]
          (.render this)
          (.emit this "item-removed" item)))))

  (submit [this e]
    (.preventDefault e)
    (let [input (.querySelector -shadow "input")]
      (.addItem this (.-value input))
      (set! (.-value input) "")))

  (render [this]
    (r/render -shadow
      [:div
       [:style css]
       [:h3 (.-label this)]
       (if (zero? (.-length -items))
         [:p.empty "Nothing to do."]
         (into [:ul]
               (map (fn [item]
                      [:li {:key (.-id item)}
                       [:span.text (.-text item)]
                       [:button.remove {:on-click (fn [_] (.removeItem this (.-id item)))
                                        :aria-label (str "Remove " (.-text item))}
                        "×"]])
                    -items)))
       [:form {:on-submit (fn [e] (.submit this e))}
        [:input {:type "text" :placeholder (.-placeholder this)
                 :aria-label (.-placeholder this)}]
        [:button {:type "submit"} "Add"]]])))

;; defining twice throws, which happens when a page loads this module more than once
(when-not (.get js/customElements "todo-list")
  (.define js/customElements "todo-list" TodoList))
