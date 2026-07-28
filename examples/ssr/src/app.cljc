(ns app)

(def row-height 24)
(def viewport 480)
(def overscan 30)

;; browsers cap element height. chrome and safari allow about 33.5M px, firefox
;; materially less, so the canvas is capped and the scroll position is scaled
;; through it. below the cap the scale is 1 and this all reduces to scrollTop.
(def max-canvas 15000000)

(defn canvas-height [total]
  (min (* total row-height) max-canvas))

(defn scale
  "Virtual pixels per canvas pixel. 1 until the table outgrows the cap."
  [total]
  (/ (* total row-height) (canvas-height total)))

;; browser state. the server renders from the state it is handed and never
;; touches this, so its handlers are inert there.
(defonce !state (atom nil))

;; client-only view state. :want is the row range the viewport is over, set by
;; the scroll handler. the default has to be a real value, because the server
;; render reads it too, and it has no viewport.
(defonce !view (atom {:want nil :editing nil}))

;; the client installs a transport here. point it at the server to make state
;; changes server-authoritative, or at handle below to keep them local.
(defonce !dispatch (atom nil))

(def columns ["name" "owner" "region" "qty" "price" "score" "tag" "updated" "status"])

(defn row
  "The whole table is a function of the index, so a million rows never exist
  anywhere at once. Only the requested window is ever built."
  [i]
  {:id i
   :name (str "row " i)
   :owner (nth ["ada" "grace" "alan" "edsger" "barbara"] (mod i 5))
   :region (nth ["emea" "apac" "amer"] (mod i 3))
   :qty (inc (mod i 7))
   :price (+ 100 (* 7 (mod i 143)))
   :score (mod (* i 37) 100)
   :tag (nth ["alpha" "beta" "gamma" "delta"] (mod i 4))
   :updated (str "2026-" (inc (mod i 12)) "-" (inc (mod i 28)))
   :status (nth ["new" "active" "done"] (mod i 3))})

(defn- window-rows
  "Rows for [from to), with any edits laid over the generated values."
  [state from to]
  (mapv (fn [i] (merge (row i) (get (:edits state) i)))
        (range from to)))

(defn handle
  "Applies an action to state. Portable, so it can run on either side."
  [state action]
  (case (:type action)
    "window" (let [from (max 0 (:from action))
                   to (min (:total state) (:to action))]
               (assoc state :from from :rows (window-rows state from to)))
    ;; the page is state like everything else, so a direct hit renders it on the
    ;; server and opening it in the browser is just another push
    "open" (assoc state :page "row" :row (merge (row (:id action))
                                                (get (:edits state) (:id action))))
    "close" (dissoc (assoc state :page "table") :row)

    ;; patch the rows in hand rather than rebuilding them from row. the client
    ;; runs this optimistically and only knows the edits made since it loaded,
    ;; so regenerating would drop every earlier one back to its generated value
    ;; until the server's push landed.
    "edit" (let [id (:id action)
                 k (keyword (:field action))
                 v (:value action)]
             (cond-> state
               true (assoc-in [:edits id k] v)
               true (update :rows (fn [rows]
                                    (mapv (fn [r] (if (= id (:id r)) (assoc r k v) r))
                                          rows)))
               (= id (:id (:row state))) (assoc-in [:row k] v)))
    state))

(defn dispatch! [action]
  (when-let [f @!dispatch]
    (f action)))

(defn- px [n] (str n "px"))

;; the slider hands back a string on both platforms
(defn- parse-ms [s]
  (parse-long (str s)))

(defn- commit! [id c e]
  ;; enter commits and re-renders, which removes the focused input, which fires
  ;; blur, which would commit again in the middle of the first render. only the
  ;; cell still marked as editing may commit.
  (when (= [id c] (:editing @!view))
    (let [action {:type "edit" :id id :field c :value (.. e -target -value)}]
      ;; optimistic: run the same reducer locally so the cell updates now, then
      ;; send it. the server's push is authoritative and overwrites this.
      ;; state first: clearing :editing renders the cell as text, and doing that
      ;; before the new value lands draws the old one for a frame.
      (swap! !state handle action)
      (swap! !view assoc :editing nil)
      (dispatch! action))))

(defn- cell [m]
  (let [r (:row m)
        c (:col m)
        id (:id r)
        v (get r (keyword c))]
    (if (= [id c] (:editing @!view))
      ;; default-value, not value: a state push mid-edit must not overwrite what
      ;; is being typed. on-render focuses the input when it is created.
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
  "Visible row indexes the client does not hold. Scrolling faster than the
  server answers leaves a run of these, and each one draws a placeholder rather
  than blank canvas."
  [want from loaded-to]
  (if want
    (filter (fn [i] (or (< i from) (>= i loaded-to)))
            (range (nth want 0) (nth want 1)))
    []))

;; runs on babashka for the server render and on squint in the browser. state is
;; a Clojure map on the server and a parsed JSON object in the browser, so read
;; it with keywords only.
(defn- table [state]
  (let [total (:total state)
        from (:from state)
        rows (:rows state)
        loaded-to (+ from (count rows))
        view @!view
        gaps (missing (:want view) from loaded-to)
        ;; where the first visible row sits on the canvas, and which row that is.
        ;; the server has no viewport, so both are 0 and rows land at index * h
        base (or (:base view) 0)
        anchor (or (:anchor view) 0)
        top-of (fn [i] (px (+ base (* (- i anchor) row-height))))]
    [:div.app
     [:h1 "reagami ssr"]
     [:p (str (count rows) " of " total " rows are in the client. Scroll and the "
              "server sends the window you are looking at.")]
     [:p.latency
      "server delay "
      ;; default-value, so dragging is not fought by the pushes it causes.
      ;; on-input moves the label at once, on-change sends one action per drag.
      [:input#latency {:type "range" :min 0 :max 100
                       :default-value (str (:latency state))
                       :on-input (fn [e]
                                   (swap! !state assoc :latency
                                          (parse-ms (.. e -target -value))))
                       :on-change (fn [e]
                                    (dispatch! {:type "latency"
                                                :ms (parse-ms (.. e -target -value))}))}]
      [:span#latency-value (str " " (:latency state) " ms")]]
     [:div#scroller {:style {:height (px viewport) :overflow-y "auto"}}
      ;; sticky, so it stays in view however far down the canvas you are
      (when (seq gaps)
        [:div.overlay [:div.spinner]])
      [:div#canvas {:style {:height (px (canvas-height total)) :position "relative"}}
       (for [i (range (count rows))]
         (let [r (nth rows i)]
           [:div.row {:key (:id r)
                      :class (:status r)
                      :style {:position "absolute"
                              :top (top-of (+ from i))
                              :height (px row-height)}}
            [:a.open {:href (str "/row/" (:id r))
                      :on-click (fn [e]
                                  (.preventDefault e)
                                  (dispatch! {:type "open" :id (:id r)}))}
             "open"]
            (for [c columns]
              [cell {:row r :col c}])]))
       (for [i gaps]
         [:div.ghost {:key (str "gap" i)
                      :style {:position "absolute"
                              :top (top-of i)
                              :height (px row-height)}}
          [:span.cell.cell-name (str "row " i)]
          [:span.shimmer]])]]]))

(defn- detail [state]
  (let [r (:row state)]
    [:div.app
     [:h1 "reagami ssr"]
     [:p [:a#back {:href "/"
                   :on-click (fn [e]
                               (.preventDefault e)
                               (dispatch! {:type "close"}))}
          "back to the table"]]
     [:h2#detail-name (:name r)]
     [:div#detail
      (for [c columns]
        [:div.field {:key c}
         [:span.label c]
         [cell {:row r :col c}]])]]))

(defn app [state]
  (if (= "row" (:page state))
    [detail state]
    [table state]))
