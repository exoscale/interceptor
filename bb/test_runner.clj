(ns test-runner
  (:require
   [clojure.test :as t]
   [exoscale.interceptor-test]))

(defn run-tests [_]
  (t/run-tests 'exoscale.interceptor-test))
