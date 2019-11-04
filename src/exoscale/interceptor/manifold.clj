(ns exoscale.interceptor.manifold
  "Manifold support"
  (:require [exoscale.interceptor.protocols :as p]
            [exoscale.interceptor.impl :as impl]
            [manifold.deferred :as d]))

(extend-protocol p/Async
  manifold.deferred.IDeferred
  (then [d f] (d/chain' d f))
  (catch [d f] (d/catch' d f)))

(defn execute-deferred
  "Like `exoscale.interceptor/execute` but ensures we always get a
  manifold.Deferred back"
  ([ctx interceptors]
   (try
     (let [result (impl/execute ctx interceptors)]
       (cond-> result
         (not (d/deferred? result))
         (d/success-deferred)))
     (catch Exception e
       (d/error-deferred e)))))
