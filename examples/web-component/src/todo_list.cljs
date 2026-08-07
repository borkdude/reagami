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
  .text { flex: 1; cursor: text; }
  .text.done { text-decoration: line-through; color: #999; }
  .edit { flex: 1; padding: 0.2rem; font: inherit; }
  .empty { color: #888; margin: 0 0 0.5rem; }
  form { display: flex; gap: 0.5rem; }
  input { padding: 0.3rem; font: inherit; }
  .new { flex: 1; }
  button { cursor: pointer; font: inherit; }
  .toggle { width: 1.4rem; height: 1.4rem; padding: 0; border: 1px solid #bbb;
            border-radius: 50%; background: #fff; color: #2a2; line-height: 1; }
  .toggle[aria-pressed=\"true\"] { border-color: #2a2; }
  .remove { border: none; background: none; }
")

;; a row. every method on the class is public JS API, so the render helpers stay
;; out here and take the element to call back into.
(defn- emit!
  ;; composed lets the event leave the shadow root, bubbles lets a parent listen
  ;; for every item at once
  [^js el event-name detail]
  (.dispatchEvent el (js/CustomEvent. event-name
                       #js {:detail detail :bubbles true :composed true})))

(defn- find-item [^js el id]
  (first (filter #(= id (:id %)) (:items @(.--state el)))))

(defn- update-item! [^js el id f]
  (swap! (.--state el) update :items
         #(mapv (fn [item] (if (= id (:id item)) (f item) item)) %)))

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
    (update-item! el id #(update % :done not))
    (emit! el "item-changed" (find-item el id))))

(defn- edit-item! [^js el id]
  (swap! (.--state el) assoc :editing id))

(defn- cancel-edit! [^js el]
  (swap! (.--state el) assoc :editing nil))

(defn- commit-edit! [^js el id text]
  (let [text (str/trim (str text))
        item (find-item el id)]
    (swap! (.--state el) assoc :editing nil)
    (cond
      (str/blank? text) (remove-item! el id)
      (not= text (:text item)) (do (update-item! el id #(assoc % :text text))
                                   (emit! el "item-changed" (find-item el id))))))

(defn- submit! [^js el e]
  (.preventDefault e)
  (add-item! el (:draft @(.--state el)))
  (swap! (.--state el) assoc :draft ""))

(defn- render-item [^js el item editing]
  (let [{:keys [id text done]} item]
    [:li {:key id}
     [:button.toggle {:on-click (fn [_] (toggle-item! el id))
                      :aria-pressed (str done)
                      :aria-label (str (if done "Mark not done: " "Mark done: ") text)}
      (when done "✓")]
     (if (= id editing)
       [:input.edit {:value text
                     :aria-label (str "Edit " text)
                     ;; reagami calls this once the input is in the document
                     :on-render (fn [{:keys [node lifecycle]}]
                                  (when (= :mount lifecycle) (.select node)))
                     :on-blur (fn [e] (commit-edit! el id (.. e -target -value)))
                     :on-key-down (fn [e]
                                    (case (.-key e)
                                      "Enter" (commit-edit! el id (.. e -target -value))
                                      "Escape" (cancel-edit! el)
                                      nil))}]
       [:span.text {:class (when done "done")
                    :on-click (fn [_] (edit-item! el id))}
        text])
     [:button.remove {:on-click (fn [_] (remove-item! el id))
                      :aria-label (str "Delete " text)}
      "🗑"]]))

(defn- render! [^js el]
  (let [{:keys [items draft label placeholder editing]} @(.--state el)]
    (r/render (.--shadow el)
      [:div
       [:style css]
       [:h3 label]
       (if (empty? items)
         [:p.empty "Nothing to do."]
         (into [:ul] (map #(render-item el % editing) items)))
       [:form {:on-submit (fn [e] (submit! el e))}
        [:input.new {:type "text" :value draft
                     :on-input (fn [e] (swap! (.--state el) assoc :draft
                                              (.. e -target -value)))
                     :placeholder placeholder
                     :aria-label placeholder}]
        [:button {:type "submit"} "Add"]]])))

(defn- upgrade-property!
  ;; a property set before this element upgraded sits on the instance and hides
  ;; the accessor. take the value, drop the instance property, and set it again
  ;; so the setter runs.
  [^js el prop]
  (when (.hasOwnProperty el prop)
    (let [v (aget el prop)]
      (js-delete el prop)
      (aset el prop v))))

(defn- sync-attributes!
  ;; read through the getters, so the defaults live in one place
  [^js el]
  (swap! (.--state el) assoc
         :label (.-label el)
         :placeholder (.-placeholder el)))

(defclass TodoList
  (extends js/HTMLElement)
  (^:static field observedAttributes #js ["label" "placeholder"])
  (field -shadow nil)
  (field -state (atom {:items [] :next-id 0 :draft "" :editing nil}))

  (constructor [this]
    (super)
    (set! -shadow (.attachShadow this #js {:mode "open"}))
    (add-watch -state ::render (fn [_ _ _ _] (render! this))))

  Object
  ;; primitives travel as attributes, with a property that mirrors them
  (^:get label [this] (or (.getAttribute this "label") "To do"))
  (^:set label [this v] (.setAttribute this "label" (str v)))

  (^:get placeholder [this] (or (.getAttribute this "placeholder") "New item"))
  (^:set placeholder [this v] (.setAttribute this "placeholder" (str v)))

  ;; a squint map is a JS object and a vector is an array, so a caller reading
  ;; item.text or item.done needs no conversion
  (^:get items [this] (:items @-state))

  (addItem [this text] (add-item! this text))

  (connectedCallback [this]
    (upgrade-property! this "label")
    (upgrade-property! this "placeholder")
    (sync-attributes! this))

  (attributeChangedCallback [this _name _old _new]
    (sync-attributes! this)))

;; defining twice throws, which happens when a page loads this module more than once
(when-not (.get js/customElements "todo-list")
  (.define js/customElements "todo-list" TodoList))
