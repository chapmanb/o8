(ns o8.api
  "Expose variant data retrieval and processing to client calls."
  (:use compojure.core
        [cemerick.friend :only [current-authentication]]
        [cemerick.shoreleave.rpc :only [defremote current-request]]
        [bcbio.variation.api.run :only [do-analysis]]
        [bcbio.variation.api.shared :only [web-config]]
        [cheshire.core :only [generate-string]])
  (:require [clojure.string :as string]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [bcbio.variation.api.file :as bc-file]
            [bcbio.variation.api.metrics :as bc-metrics]
            [bcbio.variation.remote.core :as remote]
            [o8.dataset :as dataset]
            [o8.remote.clinvar :as clinvar]))

(defremote ^{:remote-name :meta/username} get-username []
  (-> (current-authentication)
      :client
      :username))

(defremote ^{:remote-name :meta/genomes} get-genomes []
  (map (fn [x] {:value (:sample x)
                :text (format "%s (%s)" (:sample x) (:description x))})
       (:ref @web-config)))

(defremote ^{:remote-name :variant/external-dirs} list-external-dirs
  []
  (letfn [(prep-display-path [x]
            {:full (:id x)
             :name (last (string/split (:name x) #"/"))})]
    (if-let [rclient (:client (current-authentication))]
      (map prep-display-path (remote/list-dirs rclient "."))
      [])))

(defremote ^{:remote-name :variant/external-files} list-external-files
  [dir ftype]
  (if-let [rclient (:client (current-authentication))]
    (map (fn [x] {:full (:id x)
                  :name (:filename x)})
         (remote/list-files rclient {:id dir} ftype))
    []))

(defn expose-dataset
  "Create a local callback to retrieve a dataset from the server. "
  [req]
  (let [ds-path "dataset"]
    (fn [local-file remote-host]
      (dataset/expose-w-url local-file remote-host
                            (-> req :headers (get "origin")) ds-path))))

(defremote ^{:remote-name :variant/context} variant-context
  "Retrieve user-level details about available files and global metrics."
  []
  (when-let [rclient (:client (current-authentication))]
    {:files (bc-file/list-files-w-cache rclient :vcf)
     :metrics (bc-metrics/available-metrics nil)
     :username (get-username)}))

;; (defremote ^{:remote-name :variant/raw} variant-raw
;;   "Retrieve raw variant data for a given input file"
;;   [file-url]
;;   (when-let [rclient (:client (current-authentication))]
;;     (let [raw (bc-metrics/get-raw-metrics file-url
;;                                           :rclient rclient :use-subsample? true)]
;;       {:available-metrics (-> raw first (dissoc :id) keys set)
;;        :raw (generate-string raw)})))

(defremote ^{:remote-name :run/filter} run-filter
  "Run filtering analysis, pushing results to remote server.
   TODO: make asynchronous and provide callbacks to check status"
  [file-url metrics]
  (when-let [rclient (:client (current-authentication))]
    (-> (do-analysis :filter {:filename file-url :metrics metrics
                              :expose-fn (expose-dataset (current-request))}
                     rclient)
        :runner
        deref)))

(defremote ^{:remote-name :run/clinvar} run-clinvar
  "Provide a redirect link after uploading current filtered information to ClinVar"
  [file-url metrics]
  (when-let [rclient (:client (current-authentication))]
    (clinvar/submit-vcf file-url)))

(defroutes api-routes
  (GET "/vcf" req
       (when-let [rclient (:client (current-authentication))]
         (let [{{file-url :file-url} :params} req]
           (let [{:keys [raw metrics]} (bc-metrics/get-raw-metrics file-url
                                                                   :rclient rclient :use-subsample? true)]
             (generate-string
              {:clj (pr-str {:file-url file-url
                             :available-metrics metrics})
               :raw raw})))))

  (route/not-found "API ERROR =("))
