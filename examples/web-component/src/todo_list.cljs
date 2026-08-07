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
(defn- render-item [^js el item editing]
  (let [{:keys [id text done]} item]
    [:li {:key id}
     [:button.toggle {:on-click (fn [_] (.toggleItem el id))
                      :aria-pressed (str done)
                      :aria-label (str (if done "Mark not done: " "Mark done: ") text)}
      (when done "✓")]
     (if (= id editing)
       [:input.edit {:value text
                     :aria-label (str "Edit " text)
                     ;; reagami calls this once the input is in the document
                     :on-render (fn [{:keys [node lifecycle]}]
                                  (when (= :mount lifecycle) (.select node)))
                     :on-blur (fn [e] (.commitEdit el id (.. e -target -value)))
                     :on-key-down (fn [e]
                                    (case (.-key e)
                                      "Enter" (.commitEdit el id (.. e -target -value))
                                      "Escape" (.cancelEdit el)
                                      nil))}]
       [:span.text {:class (when done "done")
                    :on-click (fn [_] (.editItem el id))}
        text])
     [:button.remove {:on-click (fn [_] (.removeItem el id))
                      :aria-label (str "Delete " text)}
      "🗑"]]))

(defclass TodoList
  (extends js/HTMLElement)
  (^:static field observedAttributes #js ["label" "placeholder"])
  (field -shadow nil)
  (field -state (atom {:items [] :next-id 0 :draft "" :editing nil}))

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
  ;; item.text or item.done needs no conversion
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

  (findItem [this id]
    (first (filter #(= id (:id %)) (:items @-state))))

  (updateItem [this id f]
    (swap! -state update :items #(mapv (fn [item]
                                         (if (= id (:id item)) (f item) item))
                                       %)))

  (addItem [this text]
    (let [text (str/trim (str text))]
      (when-not (str/blank? text)
        (let [item {:id (:next-id @-state) :text text :done false}]
          (swap! -state #(-> %
                             (update :items conj item)
                             (update :next-id inc)))
          (.emit this "item-added" item)))))

  (removeItem [this id]
    (when-let [item (.findItem this id)]
      (swap! -state update :items #(vec (remove (fn [i] (= id (:id i))) %)))
      (.emit this "item-removed" item)))

  (toggleItem [this id]
    (when (.findItem this id)
      (.updateItem this id #(update % :done not))
      (.emit this "item-changed" (.findItem this id))))

  (editItem [this id]
    (swap! -state assoc :editing id))

  (cancelEdit [this]
    (swap! -state assoc :editing nil))

  (commitEdit [this id text]
    (let [text (str/trim (str text))
          item (.findItem this id)]
      (swap! -state assoc :editing nil)
      (cond
        (str/blank? text) (.removeItem this id)
        (not= text (:text item)) (do (.updateItem this id #(assoc % :text text))
                                     (.emit this "item-changed" (.findItem this id))))))

  (submit [this e]
    (.preventDefault e)
    (.addItem this (:draft @-state))
    (swap! -state assoc :draft ""))

  (render [this]
    (let [{:keys [items draft label placeholder editing]} @-state]
      (r/render -shadow
        [:div
         [:style css]
         [:h3 label]
         (if (empty? items)
           [:p.empty "Nothing to do."]
           (into [:ul] (map #(render-item this % editing) items)))
         [:form {:on-submit (fn [e] (.submit this e))}
          [:input.new {:type "text" :value draft
                       :on-input (fn [e] (swap! -state assoc :draft (.. e -target -value)))
                       :placeholder placeholder
                       :aria-label placeholder}]
          [:button {:type "submit"} "Add"]]]))))

;; defining twice throws, which happens when a page loads this module more than once
(when-not (.get js/customElements "todo-list")
  (.define js/customElements "todo-list" TodoList))
