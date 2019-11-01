(ns exoscale.interceptor
  (:refer-clojure :exclude [when throw])
  (:require [exoscale.interceptor.impl :as impl]
            [exoscale.interceptor.protocols :as p]
            ;; Will only do anything if libs are present on classpath
            [exoscale.interceptor.core-async]
            #?(:clj [exoscale.interceptor.manifold])
            #?(:clj [exoscale.interceptor.auspex])))

(defrecord Interceptor [name enter leave error])

(extend-protocol p/Interceptor
  #?(:clj clojure.lang.IPersistentMap
     :cljs cljs.core.PersistentHashMap)
  (interceptor [m] (map->Interceptor m))

  clojure.lang.IRecord
  (interceptor [r] r)

  #?(:clj clojure.lang.Fn
     :cljs function)
  (interceptor [f]
    {:enter f})

  clojure.lang.Keyword
  (interceptor [f]
    (p/interceptor {:enter f}))

  clojure.lang.Symbol
  (interceptor [s]
    (p/interceptor (resolve s)))

  clojure.lang.Var
  (interceptor [v]
    (p/interceptor (deref v))))


;;; API

(defn execute
  "Executes a queue of Interceptors attached to the context. Context
  must be a map.

  An Interceptor is a map or map-like object with the keys :enter,
  :leave, and :error. The value of each key is a function; missing
  keys or nil values are ignored. When executing a context, first all
  the :enter functions are invoked in order. As this happens, the
  Interceptors are pushed on to a stack.

  When execution reaches the end of the queue, it begins popping
  Interceptors off the stack and calling their :leave functions.
  Therefore :leave functions are called in the opposite order from
  :enter functions.

  Both the :enter and :leave functions are called on a single
  argument, the context map, and return an updated context.

  If any Interceptor function throws an exception, execution stops and
  begins popping Interceptors off the stack and calling their :error
  functions. The :error function takes a single argument: the context
  which would contain an :error key with the error triggering the
  call.  If you leave the :error key in the context the other :error
  handlers will be triggered from the stack, if you remove it it will
  resume triggering :leave functions, you can also call (resume ctx) to
  remove the error and resume execution.

  If the :error reaches the end of the stack without being handled,
  execute will throw it."
  ([ctx]
   (impl/execute ctx))
  ([ctx interceptors]
   (impl/execute ctx interceptors)))

;;; Error handling

(defn error
  "Adds error to context, potentially triggering :error stage on
  current/next interceptor"
  [ctx error]
  (assoc ctx ::error error))

;;; queue/stack manipulation

(defn terminate
  "Removes all remaining interceptors from context's execution queue.
  This effectively short-circuits execution of Interceptors' :enter
  functions and begins executing the :leave functions."
  [ctx]
  (assoc ctx ::queue nil))

(defn halt
  "Removes all remaining interceptors from context's execution queue and stack.
  This effectively short-circuits execution of
  Interceptors' :enter/:leave and returns the context"
  [ctx]
  (assoc ctx
         ::queue nil
         ::stack nil))

(defn enqueue
  "Adds interceptors to current context"
  [ctx interceptors]
  (update ctx ::queue
          into (keep p/interceptor) interceptors))


;;; helpers/middlwares

(defn in
  "Modifies interceptor function to *take in* specified path"
  [f path]
  (fn [ctx]
    (f (get-in ctx path))))

(defn out
  "Modifies interceptor function *return at* specified path"
  [f path]
  (fn [ctx]
    (assoc-in ctx path (f ctx))))

(defn when
  "Modifies interceptor function to only run on ctx if pred returns true'ish"
  [f pred]
  (fn [ctx]
    (cond-> ctx
      (pred ctx)
      f)))

(defn lens
  "Modifies interceptor function to take from path and return to path"
  [f path]
  (-> f
      (in path)
      (out path)))

(defn discard
  "Run function for side-effects only and return context"
  [f]
  (fn [ctx]
    (doto ctx f)))

(comment
  (def async-inc-interceptor {:name ::asdfid
                               :error (fn [ctx err]
                                        ctx)
                               :enter (fn [ctx]
                                        (prn :enter-async)
                                        (d/error-deferred (update ctx :a inc))
                                        )
                               :leave (fn [ctx]
                                        (prn :leave-async)
                                        (d/success-deferred (update ctx :b inc)))})

 (def inc-interceptor1 {:name ::inc1
                        :error (fn [ctx err]
                                 (prn "error passed in inc 1" )
                                 ;; (resume ctx)
                                 ctx)
                        :enter (fn [ctx]
                                 (prn :enter-1)
                                 (update ctx :a inc))
                        :leave (fn [ctx]
                                 (prn :leave-1)
                                 (update ctx :b inc))})

 (def inc-interceptor2 {:name ::inc2
                        :error (fn [ctx err]
                                 (prn "error passed in inc 2" )
                                 ;; (ignore ctx)
                                 ;; (error ctx err)
                                 ;; (throw err)
                                 )
                        :enter (fn [ctx]
                                 (prn :enter-2)
                                 ;; (throw (ex-info "boom" {}))
                                 (update ctx :a inc))
                        :leave (fn [ctx]
                                 (prn :leave-2)
                                 (update ctx :b inc))})

 (def inc-interceptor3 {:name ::inc3
                        :error (fn [ctx err]
                                 (prn "error passed in inc 3" )
                                 ;; (throw err)
                                 )
                        :enter (fn [ctx]
                                 (prn :enter-3)
                                 (update ctx :a inc))
                        :leave (fn [ctx]
                                 (prn :leave-3)
                                 ;; (throw (ex-info "" {}))
                                 (update ctx :b inc))})



 ;; (execute {:a 0 :b 0} [inc-interceptor1 inc-interceptor2 inc-interceptor3])



 (prn "---------------------")
 (prn :result
      (execute {:a 0 :b 0}
               [
                inc-interceptor1
                ;; async-inc-interceptor
                inc-interceptor2
                inc-interceptor3
                :a
                ]))


 ;; (def inc-interceptor3
 ;;   {:name ::inc3
 ;;    :enter (-> (fn [ctx] ctx)
 ;;               (in [:request])
 ;;               (out [:response])
 ;;               (guard #(...))
 ;;               (discard #(...)))}))


 ;; (def interceptor-A {:name :A
 ;;                     :enter (fn [ctx] (update ctx :a inc))
 ;;                     :leave (fn [ctx] (assoc ctx :foo :bar))
 ;;                     :error (fn [ctx err] (throw err))})

 ;; (def interceptor-B {:name :B
 ;;                     :enter (fn [ctx] (update ctx :b inc))
 ;;                     :error (fn [ctx err] ctx)})

 ;; (def interceptor-C {:name :C
 ;;                     :enter (fn [ctx] (d/success-deferred (update ctx :c inc)))})

 ;; (def interceptor-D {:name :D
 ;;                     :enter (fn [ctx] (update ctx :d inc))})


 ;; (execute {:a 0 :b 0 :c 0 :d 0}
 ;;          [interceptor-A
 ;;           interceptor-B
 ;;           interceptor-C
 ;;           interceptor-D])

 ;; ;; because we have an async step it will return a deferred
 ;; => << {:a 1, :b 1, :c 1, :d 1, ::queue #object[...], :exoscale.interceptor/stack (), :foo :bar} >>

 ;; ;; no async step, direct result
 ;; (execute {:a 0 :b 0 :d 0}
 ;;          [interceptor-A
 ;;           interceptor-B
 ;;           interceptor-D])


 ;; => {:a 1, :b 1, :d 1, ::queue #object[...], ::stack (), :foo :bar}

 ;; (execute {:a 0}
 ;;          [{:name :foo
 ;;            :enter (-> (fn [ctx] (update ctx :a inc))
 ;;                       (guard #(contains? % :a)))}])


 ;; (execute {:request 0}
 ;;          [{:name :foo
 ;;            :enter (-> inc
 ;;                       (in [:request])
 ;;                       (out [:response]))}])
 )
