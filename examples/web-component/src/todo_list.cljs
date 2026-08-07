(ns todo-list
  (:require
   [clojure.string :as str]
   [reagami.core :as r]
   [squint.core :refer [defclass]]))

;; A <todo-list> element. Reagami renders its shadow root. Nothing outside this
;; file knows that, and nothing in here knows who uses the element.

(def ^:private css "
  :host { display: block; font-family: system-ui, sans-serif; max-width: 24rem; }
  h3 { margin: 0 0 0.5rem; font-size: 1rem; }
  ul { list-style: none; margin: 0 0 0.5rem; padding: 0; }
  li { display: flex; align-items: center; gap: 0.5rem; padding: 0.25rem 0;
       border-bottom: 1px solid #eee; }
  .text { flex: 1; }
  .text.done { text-decoration: line-through; color: #999; }
  .empty { color: #888; margin: 0 0 0.5rem; }
  form { display: flex; gap: 0.5rem; }
  input { flex: 1; padding: 0.3rem; font: inherit; }
  button { cursor: pointer; font: inherit; }
  .toggle { width: 1.4rem; height: 1.4rem; padding: 0; border: 1px solid #bbb;
            border-radius: 50%; background: #fff; color: #2a2; line-height: 1; }
  .toggle[aria-pressed=\"true\"] { border-color: #2a2; }
  .remove { border: none; background: none; }
")

;; every method on the class is public JS API, so everything the element does
;; internally is a function out here that takes the element

(defn- emit!
  ;; dispatched on the element itself, and bubbling, so a parent can listen for
  ;; every item at once
  [^js el event-name detail]
  (.dispatchEvent el (js/CustomEvent. event-name
                       #js {:detail detail :bubbles true})))

(defn- find-item [^js el id]
  (first (filter #(= id (:id %)) (:items @(.--state el)))))

(defn- add-item! [^js el text]
  (let [text (str/trim (str text))]
    (when-not (str/blank? text)
      (let [!state (.--state el)
            item {:id (:next-id @!state) :text text :done false}]
        (swap! !state #(-> %
                           (update :items conj item)
                           (update :next-id inc)))
        (emit! el "item-added" item)))))

(defn- remove-item! [^js el id]
  (when-let [item (find-item el id)]
    (swap! (.--state el) update :items #(vec (remove (fn [i] (= id (:id i))) %)))
    (emit! el "item-removed" item)))

(defn- toggle-item! [^js el id]
  (when (find-item el id)
    (swap! (.--state el) update :items
           #(mapv (fn [item]
                    (if (= id (:id item)) (update item :done not) item))
                  %))
    (emit! el "item-changed" (find-item el id))))

(defn- submit! [^js el e]
  (.preventDefault e)
  (add-item! el (:draft @(.--state el)))
  (swap! (.--state el) assoc :draft ""))

(defn- render-item [^js el {:keys [id text done]}]
  [:li {:key id}
   [:button.toggle {:on-click (fn [_] (toggle-item! el id))
                    :aria-pressed (str done)
                    :aria-label (str (if done "Mark not done: " "Mark done: ") text)}
    (when done "✓")]
   [:span.text {:class (when done "done")} text]
   [:button.remove {:on-click (fn [_] (remove-item! el id))
                    :aria-label (str "Delete " text)}
    "🗑"]])

(defn- render! [^js el]
  (let [{:keys [items draft label]} @(.--state el)]
    (r/render (.--shadow el)
      [:div
       [:style css]
       [:h3 label]
       (if (empty? items)
         [:p.empty "Nothing to do."]
         (into [:ul] (map #(render-item el %) items)))
       [:form {:on-submit (fn [e] (submit! el e))}
        [:input {:type "text" :value draft
                 :on-input (fn [e] (swap! (.--state el) assoc :draft
                                          (.. e -target -value)))
                 :aria-label "New item"}]
        [:button {:type "submit"} "Add"]]])))

(defn- sync-attributes!
  ;; read through the getter, so the default lives in one place
  [^js el]
  (swap! (.--state el) assoc :label (.-label el)))

(defclass TodoList
  (extends js/HTMLElement)
  (^:static field observedAttributes #js ["label"])
  (field -shadow nil)
  (field -state (atom {:items [] :next-id 0 :draft ""}))

  (constructor [this]
    (super)
    (set! -shadow (.attachShadow this #js {:mode "open"}))
    (add-watch -state ::render (fn [_ _ _ _] (render! this))))

  Object
  ;; a primitive travels as an attribute, with a property that mirrors it
  (^:get label [this] (or (.getAttribute this "label") "To do"))
  (^:set label [this v] (.setAttribute this "label" (str v)))

  ;; a squint map is a JS object and a vector is an array, so a caller reading
  ;; item.text or item.done needs no conversion
  (^:get items [this] (:items @-state))

  (addItem [this text] (add-item! this text))

  (connectedCallback [this]
    (sync-attributes! this))

  (attributeChangedCallback [this _name _old _new]
    (sync-attributes! this)))

;; defining twice throws, which happens when a page loads this module more than once
(when-not (.get js/customElements "todo-list")
  (.define js/customElements "todo-list" TodoList))
