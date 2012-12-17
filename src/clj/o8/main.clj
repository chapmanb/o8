(ns o8.main
  (:gen-class)
  (:use [o8.server :only [start!]]
        [bcbio.variation.api.shared :only [set-config-from-file!]]
        [clojure.tools.cli :only [cli]]))

(def ^{:private true}
  default-config "config/web-processing.yaml")

(defn devel-set-config! []
  (set-config-from-file! default-config))

(defn -main [& args]
  (let [[opts args banner] (cli args
                                ["-h" "--help" "Show help" :default false :flag true]
                                ["--port" "Webserver port" :default 8080 :parse-fn #(Integer. %)]
                                ["-c" "--config" "Default genome config YAML" :default default-config])]

    (when (:help opts)
      (println banner)
      (System/exit 0))
    
    (set-config-from-file! (:config opts))
    
    (start! (:port opts))))

