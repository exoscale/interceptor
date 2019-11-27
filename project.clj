(defproject exoscale/interceptor "0.1.6"
  :license {:name "ISC"}
  :url "https://github.com/exoscale/interceptor"
  :dependencies [[org.clojure/clojure "1.10.1"]]
  :deploy-repositories [["snapshots" :clojars] ["releases" :clojars]]
  :profiles {:dev
             {:dependencies [[org.clojure/clojurescript "1.10.439"]
                             [manifold "0.1.8"]
                             [org.clojure/core.async "0.4.500"]
                             [cc.qbits/auspex "0.1.0-alpha2"]]
              :plugins [[lein-cljsbuild "1.1.7"]]

              :cljsbuild {:builds
                          [{:id "default"
                            :source-paths ["src"]
                            :compiler {:optimizations :whitespace
                                       :pretty-print true}}]}}}
  :pedantic? :warn)
