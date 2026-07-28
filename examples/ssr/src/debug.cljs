(ns debug)

;; client only, and rendered into its own root, so it never takes part in the
;; hydration of the app tree or shows up in its counts
(defn stats [info]
  [:pre#stats
   (str "rows " (:from info) ".." (+ (:from info) (:rows info))
        " of " (:total info)
        " | last push " (:bytes info) " B"
        " | created " (:created info)
        " | render " (:ms info) " ms")])
