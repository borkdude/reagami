(ns debug)

(defn- kb [n] (str (js/Math.round (/ n 100)) " "))

(defn- entry [type]
  (aget (js/performance.getEntriesByType type) 0))

(defn first-load
  "What the browser recorded for the document itself. transferSize is what came
  over the wire, so it shows what brotli did to the server-rendered page."
  []
  (let [nav (entry "navigation")
        paint (aget (js/performance.getEntriesByName "first-contentful-paint") 0)]
    (when nav
      {:wire (.-transferSize nav)
       :decoded (.-decodedBodySize nav)
       :response (js/Math.round (.-responseEnd nav))
       :fcp (when paint (js/Math.round (.-startTime paint)))})))

(defn stats [info]
  (let [wire (:wire info)
        load (:load info)]
    [:div#debugbox
     [:pre#load
      (if load
        (str "first load | document " (:wire load) " B over the wire, "
             (:decoded load) " B decoded"
             " | response at " (:response load) " ms"
             (if (:fcp load) (str " | painted at " (:fcp load) " ms") "")
             " | interactive at " (:hydrated info) " ms")
        "first load | not measured here")]
     [:pre#stats
      (str "rows " (:from info) ".." (+ (:from info) (:rows info))
           " of " (:total info)
           " | last push " (:bytes info) " chars"
           (if wire
             (str ", " (:br wire) " B on the wire ("
                  (js/Math.round (/ (:raw wire) (max 1 (:br wire)))) "x smaller)")
             ", uncompressed")
           " | parse " (if (:parse info) (str (:parse info) " us") "-")
           " | created " (:created info)
           " | render " (:ms info) " ms")]]))
