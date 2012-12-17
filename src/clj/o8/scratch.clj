(ns o8.scratch
  (:use [bcbio.variation.api.metrics :only [plot-ready-metrics get-raw-metrics]]
        [bcbio.variation.api.file :only [get-files]]
        [bcbio.variation.api.run :only [do-analysis]]
        [bcbio.variation.api.shared :only [set-config-from-file!]]
        [cheshire.core :only [generate-string]]))


(def config-yaml "config/web-processing.yaml")
(def vcf1 "vendor/bcbio.variation/test/data/gatk-calls.vcf")
(def vcf2 "vendor/bcbio.variation/test/data/freebayes-calls.vcf")

(set-config-from-file! config-yaml)

[(plot-ready-metrics vcf1)
 (plot-ready-metrics vcf2)]

(def creds {:username "keminglabs" :password "vcftest"})
(def files (get-files :vcf creds))

(plot-ready-metrics (:id (first files))
                    :creds creds)

(do-analysis :filter
             {:filename (:id (first files))
              :metrics {"DP" [0 100]}}
             creds)


(def big "data/NA19239-v0_1-prep-negtrain.vcf")

;;Needed to increase heap size to 2 GB for this one: -Xmx2g
(spit "public/big.json"
      (let [raw (get-raw-metrics big)]
        (generate-string
         {:clj (pr-str {:file-url "data/NA19239-v0_1-prep-negtrain.vcf"
                        :available-metrics (-> raw first (dissoc :id) keys set)})
          :raw raw})))
