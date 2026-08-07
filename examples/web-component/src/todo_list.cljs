(ns todo-list
  (:require
   [clojure.string :as str]
   [reagami.core :as r]
   [squint.core :refer [defclass]]))

;; A <todo-list> element. Reagami renders its shadow root. Nothing outside this
;; file knows that, and nothing in here knows who uses the element.
;;
;; State lives in one atom per element. `handle` is a pure function of the state
;; and an event, `view` is a pure function of the state, and `dispatch!` finds
;; the element from the DOM event. The class holds the public API and no logic.

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

(def ^:private initial-state
  {:items [] :next-id 0 :draft "" :editing nil :edit-draft ""
   :label nil :placeholder nil})

(defn- find-item [items id]
  (first (filter #(= id (:id %)) items)))

(defn- change-item [state id f]
  (update state :items (fn [items] (mapv #(if (= id (:id %)) (f %) %) items))))

(defn- add-item [state text]
  (let [text (str/trim (str text))]
    (if (str/blank? text)
      [state nil]
      (let [item {:id (:next-id state) :text text :done false}]
        [(-> state (update :items conj item) (update :next-id inc))
         ["item-added" item]]))))

(defn- handle
  "The state and an event in. Returns the next state, and the event the element
  should tell the world about when there is one."
  [state [op a]]
  (case op
    :attributes [(assoc state :label (first a) :placeholder (second a)) nil]
    :draft [(assoc state :draft a) nil]
    :edit-draft [(assoc state :edit-draft a) nil]
    :edit [(assoc state :editing a :edit-draft (:text (find-item (:items state) a))) nil]
    :cancel-edit [(assoc state :editing nil) nil]
    :add (add-item state a)
    :add-draft (let [[state event] (add-item state (:draft state))]
                 [(assoc state :draft "") event])
    :toggle (let [state (change-item state a #(update % :done not))]
              [state ["item-changed" (find-item (:items state) a)]])
    :remove (if-let [item (find-item (:items state) a)]
              [(update state :items (fn [items] (vec (remove #(= a (:id %)) items))))
               ["item-removed" item]]
              [state nil])
    :commit-edit (let [text (str/trim (:edit-draft state))
                       state (assoc state :editing nil)]
                   (cond
                     (str/blank? text) (handle state [:remove a])
                     (= text (:text (find-item (:items state) a))) [state nil]
                     :else (let [state (change-item state a #(assoc % :text text))]
                             [state ["item-changed" (find-item (:items state) a)]])))
    [state nil]))

(defn- emit!
  ;; composed lets the event leave the shadow root, bubbles lets a parent listen
  ;; for every item at once
  [^js el event-name detail]
  (.dispatchEvent el (js/CustomEvent. event-name
                       #js {:detail detail :bubbles true :composed true})))

(defn- dispatch!
  "Apply an event to an element's state and emit whatever follows from it."
  [^js el event]
  (when-let [!state (.--state el)]
    (let [[state [event-name detail]] (handle @!state event)]
      (reset! !state state)
      (when event-name (emit! el event-name detail)))))

(defn- on
  ;; the handler finds its element from the event, so a view needs no element
  ;; and no dispatch of its own
  [event]
  (fn [e] (dispatch! (.-host (.getRootNode (.-currentTarget e))) event)))

(defn- on-value [event]
  (fn [e] (dispatch! (.-host (.getRootNode (.-currentTarget e)))
                     (conj event (.. e -target -value)))))

(defn- render-item [item editing edit-draft]
  (let [{:keys [id text done]} item]
    [:li {:key id}
     [:button.toggle {:on-click (on [:toggle id])
                      :aria-pressed (str done)
                      :aria-label (str (if done "Mark not done: " "Mark done: ") text)}
      (when done "✓")]
     (if (= id editing)
       [:input.edit {:value edit-draft
                     :aria-label (str "Edit " text)
                     ;; reagami calls this once the input is in the document
                     :on-render (fn [{:keys [node lifecycle]}]
                                  (when (= :mount lifecycle) (.select node)))
                     :on-input (on-value [:edit-draft])
                     :on-blur (on [:commit-edit id])
                     :on-key-down (fn [e]
                                    (case (.-key e)
                                      "Enter" ((on [:commit-edit id]) e)
                                      "Escape" ((on [:cancel-edit]) e)
                                      nil))}]
       [:span.text {:class (when done "done")
                    :on-click (on [:edit id])}
        text])
     [:button.remove {:on-click (on [:remove id])
                      :aria-label (str "Delete " text)}
      "🗑"]]))

(defn- view [state]
  (let [{:keys [items draft label placeholder editing edit-draft]} state]
    [:div
     [:style css]
     [:h3 label]
     (if (empty? items)
       [:p.empty "Nothing to do."]
       (into [:ul] (map #(render-item % editing edit-draft) items)))
     [:form {:on-submit (fn [e]
                          (.preventDefault e)
                          ((on [:add-draft]) e))}
      [:input.new {:type "text" :value draft
                   :on-input (on-value [:draft])
                   :placeholder placeholder
                   :aria-label placeholder}]
      [:button {:type "submit"} "Add"]]]))

(defn- upgrade-property!
  ;; a property set before this element upgraded sits on the instance and hides
  ;; the accessor. take the value, drop the instance property, and set it again
  ;; so the setter runs.
  [^js el prop]
  (when (.hasOwnProperty el prop)
    (let [v (aget el prop)]
      (js-delete el prop)
      (aset el prop v))))

(defclass TodoList
  (extends js/HTMLElement)
  (^:static field observedAttributes #js ["label" "placeholder"])
  (field -shadow nil)
  (field -state (atom initial-state))

  (constructor [this]
    (super)
    (set! -shadow (.attachShadow this #js {:mode "open"}))
    (add-watch -state ::render
               (fn [_ _ _ state] (r/render -shadow (view state)))))

  Object
  ;; primitives travel as attributes, with a property that mirrors them
  (^:get label [this] (or (.getAttribute this "label") "To do"))
  (^:set label [this v] (.setAttribute this "label" (str v)))

  (^:get placeholder [this] (or (.getAttribute this "placeholder") "New item"))
  (^:set placeholder [this v] (.setAttribute this "placeholder" (str v)))

  ;; a squint map is a JS object and a vector is an array, so a caller reading
  ;; item.text or item.done needs no conversion
  (^:get items [this] (:items @-state))

  (addItem [this text] (dispatch! this [:add text]))

  (connectedCallback [this]
    (upgrade-property! this "label")
    (upgrade-property! this "placeholder")
    ;; read through the getters, so the defaults live in one place
    (dispatch! this [:attributes [(.-label this) (.-placeholder this)]]))

  (attributeChangedCallback [this _name _old _new]
    (dispatch! this [:attributes [(.-label this) (.-placeholder this)]])))

;; defining twice throws, which happens when a page loads this module more than once
(when-not (.get js/customElements "todo-list")
  (.define js/customElements "todo-list" TodoList))
