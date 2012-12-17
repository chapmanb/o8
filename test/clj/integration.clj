(ns test.integration
  (:use [o8.server :only [app]]
        [ring.mock.request :only [request body]]
        [ring.adapter.jetty :only [run-jetty]]
        midje.sweet)
  (:require [clj-http.client :as http]))

;;Tests against live server
(def test-port 9999)
(background (around :facts
                    (let [server (ring.adapter.jetty/run-jetty #'app {:port test-port :join? false})]
                      (binding [clj-http.core/*cookie-store* (clj-http.cookies/cookie-store)]
                        (try
                          ?form
                          (finally
                           (.stop server)))))))
(defn url
  [uri]
  (str "http://localhost:" test-port uri))

(defn login! []
  (http/post (url "/login")
             {:form-params {:username "keminglabs" :password "vcftest"}}))


(fact "New requests should redirect to login"
      (let [res (http/get (url "/") {:follow-redirects false})]
        (http/redirect? res) => true
        res => (contains {:headers (contains {"location" "/login"})})))


(facts
 (login!)
 (let [res (http/get (url "/api/files"))]
   (prn res)
   res => map?
   ))
