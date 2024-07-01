(defproject exoscale/interceptor "0.1.17"
  :license {:name "ISC"}
  :url "https://github.com/exoscale/interceptor"
  :dependencies [[org.clojure/clojure "1.11.1"]]
  :deploy-repositories [["snapshots" :clojars] ["releases" :clojars]]
  :profiles {:dev
             {:source-paths ["dev"]
              :dependencies [[org.clojure/clojurescript "1.11.54"]
                             [manifold "0.2.4"]
                             [org.clojure/core.async "1.5.648"]
                             [cc.qbits/auspex "1.0.0-alpha9"]
                             [criterium "0.4.6"]]
              :plugins [[lein-cljsbuild "1.1.7"]]

              :cljsbuild {:builds
                          [{:id "default"
                            :source-paths ["src"]
                            :compiler {:optimizations :whitespace
                                       :pretty-print true}}]}}}
  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "--no-sign"]
                  ["deploy" "clojars"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]
  :pedantic? :warn)
