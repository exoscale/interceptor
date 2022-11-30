(ns exoscale.interceptor-discard-test
  (:require [clojure.test :refer :all]
            [exoscale.interceptor :as ix]))

(deftest discard-can-return-nil-test
  (testing "ix/discard doesnt return anything"
    (let [f (fn [_] (println "fn called"))
          test-ix {:name  ::discard-ix
                    :leave (-> f
                               (ix/in [:a])
                               (ix/discard))}]
      (is (= {:a 1}
             (-> (ix/execute {:a 1} [test-ix])
                 (select-keys [:a])))))))
