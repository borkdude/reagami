(ns debug)

(defn- entry [type]
  (aget (js/performance.getEntriesByType type) 0))

(defn- bytes-str [n]
  (if (< n 1024)
    (str n " B")
    (str (/ (js/Math.round (* 10 (/ n 1024))) 10) " kB")))

(defn- assets
  "Wire bytes for every subresource the page pulled. transferSize is already the
  compressed size. The event stream is left out, because it is counted from the
  proxy's own numbers instead."
  []
  (reduce (fn [n e]
            (if (.includes (.-name e) "/state/")
              n
              (+ n (or (.-transferSize e) 0))))
          0
          (js/performance.getEntriesByType "resource")))

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
        stream (or (:brTotal wire) (:chars info) 0)
        doc (or (:wire load) 0)
        asset (assets)]
    [:div#debugbox
     [:pre#wire
      (str "received " (bytes-str (+ doc asset stream)) " over the wire"
           " | document " (bytes-str doc)
           ", assets " (bytes-str asset)
           ", stream " (bytes-str stream)
           " over " (:pushes info) " pushes"
           (if wire
             (str " (" (js/Math.round (/ (:rawTotal wire) (max 1 (:brTotal wire)))) "x)")
             " uncompressed"))]
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
