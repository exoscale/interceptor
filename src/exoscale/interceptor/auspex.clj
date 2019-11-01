(ns exoscale.interceptor.auspex
  (:require [exoscale.interceptor.utils :as u]))

(u/compile-when-available qbits.auspex
  (in-ns 'exoscale.interceptor)
  (require '[qbits.auspex :as auspex])
  (require '[exoscale.interceptor.utils :as u])
  (require '[exoscale.interceptor.protocols :as p])
  (require '[exoscale.interceptor.impl :as impl])
  (extend-protocol p/Async
    java.util.concurrent.CompletableFuture
    (then [d f] (auspex/chain d f))
    (catch [d f] (auspex/catch d f)))

  (defn execute-future
    "Like `exoscale.interceptor/execute` but ensures we always get a
  CompletableFuture back"
    ([ctx interceptors]
     (try
       (let [result (impl/execute ctx interceptors)]
         (cond-> result
           (not (auspex/future? result))
           (auspex/success-future)))
       (catch Exception e
         (auspex/error-future e))))))
