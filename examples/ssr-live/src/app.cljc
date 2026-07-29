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

;; browser state. the server renders from the state it is handed and never reads
;; this, so the handlers below are inert there.
(defonce !state (atom nil))

;; client-only view state. :want is the row range on screen. the default must be
;; a real value, because the server render reads it and has no viewport.
;; :overscan is the margin fetched around the viewport, so it sets how many rows
;; the client holds.
(defonce !view (atom {:want nil :editing nil :optimistic true :inflight 0
                      :overscan overscan}))

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
    ;; the page is part of state, so a direct hit renders on the server
    "open" (assoc state :page "row" :row (merge (row (:id action))
                                                (get (:edits state) (:id action))))
    "close" (dissoc (assoc state :page "table") :row)

    ;; patches the rows in hand: the client only knows the edits made since it
    ;; loaded, so regenerating them here would lose the rest.
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
  ;; only the cell still marked as editing may commit, so the blur that follows
  ;; Enter does not commit a second time.
  (when (= [id c] (:editing @!view))
    (let [action {:type "edit" :id id :field c :value (.. e -target -value)}
          optimistic? (:optimistic @!view)]
      ;; optimistic: apply locally so the cell updates now, then send. the
      ;; server's push is authoritative either way. state before :editing, or
      ;; the cell redraws the old value.
      (when optimistic?
        (swap! !state handle action))
      ;; one edit at a time: a second would be reverted by the push already on
      ;; its way. the cell carries the typed value, because without an
      ;; optimistic swap state still holds the old one. cleared on the push.
      (swap! !view assoc :editing nil
             :pending {:cell [id c] :value (:value action)})
      (dispatch! action))))

(defn- cell [m]
  (let [r (:row m)
        c (:col m)
        id (:id r)
        v (get r (keyword c))
        view @!view]
    (cond
      (= [id c] (:editing view))
      ;; default-value so a state push mid-edit does not overwrite what is typed
      [:input {:key c
               :class (str "cell cell-" c)
               :default-value (str v)
               :on-render (fn [node phase _] (when (= :mount phase) (.focus node)) nil)
               :on-key-down (fn [e] (when (= "Enter" (.-key e)) (commit! id c e)))
               :on-blur (fn [e] (commit! id c e))}]

      ;; this cell's commit is in flight, so it shows what was typed. only
      ;; marked unconfirmed when state has not been given the value already.
      (= [id c] (:cell (:pending view)))
      [:span {:key c
              :class (str "cell cell-" c (when-not (:optimistic view) " pending"))}
       (:value (:pending view))]

      :else
      [:span {:key c
              :class (str "cell cell-" c)
              ;; nothing is editable while a commit is in flight
              :on-click (fn [_]
                          (when-not (:pending view)
                            (swap! !view assoc :editing [id c])))}
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
        ;; where the first visible row sits, and which row that is. the server
        ;; has no viewport, so both are 0 and rows land at index * row-height
        base (or (:base view) 0)
        anchor (or (:anchor view) 0)
        top-of (fn [i] (px (+ base (* (- i anchor) row-height))))]
    [:div.app
     ;; one bar for every request in flight, rather than an indicator per
     ;; waiting thing. the client counts them, a server render never has any.
     (when (pos? (or (:inflight view) 0))
       [:div.progress])
     [:h1 "reagami ssr"]
     [:p (str (count rows) " of " total " rows are in the client. Scroll and the "
              "server sends the window you are looking at.")]
     [:p.latency
      "server delay "
      ;; on-input moves the label, on-change sends one action per drag
      [:input#latency {:type "range" :min 0 :max 100
                       :default-value (str (:latency state))
                       :on-input (fn [e]
                                   (swap! !state assoc :latency
                                          (parse-ms (.. e -target -value))))
                       :on-change (fn [e]
                                    (dispatch! {:type "latency"
                                                :ms (parse-ms (.. e -target -value))}))}]
      [:span#latency-value (str " " (:latency state) " ms")]
      [:span.cache-ctl
       "client cache "
       ;; a bigger margin means bigger pushes: the panel below shows what that
       ;; does to the wire, the parse and the render
       [:input#cache {:type "range" :min 30 :max 1000 :step 10
                      :default-value (str (:overscan view))
                      :on-change
                      (fn [e]
                        (let [o (parse-ms (.. e -target -value))
                              a (or (:anchor view) 0)]
                          (swap! !view assoc :overscan o)
                          (dispatch! {:type "window"
                                      :from (max 0 (- a o))
                                      :to (min total (+ a (quot viewport row-height) o))})))}]
       [:span#cache-value
        (str " ~" (+ (quot viewport row-height) (* 2 (:overscan view))) " rows")]]
      [:label.toggle
       [:input#optimistic
        {:type "checkbox"
         :checked (:optimistic view)
         :on-change (fn [e]
                      (swap! !view assoc :optimistic (.. e -target -checked)))}]
       "optimistic edits"]]
     [:div#scroller {:style {:height (px viewport) :overflow-y "auto"}}
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
  (let [r (:row state)
        view @!view]
    [:div.app
     (when (pos? (or (:inflight view) 0))
       [:div.progress])
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
