(ns exoscale.interceptor
  (:refer-clojure :exclude [when])
  (:require [exoscale.interceptor.impl :as impl]
            [exoscale.interceptor.protocols :as p]
            [manifold.deferred :as d]))

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
  resume triggering :leave functions.

  If the :error reaches the end of the stack without being handled,
  execute will throw it."
  ([ctx]
   (impl/execute ctx))
  ([ctx interceptors]
   (impl/execute ctx interceptors)))


;;; Error handling


(defn error
  "Sets error on context, useful when you need to extend the context
  and let the error flow to the other handlers instead of just rethrowing"
  [ctx err]
  (assoc ctx ::error err))


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
  (assoc ctx ::queue nil ::stack nil))

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

(defn guard
  "Modifies interceptor function to only run on ctx if guard is true'ish"
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

;;; Manifold support

;; FIXME throw in conditional compilation for cljs support?

(extend-protocol p/Async
  manifold.deferred.IDeferred
  (then [d f] (d/chain' d f))
  (catch [d f] (d/catch' d f)))

(defn execute-deferred
  "Like `exoscale.interceptor/execute` but ensures we always get a
  manifild.Deferred back"
  ([ctx interceptors]
   (try
     ;; make sure it always returns a deferred since last step is not
     ;; necesary sync
     (let [result (execute ctx interceptors)]
       (cond-> result
         (not (d/deferred? result))
         (d/success-deferred)))
     (catch Exception e
       (d/error-deferred e)))))

(comment
    (def async-inc-interceptor {:id ::asdfid
                                :error (fn [ctx err]
                                         (prn :err-async)
                                         ctx)
                                :enter (fn [ctx]
                                         (prn :enter-async)
                                         (d/error-deferred (update ctx :a inc))
                                         )
                                :leave (fn [ctx]
                                         (prn :leave-async)
                                         (d/success-deferred (update ctx :b inc)))})

    (def inc-interceptor1 {:id ::inc1
                           :error (fn [ctx err]
                                    (prn "error passed in inc 1" )
                                    ctx
                                    )
                           :enter (fn [ctx]
                                    (prn :enter-inc1)
                                    (update ctx :a inc))
                           :leave (fn [ctx]
                                    (prn :leave-inc1)
                                    ;; (throw (ex-info "" {}))
                                    (update ctx :b inc))})

    (def inc-interceptor2 {:id ::inc2
                           :error (fn [ctx err]
                                    (prn "error passed in inc 2" )
                                    ;; (error ctx err)
                                    ctx
                                    )
                           :enter (fn [ctx]
                                    (prn :enter-inc2)
                                    (update ctx :a inc))
                           :leave (fn [ctx]
                                    (prn :leave-inc2)
                                    (update ctx :b inc))})

(prn @(execute {:a 0 :b 0} [inc-interceptor1 inc-interceptor2 async-inc-interceptor]))

    ;; (def inc-interceptor3
    ;;   {:id ::inc3
    ;;    :enter (-> (fn [ctx] ctx)
    ;;               (in [:request])
    ;;               (out [:response])
    ;;               (guard #(...))
    ;;               (discard #(...)))}))
)
