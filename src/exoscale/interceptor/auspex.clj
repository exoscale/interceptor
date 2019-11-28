(ns exoscale.interceptor.auspex
  "Auspex support"
  (:require [exoscale.interceptor.protocols :as p]
            [exoscale.interceptor.impl :as impl]
            [qbits.auspex :as auspex]))

(extend-protocol p/AsyncContext
  java.util.concurrent.CompletableFuture
  (then [d f] (auspex/chain d f))
  (catch [d f] (auspex/catch d f)))

(defn execute
  "Like `exoscale.interceptor/execute` but ensures we always get a
  CompletableFuture back"
  ([ctx interceptors]
   (execute (impl/enqueue ctx interceptors)))
  ([ctx]
   (let [fut (auspex/future)]
     (impl/execute ctx
                   #(auspex/success! fut %)
                   #(auspex/error! fut %))
     fut)))
