(ns o8.login
  "Handle login credentials for multiple remote providers."
  (:use [bcbio.variation.api.file :only [get-client]])
  (:require [cemerick.friend :as friend]
            [cemerick.friend [workflows :as workflows]
                             [credentials :as creds]]))

(defn- client->friend [rclient]
  {:client rclient
   :identity (:username rclient)
   :roles #{:user}})

(defmulti bio-credential-fn
  "Handle login with multiple credentials to useful biological services."
  (fn [params]
    (let [{:keys [username password galaxy-apikey]} params]
      (cond
       (and (seq username) (seq password)) :gs
       (seq galaxy-apikey) :galaxy
       :else :default))))

(defmethod bio-credential-fn :gs
  ^{:doc "Given map with GenomeSpace username and password keys, returns GS client (if valid) or nil."}
  [{:keys [username password]}]
  (when (and (seq username) (seq password))
    (let [rclient (get-client {:username username :password password :type :gs})]
      (when (:conn rclient)
        (client->friend rclient)))))

(defmethod bio-credential-fn :galaxy
  ^{:doc "Retrieve Galaxy client connection using API key."}
  [{:keys [galaxy-server galaxy-apikey]}]
  (when (seq galaxy-apikey)
    (let [rclient (get-client {:url galaxy-server :api-key galaxy-apikey :type :galaxy})]
      (when (:conn rclient)
        (client->friend rclient)))))

(defmethod bio-credential-fn :default [_] nil)

(defn bio-remote-workflow
  "Provide retrieval of friend authentication for remote servers."
  [& {:keys [login-uri credential-fn redirect-on-auth?] :as form-config
      :or {redirect-on-auth? true}}]
  (fn [{:keys [uri request-method params] :as request}]
    (when (and (= uri (get-in request [::friend/auth-config :login-uri]))
               (= :post request-method))
      (let [credential-fn (or credential-fn (get-in request [::friend/auth-config :credential-fn]))
            friend-kw :bio-remote]
        (if-let [user-rec (credential-fn (with-meta params {::friend/workflow friend-kw}))]
          (workflows/make-auth user-rec
                               {::friend/workflow friend-kw
                                ::friend/redirect-on-auth? redirect-on-auth?})
          ((or (get-in request [::friend/auth-config :login-failure-handler])
               #'workflows/interactive-login-redirect)
           (update-in request [::friend/auth-config] merge form-config)))))))
