(ns o8.remote.clinvar
  "Submit file of variation information to ClinVar, retrieving results URL.
   Provides a simple API based on the support Perl API to allow external
   programs to submit to ClinVar and redirect to results."
  (:require [clojure.java.io :as io]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :as datazip]
            [clj-http.client :as client]))

(def ^{:doc "Connection information for ClinVar"}
  clinvar-config
  {:api-url "http://www.ncbi.nlm.nih.gov/SNP/VariantAnalyzer/var_rep.cgi"
   :submit-url "http://www.ncbi.nlm.nih.gov/projects/SNP/VariantAnalyzer/variantanalyzer.cgi"
   :result-url "http://www.ncbi.nlm.nih.gov/variation/tools/reporter/"})

(defn- get-jobid
  "Parse ClinVar JobID from XML returned by post."
  [s]
  (-> (java.io.ByteArrayInputStream. (.getBytes s))
      xml/parse
      zip/xml-zip
      (datazip/xml-> :jobid datazip/text)
      first))

(defn submit-vcf
  "Submit a VCF file to ClinVar, retrieving the results URL.
   Requires a VCF file corresponding to GRCh37 coordinates."
  [in-file & {:keys [wait?]}]
  (let [res (client/post (:submit-url clinvar-config)
                               {:multipart [{:name "organism" :content "9606"}
                                            {:name "source-assembly" :content "GCF_000001405.13"}
                                            {:name "annot" :content (io/file in-file)}
                                            {:name "annot1" :content ""}]
                                :follow-redirects false})
        jobid (get-jobid (:body res))
        result-url (str (:result-url clinvar-config) jobid)]
    (loop []
      (if (or (not wait?)
              (not (.contains (:body (client/get result-url)) "variation-reporter-running-page")))
        result-url
        (do (Thread/sleep 3000) (recur))))))