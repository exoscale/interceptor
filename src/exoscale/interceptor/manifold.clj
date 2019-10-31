(ns exoscale.interceptor.manifold
  (:require [exoscale.interceptor.protocols :as p]
            [exoscale.interceptor.impl :as impl]
            [exoscale.interceptor.utils :as utils]))

(utils/compile-when-available manifold.deferred
 (prn "Found manifold on classpath")
 (require '[manifold.deferred :as d])
 (extend-protocol p/Async
   manifold.deferred.IDeferred
   (then [d f] (d/chain' d f))
   (catch [d f] (d/catch' d f)))

 (in-ns 'exoscale.interceptor)
 (require '[manifold.deferred :as d])
 (defn execute-deferred
   "Like `exoscale.interceptor/execute` but ensures we always get a
  manifild.Deferred back"
   ([ctx interceptors]
    (try
      (let [result (impl/execute ctx interceptors)]
        (cond-> result
          (not (d/deferred? result))
          (d/success-deferred)))
      (catch Exception e
        (d/error-deferred e))))))
