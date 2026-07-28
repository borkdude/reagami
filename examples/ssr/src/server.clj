(ns server
  (:require
   [app]
   [cheshire.core :as json]
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
          ;; vite injects these into its own index.html. this page comes from
          ;; here, so it emits them: vite's client, the squint plugin's REPL
          ;; listener, and the compiled entry namespace.
          [:script {:type "module" :src "http://localhost:5173/@vite/client"}]
          [:script {:type "module" :src "http://localhost:5173/@id/__x00__virtual:squint-repl-client"}]
          [:script {:type "module" :src "http://localhost:5173/out/client.mjs"}]]])))

(defn handler [req]
  (case (:uri req)
    "/" {:status 200 :headers {"Content-Type" "text/html"} :body (page)}
    {:status 404 :body "not found"}))

(defn -main [& _]
  (http/run-server handler {:port 8080})
  (println "http://localhost:8080")
  @(promise))
