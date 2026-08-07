(ns server
  (:require
   [org.httpkit.server :as http]
   [page]
   [reagami.ssr :as ssr]))

(defn html []
  (str "<!doctype html>"
       (ssr/render
        [:html {:lang "en"}
         [:head
          [:meta {:charset "utf-8"}]
          [:title "Reagami SSR and a web component"]
          [:style {:innerHTML "body { font-family: system-ui, sans-serif; margin: 2rem; max-width: 40rem; }"}]]
         [:body
          [:div#app [page/page]]
          [:p#hydration "waiting for the client"]
          [:p [:a {:href "/"} "back to the Vite pages"]]
          [:script {:type "module" :src "http://localhost:5174/@vite/client"}]
          [:script {:type "module" :src "http://localhost:5174/out/client.mjs"}]]])))

(defn handler [req]
  (if (= "/" (:uri req))
    {:status 200 :headers {"Content-Type" "text/html"} :body (html)}
    {:status 404 :body "not found"}))

(defn -main [& _]
  (http/run-server handler {:port 8081})
  (println "http://localhost:8081")
  @(promise))
