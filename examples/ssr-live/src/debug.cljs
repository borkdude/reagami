(ns debug)

(defn- entry [type]
  (aget (js/performance.getEntriesByType type) 0))

(defn- bytes-str [n]
  (if (< n 1024)
    (str n " B")
    (str (/ (js/Math.round (* 10 (/ n 1024))) 10) " kB")))


(defn first-load
  "What the browser recorded for the document. transferSize is what came over
  the wire."
  []
  (let [nav (entry "navigation")
        paint (aget (js/performance.getEntriesByName "first-contentful-paint") 0)]
    (when nav
      {:wire (.-transferSize nav)
       :decoded (.-decodedBodySize nav)
       :response (js/Math.round (.-responseEnd nav))
       :fcp (when paint (js/Math.round (.-startTime paint)))})))

(defn- summarize
  "The rows array is nearly all of the state, so show its shape instead of its
  contents."
  [state]
  (let [o (js/Object.assign #js {} state)
        rows (:rows state)]
    (aset o "rows" (str (count rows) " rows, from " (:from state)))
    (when (:edits state) (aset o "edits" "server only"))
    o))

(defn state-view [state]
  [:div#stateview
   [:div.state-head "app state"]
   [:pre.state-json (js/JSON.stringify (summarize state) nil 2)]
   [:div.state-head "first row"]
   [:pre.state-json (js/JSON.stringify (first (:rows state)) nil 2)]])

(defn stats [info]
  (let [wire (:wire info)
        load (:load info)
        ;; without the proxy the stream is plain text, so its chars are its bytes
        stream (or (:brTotal wire) (:chars info) 0)]
    [:div#debugbox
     [:pre#wire
      (str "stream " (:pushes info) " pushes, " (bytes-str (:chars info))
           " of state, " (bytes-str stream) " on the wire"
           (if wire
             (str " (" (js/Math.round (/ (:rawTotal wire) (max 1 (:brTotal wire)))) "x smaller)")
             " (uncompressed)"))]
     [:div.note
      (str "Every push is the whole app state. Brotli only sends the "
           "difference with the pushes before it.")]
     [:pre#stats
      (str "rows " (:from info) ".." (+ (:from info) (:rows info))
           " of " (:total info)
           " | state " (bytes-str (:bytes info)) " uncompressed"
           " | parse " (if (:parse info) (str (:parse info) " us") "-")
           " | created " (:created info)
           " | render " (:ms info) " ms")]
     [:pre#load
      (if load
        (str "load: response " (:response load) " ms"
             (when (:fcp load) (str ", painted " (:fcp load) " ms"))
             ", interactive " (:hydrated info) " ms")
        "load: not measured here")]]))
