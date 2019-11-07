(ns exoscale.interceptor.manifold
  "Manifold support"
  (:require [exoscale.interceptor.protocols :as p]
            [exoscale.interceptor.impl :as impl]
            [manifold.deferred :as d]))

(extend-protocol p/AsyncContext
  manifold.deferred.IDeferred
  (then [d f] (d/chain' d f))
  (catch [d f] (d/catch' d f)))

(defn execute-deferred
  "Like `exoscale.interceptor/execute` but ensures we always get a
  manifold.Deferred back"
  ([ctx interceptors]
   (execute-deferred (impl/assoc-queue ctx interceptors)))
  ([ctx]
   (let [d (d/deferred)]
     (impl/execute ctx
                   #(d/success! d %)
                   #(d/error! d %))
     d)))
