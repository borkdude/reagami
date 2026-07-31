(ns app)

(def row-height 24)
(def viewport 480)
(def overscan 30)

;; browsers cap element height, Firefox lower than Chrome. past the cap the
;; scroll position is scaled through it. below the cap the scale is 1.
(def max-canvas 15000000)

(defn canvas-height [total]
  (min (* total row-height) max-canvas))

(defn scale
  "Virtual pixels per canvas pixel. 1 until the table outgrows the cap."
  [total]
  (/ (* total row-height) (canvas-height total)))

;; browser state. the server renders from the state it is handed and never
;; reads this atom.
(defonce !state (atom nil))

;; client-only view state. :want is the row range on screen.
(defonce !view (atom {:want nil :editing nil}))

;; the client installs a transport here. point it at the server to make state
;; changes server-authoritative, or at handle below to keep them local.
(defonce !dispatch (atom nil))

(def columns ["name" "qty" "price" "status"])

(defn row
  "The whole table is a function of the index, so a million rows never exist
  anywhere at once. Only the requested window is built."
  [i]
  {:id i
   :name (str "row " i)
   :qty (inc (mod i 7))
   :price (+ 100 (* 7 (mod i 143)))
   :status (nth ["new" "active" "done"] (mod i 3))})

(defn- window-rows
  "Rows for [from to), with the edits laid over the generated values."
  [state from to]
  (mapv (fn [i] (merge (row i) (get (:edits state) i)))
        (range from to)))

(defn handle
  "Applies an action to state. Portable, so it runs on either side."
  [state action]
  (case (:type action)
    "window" (let [from (max 0 (:from action))
                   to (min (:total state) (:to action))]
               (assoc state :from from :rows (window-rows state from to)))
    ;; the edit goes into the shared edits, then the window regenerates with
    ;; that edit laid over it
    "edit" (-> state
               (assoc-in [:edits (:id action) (keyword (:field action))]
                         (:value action))
               (handle {:type "window"
                        :from (:from state)
                        :to (+ (:from state) (count (:rows state)))}))
    state))

(defn dispatch! [action]
  (when-let [f @!dispatch]
    (f action)))

(defn- px [n] (str n "px"))

(defn- commit! [id c e]
  ;; only the cell marked as editing can commit, so the blur after Enter does
  ;; not commit twice
  (when (= [id c] (:editing @!view))
    (swap! !view assoc :editing nil)
    (dispatch! {:type "edit" :id id :field c :value (.. e -target -value)})))

(defn- cell [m]
  (let [r (:row m)
        c (:col m)
        id (:id r)
        v (get r (keyword c))]
    (if (= [id c] (:editing @!view))
      ;; default-value, so a push during the edit does not overwrite the typing
      [:input {:key c
               :class (str "cell cell-" c)
               :default-value (str v)
               :on-render (fn [node phase _] (when (= :mount phase) (.focus node)) nil)
               :on-key-down (fn [e] (when (= "Enter" (.-key e)) (commit! id c e)))
               :on-blur (fn [e] (commit! id c e))}]
      [:span {:key c
              :class (str "cell cell-" c)
              :on-click (fn [_] (swap! !view assoc :editing [id c]))}
       v])))

(defn- missing
  "Visible row indexes that the client does not hold. A scroll that outruns the
  server leaves a run of these, and each one draws a placeholder."
  [want from loaded-to]
  (if want
    (filter (fn [i] (or (< i from) (>= i loaded-to)))
            (range (nth want 0) (nth want 1)))
    []))

;; runs on Babashka for the server render and on Squint in the browser. state
;; is a Clojure map on the server and a parsed JSON object in the browser, so
;; read it with keywords only.
(defn app [state]
  (let [total (:total state)
        from (:from state)
        rows (:rows state)
        view @!view
        gaps (missing (:want view) from (+ from (count rows)))
        ;; where the first visible row sits, and which row that is. the server
        ;; has no viewport, so both are 0 and rows land at index * row-height
        base (or (:base view) 0)
        anchor (or (:anchor view) 0)
        top-of (fn [i] (px (+ base (* (- i anchor) row-height))))]
    [:div.app
     [:h1 "reagami ssr"]
     [:p (str (count rows) " of " total " rows are in the client. Scroll, and "
              "the server sends the window on your screen. Click a cell to "
              "edit it.")]
     [:div#scroller {:style {:height (px viewport) :overflow-y "auto"}}
      [:div#canvas {:style {:height (px (canvas-height total)) :position "relative"}}
       (for [i (range (count rows))]
         (let [r (nth rows i)]
           [:div.row {:key (:id r)
                      :class (:status r)
                      :style {:position "absolute"
                              :top (top-of (+ from i))
                              :height (px row-height)}}
            (for [c columns]
              [cell {:row r :col c}])]))
       (for [i gaps]
         [:div.ghost {:key (str "gap" i)
                      :style {:position "absolute"
                              :top (top-of i)
                              :height (px row-height)}}
          [:span.cell.cell-name (str "row " i)]
          [:span.shimmer]])]]]))
