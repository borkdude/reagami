(ns server
  (:require
   [app]
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [org.httpkit.server :as http]
   [reagami.ssr :as ssr]))

(def state {:count 0})

(defn page []
  (str "<!doctype html>"
       (ssr/render
        [:html {:lang "en"}
         [:head [:meta {:charset "utf-8"}] [:title "reagami ssr"]]
         [:body
          [:div#app [app/app state]]
          ;; the browser needs the same state to render the same hiccup
          [:script {:type "application/json" :id "state"
                    :innerHTML (json/generate-string state)}]
          [:script {:type "module" :src "/client.js"}]]])))

(defn handler [req]
  (case (:uri req)
    "/" {:status 200 :headers {"Content-Type" "text/html"} :body (page)}
    "/client.js" {:status 200
                  :headers {"Content-Type" "text/javascript"}
                  :body (slurp (io/file "dist/client.js"))}
    {:status 404 :body "not found"}))

(defn -main [& _]
  (http/run-server handler {:port 8080})
  (println "http://localhost:8080")
  @(promise))
