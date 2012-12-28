(ns o8.remote.clinvar_test
  (:use [clojure.test])
  (:require [clojure.java.io :as io]
            [o8.remote.clinvar :as clinvar]))

(deftest clinvar-submit
  (testing "Submit a VCF file to ClinVar, retrieving results URL."
    (println (clinvar/submit-vcf (io/file "test" "data" "NA12878-test.vcf") :wait? true))))