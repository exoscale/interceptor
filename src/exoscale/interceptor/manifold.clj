(ns exoscale.interceptor.manifold
  "Manifold support"
  (:require [exoscale.interceptor.protocols :as p]
            [exoscale.interceptor.impl :as impl]
            [manifold.deferred :as d])
  (:import (java.util.concurrent CompletableFuture)))

(extend-protocol p/AsyncContext
  manifold.deferred.IDeferred
  (then [d f] (d/chain' d f))
  (catch [d f] (d/catch' d f)))

(extend-protocol p/AsyncContext
  CompletableFuture
  (then [d f] (d/chain' d f))
  (catch [d f] (d/catch' d f)))

(defn execute
  "Like `exoscale.interceptor/execute` but ensures we always get a
  manifold.Deferred back"
  ([ctx interceptors]
   (execute (impl/enqueue ctx interceptors)))
  ([ctx]
   (let [d (d/deferred)]
     (impl/execute ctx
                   #(d/success! d %)
                   #(d/error! d %))
     d)))
