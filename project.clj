(defproject exoscale/interceptor "0.1.10-SNAPSHOT"
  :license {:name "ISC"}
  :url "https://github.com/exoscale/interceptor"
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/tools.logging "1.1.0"]]
  :deploy-repositories [["snapshots" :clojars] ["releases" :clojars]]
  :profiles {:test
             {:dependencies [[ch.qos.logback/logback-classic "1.1.3"]]
              :jvm-opts ["-Dclojure.tools.logging.factory=clojure.tools.logging.impl/slf4j-factory"]}

             :dev
             {:dependencies [[org.clojure/clojurescript "1.10.439"]
                             [manifold "0.1.8"]
                             [org.clojure/core.async "0.4.500"]
                             [cc.qbits/auspex "0.1.0-alpha2"]
                             ;;bench+logging
                             [spootnik/unilog "0.7.24"
                              :exclusions [ch.qos.logback/logback-classic
                                           ch.qos.logback/logback-core
                                           com.fasterxml.jackson.core/jackson-core]]
                             [ch.qos.logback/logback-classic "1.1.3"]
                             [ch.qos.logback/logback-core "1.1.3"]]
              :jvm-opts ["-Dclojure.tools.logging.factory=clojure.tools.logging.impl/slf4j-factory"]
              :plugins [[lein-cljsbuild "1.1.7"]]
              :cljsbuild {:builds
                          [{:id "default"
                            :source-paths ["src"]
                            :compiler {:optimizations :whitespace
                                       :pretty-print true}}]}}}
  :cljsbuild {:repl-listen-port 9000
              :repl-launch-commands {"ffox" ["firefox" "-jsconsole"]}}
  :pedantic? :warn)
