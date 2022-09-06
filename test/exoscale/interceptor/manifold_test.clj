(ns exoscale.interceptor.manifold-test
  (:require [exoscale.interceptor.manifold :as ixm]
            [exoscale.interceptor-test :refer [default-result
                                               start-ctx clean-ctx
                                               iinc
                                               ex
                                               throwing]]
            [exoscale.interceptor :as ix]
            [manifold.deferred :as d]
            [clojure.test :as t :refer [deftest is]]
            [clojure.core.async :as a]
            [qbits.auspex :as q]
            [exoscale.interceptor.core-async :as ixa]
            [exoscale.interceptor.auspex :as ixq]))


(deftest manifold-test
  (let [dinc {:enter (fn [ctx] (d/success-deferred (update ctx :a inc)))
              :leave (fn [ctx] (d/success-deferred (update ctx :b inc)))}]

    (is (= default-result
           (-> @(ix/execute start-ctx
                            [dinc dinc dinc])
               clean-ctx)))

    (is (= default-result
           (-> @(ix/execute start-ctx
                            [dinc dinc iinc])
               clean-ctx)))

    (is (= default-result
           (-> @(ix/execute start-ctx
                            [dinc iinc dinc])
               clean-ctx)))

    (is (= default-result
           (-> @(ix/execute start-ctx
                            [iinc dinc dinc])
               clean-ctx)))

    (is (= default-result
           (-> @(ix/execute start-ctx
                            [dinc dinc dinc])
               clean-ctx)))

    (is (= {:a 2 :b 2}
           (-> @(ix/execute start-ctx [dinc dinc])
               clean-ctx)))

    (is (= {}
           (-> @(ixm/execute {} [])
               clean-ctx))))
  (let [dinc {:enter (fn [ctx] (d/success-deferred (update ctx :a inc)))
              :leave (fn [ctx] (d/error-deferred ex))}]
    (is (thrown? Exception @(ixm/execute start-ctx
                                         [dinc]))))

  (is (= 4 @(ixm/execute {:a 1}
                         [(ix/lens inc [:a])
                          (ix/out #(d/success-deferred (-> % :a inc)) [:a])
                          (ix/out #(-> % :a inc) [:a])
                          :a])))

  (is (thrown? clojure.lang.ExceptionInfo
               @(ixm/execute {:a 1}
                             [(ix/lens inc [:a])
                              (ix/out #(d/success-deferred (-> % :a inc)) [:a])
                              (ix/out #(d/error-deferred (ex-info (str %) {})) [:a])
                              (ix/out #(-> % :a inc) [:a])
                              :a])))

  (is (thrown? clojure.lang.ExceptionInfo
               @(ixm/execute {:a 1}
                             [(ix/lens inc [:a])
                              (ix/out #(d/success-deferred (-> % :a inc)) [:a])
                              (ix/out #(throw (ex-info (str %) {})) [:a])
                              (ix/out #(-> % :a inc) [:a])
                              :a])))

  (is (= 3
         (:a @(ixm/execute {:a 1}
                           [(ix/lens inc [:a])
                            (ix/out #(d/success-deferred (-> % :a inc)) [:a])
                            {:error (fn [ctx err] ctx)}
                            (ix/out #(d/error-deferred (ex-info (str %) {})) [:a])
                            (ix/out #(-> % :a inc) [:a])]))))

  (is (= 3
         (:a @(ixm/execute {:a 1}
                           [(ix/lens inc [:a])
                            (ix/out #(d/success-deferred (-> % :a inc)) [:a])
                            {:error (fn [ctx err] ctx)}
                            (ix/out #(throw (ex-info (str %) {})) [:a])
                            (ix/out #(-> % :a inc) [:a])]))))

  (is (= 42 (ix/execute {:a 42}
                        [(ix/discard (fn [ctx] (assoc ctx :a 43)))
                         :a]))))

(deftest mixed-test
  (let [a (fn [ctx]
            (doto (a/promise-chan)
              (a/offer! (update ctx :a inc))))
        m (fn [ctx] (d/success-deferred (update ctx :a inc)))
        q (fn [ctx] (q/success-future (update ctx :a inc)))]
    (is (= 3 (:a (a/<!! (ixa/execute {:a 0} [m q a])))))
    (is (= 3 (:a @(ixm/execute {:a 0} [m q a]))))
    (is (= 3 (:a @(ixq/execute {:a 0} [m q a]))))

    ;; single type can just use execute
    (is (= 3 (:a @(ix/execute {:a 0} [m m m]))))
    (is (= 3 (:a @(ix/execute {:a 0} [q q q]))))
    (is (= 3 (:a (a/<!! (ix/execute {:a 0} [a a a])))))

    (is (thrown? Exception @(ix/execute {:a 0} [m throwing m])))
    (is (thrown? Exception @(ix/execute {:a 0} [q throwing q])))

    (is (thrown? Exception @(ixm/execute {:a 0} [throwing m m])))
    (is (thrown? Exception @(ixm/execute {:a 0} [m throwing m])))
    (is (thrown? Exception @(ixm/execute {:a 0} [m m throwing])))

    (is (thrown? Exception @(ixq/execute {:a 0} [throwing q q])))
    (is (thrown? Exception @(ixq/execute {:a 0} [q throwing q])))
    (is (thrown? Exception @(ixq/execute {:a 0} [q q throwing])))

    (is (instance? Exception (a/<!! (ixa/execute {:a 0} [a throwing a]))))
    (is (instance? Exception (a/<!! (ixa/execute {:a 0} [throwing a a]))))
    (is (instance? Exception (a/<!! (ixa/execute {:a 0} [a a throwing]))))))

(deftest out-test
  (let [counter (atom 0)
        deferred-inc (fn [arg]
                       (swap! counter inc)
                       (d/success-deferred (-> arg :a inc)))]

    (is (= 2 @(ixm/execute {:a 1}
                           [(ix/out deferred-inc [:a])
                            :a])))
    (is (= 1 @counter))))

(deftest transform-test
  (is (= {:a 1 :b 2 :c 3}
         (-> @(ix/execute {:a 1}
                          [{:enter (-> (fn [ctx] (d/success-deferred (assoc ctx :c 3)))
                                       (ix/transform (fn [ctx x] (merge ctx x {:b 2}))))}])
             (select-keys [:a :b :c]))))
  (is (= {:a 1 :b 2 :c 3}
         (-> (ix/execute {:a 1}
                         [{:enter (-> (fn [ctx] (assoc ctx :c 3))
                                      (ix/transform (fn [ctx x] (merge ctx x {:b 2}))))}])
             (select-keys [:a :b :c]))))
  (is (= {:a 1 :b 2 :c 3 :d 4 :e 5 :f 6 :g 7}
         (-> @(ix/execute {:a 1}
                          [{:enter (-> (fn [ctx] (d/success-deferred (assoc ctx :c 3)))
                                       (ix/transform (fn [ctx x] (merge ctx x {:b 2})))
                                       (ix/transform (fn [ctx x] (merge ctx x {:d 4})))
                                       (ix/transform (fn [ctx x] (d/success-deferred (merge ctx x {:e 5}))))
                                       (ix/transform (fn [ctx x] (d/success-deferred (merge ctx x {:f 6})))))}
                           ;; just to make sure we preserve chaining
                           {:enter (fn [ctx] (assoc ctx :g 7))}])
             (select-keys [:a :b :c :d :e :f :g])))))
