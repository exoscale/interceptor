(defproject exoscale/interceptor "0.1.0-SNAPSHOT"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :url "https://github.com/exoscale/ex"
  :dependencies [[org.clojure/clojure "1.10.0"]]
  :profiles {:dev
             {:dependencies [[manifold "0.1.8"]
                             [org.clojure/core.async "0.4.500"]
                             [cc.qbits/auspex "0.1.0-alpha2"]]}}
  :pedantic? :warn
  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "--no-sign"]
                  ["deploy"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]])
