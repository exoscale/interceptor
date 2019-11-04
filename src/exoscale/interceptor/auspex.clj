(ns exoscale.interceptor.auspex
  "Auspex support"
  (:require [exoscale.interceptor.protocols :as p]
            [exoscale.interceptor.impl :as impl]
            [qbits.auspex :as auspex]))

(extend-protocol p/Async
  java.util.concurrent.CompletableFuture
  (then [d f] (auspex/chain d f))
  (catch [d f] (auspex/catch d f)))

(defn execute-future
  "Like `exoscale.interceptor/execute` but ensures we always get a
  CompletableFuture back"
  ([ctx interceptors]
   (execute-future (assoc ctx
                          :exoscale.interceptor/queue
                          (impl/queue interceptors))))
  ([ctx]
   (try
     (let [result (impl/execute ctx)]
       (cond-> result
         (not (auspex/future? result))
         (auspex/success-future)))
     (catch Exception e
       (auspex/error-future e)))))
