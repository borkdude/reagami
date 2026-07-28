(ns app)

(def row-height 24)
(def viewport 480)
(def overscan 10)

;; browser state. the server renders from the state it is handed and never
;; touches this, so its handlers are inert there.
(defonce !state (atom nil))

;; client-only view state. :want is the row range the viewport is over, set by
;; the scroll handler. the default has to be a real value, because the server
;; render reads it too, and it has no viewport.
(defonce !view (atom {:want nil}))

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

(defn handle
  "Applies an action to state. Portable, so it can run on either side."
  [state action]
  (case (:type action)
    "window" (let [from (max 0 (:from action))
                   to (min (:total state) (:to action))]
               (assoc state :from from :rows (mapv row (range from to))))
    state))

(defn dispatch! [action]
  (when-let [f @!dispatch]
    (f action)))

(defn- px [n] (str n "px"))

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
(defn app [state]
  (let [total (:total state)
        from (:from state)
        rows (:rows state)
        loaded-to (+ from (count rows))
        gaps (missing (:want @!view) from loaded-to)]
    [:div.app
     [:h1 "reagami ssr"]
     [:p (str (count rows) " of " total " rows are in the client. Scroll and the "
              "server sends the window you are looking at.")]
     [:div#scroller {:style {:height (px viewport) :overflow-y "auto"}}
      [:div#canvas {:style {:height (px (* total row-height)) :position "relative"}}
       (for [i (range (count rows))]
         (let [r (nth rows i)]
           [:div.row {:key (:id r)
                      :class (:status r)
                      :style {:position "absolute"
                              :top (px (* (+ from i) row-height))
                              :height (px row-height)}}
            (for [c columns]
              [:span {:key c :class (str "cell cell-" c)} (get r (keyword c))])]))
       (for [i gaps]
         [:div.spinner {:key (str "gap" i)
                        :style {:position "absolute"
                                :top (px (* i row-height))
                                :height (px row-height)}}
          [:span.cell.cell-name (str "row " i)]
          [:span.shimmer]])]]]))
