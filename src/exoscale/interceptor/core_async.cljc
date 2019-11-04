(ns exoscale.interceptor.core-async
  "core.async support"
  {:no-doc true}
  (:require #?(:clj [clojure.core.async :as async]
               :cljs [cljs.core.async :as async])
            [exoscale.interceptor.utils :as u]
            [exoscale.interceptor.protocols :as p]
            [exoscale.interceptor.impl :as impl]))

(defmacro with-promise
  [p & body]
  `(let [~p (async/promise-chan)]
     ~@body
     ~p))

(defn channel?
  [x]
  (instance? #?(:clj clojure.core.async.impl.channels.ManyToManyChannel
                :cljs cljs.core.async.impl.channels.ManyToManyChannel)
             x))

(defn wrap
  [x]
  (cond-> x
    (not (channel? x))
    (async/promise-chan x)))

(defn fmap
  [ch f]
  (async/take! ch
               #(if (channel? %)
                  (fmap % f)
                  (f %))))

(defn offer!
  [ch x]
  (if x
    (async/offer! ch x)
    (async/close! ch))
  ch)

(extend-protocol p/Async
  #?(:cljs cljs.core.async.impl.channels.ManyToManyChannel
     :clj clojure.core.async.impl.channels.ManyToManyChannel)
  (then [ch f]
    (with-promise out-ch
      (fmap ch #(offer! out-ch (f %)))))

  (catch [ch f]
    (with-promise out-ch
      (fmap ch
            #(offer! out-ch
                     (cond-> %
                       (u/exception? %)
                       (f %)))))))

(defn execute-chan
  ([ctx interceptors]
   (try
     (let [result (impl/execute ctx interceptors)]
       (if (channel? result)
         result
         (offer! (async/promise-chan)
                 result)))

     (catch #?(:clj Exception :cljs :default) e
       (doto (async/promise-chan)
         (async/offer! e))))))
