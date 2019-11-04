(ns exoscale.interceptor-test
  (:require [clojure.test :refer :all]
            [exoscale.interceptor :as ix]
            [exoscale.interceptor.manifold :as ixm]
            [exoscale.interceptor.core-async :as ixa]
            [exoscale.interceptor.auspex :as ixq]
            [manifold.deferred :as d]
            [qbits.auspex :as q]
            [clojure.core.async :as a]))

(def iinc {:error (fn [ctx err]
                    ctx)
           :enter (fn [ctx]
                    (update ctx :a inc))
           :leave (fn [ctx]
                    (update ctx :b inc))})

(def a {:enter #(update % :x conj :a)
        :leave #(update % :x conj :f)})

(def b {:enter #(update % :x conj :b)
        :leave #(update % :x conj :e)})

(def c {:enter #(update % :x conj :c)
        :leave #(update % :x conj :d)})


(def start-ctx {:a 0 :b 0})
(def default-result {:a 3 :b 3})
(def ex (ex-info "boom" {}))
(def throwing (fn [_] (throw ex)))

(defn clean-ctx
  [ctx]
  (dissoc ctx ::ix/queue ::ix/stack))

(deftest a-test-workflow
  (is (= default-result
         (-> (ix/execute start-ctx
                         [iinc iinc iinc])
             clean-ctx)))

  (is (= {:a 2 :b 2}
         (-> (ix/execute start-ctx
                         [iinc iinc])
             clean-ctx)))

  (is (= {}
         (-> (ix/execute {}
                         [])
             clean-ctx)))

  (is (= {}
         (-> (ix/execute {})
             clean-ctx)))


  (is (= {:a 1 :b 2}
         (-> (ix/execute start-ctx
                         [iinc
                          (assoc iinc
                                 :enter (fn [ctx] (ix/terminate ctx)))])
             clean-ctx)))

  (is (= {:a 0 :b 1}
         (-> (ix/execute start-ctx
                         [(assoc iinc
                                 :enter (fn [ctx] (ix/terminate ctx)))
                          iinc])
             clean-ctx)))


  ;; test flow

  (let [ctx {:x []}]
    (is (= [:a :b :c] (ix/execute ctx [a b c :x]))))

  (let [ctx {:x []}]
    (is (= [:a :b :c :d :e :f] (:x (ix/execute ctx [a b c])))))

  (let [ctx {:x []}
        b {:leave #(update % :x conj :e)
           :enter throwing
           :error (fn [ctx err]
                    ctx)}]
    ;; shortcuts to error b then stack aq
    (is (= [:a :f] (:x (ix/execute ctx [a b c])))))

  (let [ctx {:x []}
        a {:enter #(update % :x conj :a)
           :leave #(update % :x conj :f)
           :error (fn [ctx err]  ctx)}
        b {:leave throwing
           :enter #(update % :x conj :b)}]
    ;; a b c then c b(boom) a(error)
    (is (= [:a :b :c :d] (:x (ix/execute ctx [a b c])))))

  (let [ctx {:x []}
        c {:enter throwing
           :leave #(update % :x conj :d)
           :error (fn [ctx err]  ctx)}]
    ;; a b c(boom) then b a
    (is (= [:a :b :e :f] (:x (ix/execute ctx [a b c])))))

  (let [ctx {:x []}
        a {:enter #(throw (ex-info "boom" %))}]
    ;; a b c(boom) then b a
    (is (thrown? Exception (ix/execute ctx [a])))))

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
           (-> @(ixm/execute-deferred {} [])
               clean-ctx))))
  (let [dinc {:enter (fn [ctx] (d/success-deferred (update ctx :a inc)))
              :leave (fn [ctx] (d/error-deferred ex))}]
    (is (thrown? Exception @(ixm/execute-deferred start-ctx
                                                  [dinc])))))


(deftest auspex-test
  (let [dinc {:enter (fn [ctx] (q/success-future (update ctx :a inc)))
              :leave (fn [ctx] (q/success-future (update ctx :b inc)))}]

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
           (-> @(ixq/execute-future {} [])
               clean-ctx))))
  (let [dinc {:enter (fn [ctx] (q/success-future (update ctx :a inc)))
              :leave (fn [ctx] (q/error-future ex))}]
    (is (thrown? Exception @(ixq/execute-future start-ctx
                                                [dinc])))))

(deftest core-async-test
  (let [dinc {:enter (fn [ctx]
                       (doto (a/promise-chan)
                         (a/offer! (update ctx :a inc))))
              :leave (fn [ctx]
                       (doto (a/promise-chan)
                         (a/offer! (update ctx :b inc))))}]

    (is (= default-result
           (-> (a/<!! (ix/execute start-ctx
                                  [dinc dinc dinc]))
               clean-ctx)))

    (is (= default-result
           (-> (a/<!! (ixa/execute-chan start-ctx
                                        [dinc dinc iinc]))
               clean-ctx)))

    (is (= default-result
           (-> (a/<!! (ixa/execute-chan start-ctx
                                        [dinc iinc dinc]))
               clean-ctx)))

    (is (= default-result
           (-> (a/<!! (ixa/execute-chan start-ctx
                                        [iinc dinc dinc]))
               clean-ctx)))

    (is (= default-result
           (-> (a/<!! (ixa/execute-chan start-ctx
                                        [dinc dinc dinc]))
               clean-ctx)))

    (is (= {:a 2 :b 2}
           (-> (a/<!! (ixa/execute-chan start-ctx [dinc dinc]))
               clean-ctx)))

    (is (= {}
           (-> (a/<!! (ixa/execute-chan {} []))
               clean-ctx)))
    (let [dinc {:enter (fn [ctx] (doto (a/promise-chan (a/offer! (update ctx :a inc)))))
                :leave (fn [ctx]
                         (doto (a/promise-chan (a/offer! (ex-info "boom" {})))))}]

      (is (instance? Exception (a/<!! (ixa/execute-chan start-ctx
                                                        [dinc]))))

      (is (instance? Exception (a/<!! (ixa/execute-chan start-ctx
                                                        [{:enter throwing}])))))))a
