(defproject com.keminglabs/vcf "0.0.1-SNAPSHOT"
  :description "Genetic variant metric visualization tool"
  :license {:name "BSD" :url "http://www.opensource.org/licenses/BSD-3-Clause"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 
                 [com.keminglabs/c2 "0.2.1-SNAPSHOT"]
                 [com.keminglabs/chosen "0.1.7-SNAPSHOT"]
                 
                 [compojure "1.1.1"]
                 [ring/ring-core "1.1.1"]
                 [ring/ring-jetty-adapter "1.1.1"]
                 [com.cemerick/friend "0.0.9"]
                 
                 [bcbio.variation "0.0.1-SNAPSHOT"
                  :exclusions [org.clojars.chapmanb/gatk-lite]
                  ]]
  
  :profiles {:dev {:dependencies [[midje "1.4.0"]
                                  [clj-http "0.5.0"]]}}
  
  :min-lein-version "2.0.0"
  :source-paths ["src/clj" "src/cljs"]
  
  :plugins [[lein-cljsbuild "0.2.4"]]

  :cljsbuild {:builds
              [{:source-path "src/cljs"
                :compiler {:output-to "public/vcf.js"
                           
                           ;; :optimizations :advanced
                           ;; :pretty-print false
                           
                           :optimizations :whitespace
                           :pretty-print true

                           :externs ["externs/jquery.js"
                                     "resources/closure-js/externs"]}}]})
