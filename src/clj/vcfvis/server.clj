(ns vcfvis.server
  (:use [compojure.core]
        [vcfvis.login :only [bio-remote-workflow bio-credential-fn]]
        [ring.adapter.jetty :only [run-jetty]]
        [ring.middleware file-info session anti-forgery
         keyword-params multipart-params params]
        [ring.util.response :only [redirect response content-type]]
        [bcbio.variation.api.shared :only [web-config]])
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [cemerick.friend :as friend]
            [cemerick.shoreleave.rpc :as rpc]
            [vcfvis.api :as api]
            [vcfvis.dataset :as dataset]
            [vcfvis.pages :as pages]
            [vcfvis.xprize :as xprize]))

(defroutes main-routes
  (GET "/login" req (pages/add-std-info "public/login.html"))
  (friend/logout (ANY "/logout" req (redirect "/")))

  (GET "/" req
       (if (get-in @web-config [:params :web :xprize])
         (redirect "/xprize")
         (redirect "/viz")))
  (GET "/viz" req
       (friend/authorize #{:user} (pages/add-std-info "public/viz.html")))

  (context "/api" req (friend/wrap-authorize api/api-routes #{:user}))
  (context "/xprize" req (friend/wrap-authorize xprize/xprize-routes #{:user}))

  (GET "/dataset/:dsid" [dsid :as request]
       (dataset/retrieve dsid request))
  (GET "/dataset/:runid/:name" [runid name :as {session :session}]
       (-> (response (dataset/get-variant-file runid name (api/get-username)
                                               (get (:work-info session) runid)))
           (content-type "text/plain")))

  (route/files "/" {:root "public" :allow-symlinks? true})
  (route/not-found "Not found"))

(def app
  (-> main-routes
      wrap-file-info
      wrap-anti-forgery
      rpc/wrap-rpc
      (friend/authenticate {:credential-fn bio-credential-fn
                            :workflows [(bio-remote-workflow)]})
      wrap-session
      wrap-keyword-params
      wrap-params
      wrap-multipart-params
      (handler/site {:session {:cookie-attrs {:max-age 3600}}})))

(defn start!
  ([] (start! 8080))
  ([port]
     (defonce server
       (run-jetty #'app {:port port :join? false}))))
