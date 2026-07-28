(ns debug)

;; client only, and rendered into its own root, so it never takes part in the
;; hydration of the app tree or shows up in its counts
(defn stats [info]
  (let [wire (:wire info)]
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
          " | render " (:ms info) " ms")]))
