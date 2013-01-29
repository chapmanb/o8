(defproject com.keminglabs/vcf "0.0.1-SNAPSHOT"
  :description "Genetic variant visualization and analysis tool"
  :license {:name "MIT" :url "http://www.opensource.org/licenses/mit-license.html"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojure/tools.cli "0.2.1"]
                 
                 [com.keminglabs/c2 "0.2.1"
                  :exclusions [com.keminglabs/singult]]
                 [com.keminglabs/singult "0.1.5-SNAPSHOT"]
                 [com.keminglabs/chosen "0.1.7-SNAPSHOT"]
                 [com.keminglabs/dubstep "0.1.2-SNAPSHOT"]

                 [compojure "1.1.3"]
                 [ring/ring-core "1.1.6"]
                 [ring/ring-jetty-adapter "1.1.6"]
                 [com.cemerick/friend "0.1.2"]
                 [cheshire "4.0.1"]
                 [shoreleave/shoreleave-remote "0.2.2"]
                 [com.cemerick/shoreleave-remote-ring "0.0.3-SNAPSHOT"]
                 [ring-anti-forgery "0.2.1" :exclusions [hiccup]]
                 [hiccup "1.0.1"]
                 [enlive "1.0.1" :exclusions [org.clojure/clojure]]

                 [clj-http "0.6.3"]
                 [org.clojure/data.zip "0.1.1"]

                 [bcbio.variation "0.0.7-SNAPSHOT"]]

  :jvm-opts ["-Dorg.eclipse.jetty.util.log.class=org.eclipse.jetty.util.log.StdErrLog"
             "-Xms2g" "-Xmx4g"]
  :main o8.main
  
  :profiles {:dev {:dependencies [[midje "1.4.0"]
                                  [clj-http "0.5.0"]]}
             :cljs {:dependencies [[bcbio.variation "0.0.7-SNAPSHOT"
                                    :exclusions [com.google.collections/google-collections
                                                 org.clojure/clojurescript]]]}}

  :min-lein-version "2.0.0"
  :source-paths ["src/clj" "src/cljs"]
  :test-paths ["test/clj"]

  :plugins [[lein-cljsbuild "0.2.7"]
            [lein-ring "0.7.5"]]

  :ring {:handler o8.server/app
         :init o8.main/devel-set-config!}

  :cljsbuild {:builds
              [{:source-path "src/cljs/vcfvis"
                :compiler {:output-to "public/vcfvis.js"

                           ;; :optimizations :advanced
                           ;; :pretty-print false

                           :optimizations :whitespace
                           :pretty-print true
                           :externs ["externs/jquery.js"
                                     "vendor/externs.js"
                                     "resources/closure-js/externs"]}}
               {:source-path "src/cljs/o8"
                :compiler {:output-to "public/o8.js"
                           :optimizations :whitespace
                           :pretty-print true
                           :externs ["externs/jquery.js"]}}]})
