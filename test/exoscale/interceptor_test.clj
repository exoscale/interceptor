(ns exoscale.interceptor-test
  (:require
   [clojure.core.async :as a]
   [clojure.test :refer [deftest is]]
   [exoscale.interceptor :as ix]
   [exoscale.interceptor.auspex :as ixq]
   [exoscale.interceptor.core-async :as ixa]
   [qbits.auspex :as q]))

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
           :error (fn [ctx err] ctx)}
        b {:leave throwing
           :enter #(update % :x conj :b)}]
    ;; a b c then c b(boom) a(error)
    (is (= [:a :b :c :d] (:x (ix/execute ctx [a b c])))))

  (let [ctx {:x []}
        c {:enter throwing
           :leave #(update % :x conj :d)
           :error (fn [ctx err] ctx)}]
    ;; a b c(boom) then b a
    (is (= [:a :b :e :f] (:x (ix/execute ctx [a b c])))))

  (let [ctx {:x []}
        a {:enter #(throw (ex-info "boom" %))}]
    ;; a b c(boom) then b a
    (is (thrown? Exception (ix/execute ctx [a])))))

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
           (-> (a/<!! (ixa/execute start-ctx
                                   [dinc dinc iinc]))
               clean-ctx)))

    (is (= default-result
           (-> (a/<!! (ixa/execute start-ctx
                                   [dinc iinc dinc]))
               clean-ctx)))

    (is (= default-result
           (-> (a/<!! (ixa/execute start-ctx
                                   [iinc dinc dinc]))
               clean-ctx)))

    (is (= default-result
           (-> (a/<!! (ixa/execute start-ctx
                                   [dinc dinc dinc]))
               clean-ctx)))

    (is (= {:a 2 :b 2}
           (-> (a/<!! (ixa/execute start-ctx [dinc dinc]))
               clean-ctx)))

    (is (= {}
           (-> (a/<!! (ixa/execute {} []))
               clean-ctx)))
    (let [dinc {:enter (fn [ctx] (doto (a/promise-chan (a/offer! ctx (update ctx :a inc)))))
                :leave (fn [ctx]
                         (doto (a/promise-chan (a/offer! ctx (ex-info "boom" {})))))}]

      (is (instance? Exception (a/<!! (ixa/execute start-ctx
                                                   [dinc]))))

      (is (instance? Exception (a/<!! (ixa/execute start-ctx
                                                   [{:enter throwing}])))))))

(deftest wrap-test
  (let [f #(update % :x inc)
        m #(update % :x dec)]

    (is (= 2 (-> (ix/execute {:x 0}
                             (ix/into-stages [f f]
                                             []
                                             #(fn [s _] (ix/before-stage s m))))
                 :x))
        "does nothing")
    (is (= 0 (-> (ix/execute {:x 0}
                             (ix/into-stages [f f]
                                             [:enter]
                                             (fn [s _] (ix/before-stage s m))))
                 :x))
        "decs before incs on enter")

    (is (= 2 (-> (ix/execute {:x 0}
                             (ix/into-stages [{:enter f :leave f}
                                              {:enter f :leave f}]
                                             [:enter]
                                             (fn [s _] (ix/before-stage s m))))
                 :x))
        "decs before incs on enter, not on leave")

    (is (= 0 (-> (ix/execute {:x 0}
                             (ix/into-stages [{:enter f :leave f}
                                              {:enter f :leave f}]
                                             [:enter :leave]
                                             (fn [s _] (ix/before-stage s m))))
                 :x))
        "decs before incs on enter and on leave")

    (is (= 1 (-> (ix/execute {:x 0}
                             (ix/into-stages [{:error (fn [ctx _e]
                                                        ctx)}
                                              f
                                              f
                                              {:enter (fn [_ctx] (throw (ex-info "boom" {})))}]
                                             [:error]
                                             (fn [s _]
                                               (ix/after-stage s (fn [ctx _err] (m ctx))))))
                 :x))
        "first f incs, second too, third blows up, error stage decrs")))

(deftest chain-ops
  (let [chain [{:id ::foo :enter (fn [ctx] (ix/remove ctx #(contains? #{::bar1 ::bar2} (:id %))))}
               {:id ::bar1
                :enter (fn [_] :should-be-removed)
                :leave (fn [_] :should-be-removed)
                :error (fn [_] :should-be-removed)}
               {:id ::bar2
                :enter (fn [_] :should-be-removed)
                :leave (fn [_] :should-be-removed)
                :error (fn [_] :should-be-removed)}
               {:id ::baz :enter (fn [_] :works)}]]
    (is (= :works (ix/execute {} chain)))
    (is (= {:exoscale.interceptor/stack '(1 2 3)}
           (ix/xform-stack {:exoscale.interceptor/stack '(1 2 3)}
                           (map identity))))))

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
           (-> @(ixq/execute {} [])
               clean-ctx))))
  (let [dinc {:enter (fn [ctx] (q/success-future (update ctx :a inc)))
              :leave (fn [ctx] (q/error-future ex))}]
    (is (thrown? Exception @(ixq/execute start-ctx
                                         [dinc])))))
