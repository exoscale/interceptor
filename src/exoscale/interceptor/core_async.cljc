(ns exoscale.interceptor.core-async
  (:require [exoscale.interceptor.utils :as u]))

(u/compile-when-available clojure.core.async
  (in-ns 'exoscale.interceptor)

  #?(:clj (require '[clojure.core.async :as async])
     :cljs (require '[cljs.core.async :as async]))
  (require '[exoscale.interceptor.utils :as u])
  (require '[exoscale.interceptor.protocols :as p])
  (require '[exoscale.interceptor.impl :as impl])

  (extend-protocol p/Async
    #?(:clj clojure.core.async.impl.protocols.Channel
       :cljs cljs.core.async.impl.channels/ManyToManyChannel)
    (then [ch f]
      (let [out-ch (async/promise-chan)]
        (async/take! ch #(async/offer! out-ch %))
        out-ch))
    (catch [ch f]
        (let [out-ch (async/promise-chan)]
          (async/take! ch #(async/offer! out-ch
                                         (cond-> %
                                           (u/exception? %)
                                           (f %))))
          out-ch)))

  (defn execute-chan
    ""
    ([ctx interceptors]
     (try
       (let [result (impl/execute ctx interceptors)]
         (cond-> result
           (not (instance? #?(:clj clojure.core.async.impl.protocols.Channel
                              :cljs cljs.core.async.impl.channels/ManyToManyChannel)
                           result))
           (async/promise-chan)))
       (catch #?(:clj Exception :cljs :default) e
         (async/promise-chan e))))))
