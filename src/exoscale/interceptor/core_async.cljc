(ns exoscale.interceptor.core-async
  "core.async support"
  (:require #?(:clj [clojure.core.async :as async]
               :cljs [cljs.core.async :as async])
            [exoscale.interceptor.protocols :as p]
            [exoscale.interceptor.impl :as impl]))

(defn ^:no-doc exception?
  [e]
  (instance? #?(:clj Exception
                :cljs js/Error)
             e))

(defn ^:no-doc channel?
  [x]
  (instance? #?(:clj clojure.core.async.impl.channels.ManyToManyChannel
                :cljs cljs.core.async.impl.channels.ManyToManyChannel)
             x))

(defn ^:no-doc fmap
  [ch f]
  (async/take! ch
               #(if (channel? %)
                  (fmap % f)
                  (f %))))

(defn ^:no-doc offer!
  [ch x]
  (if x
    (async/offer! ch x)
    (async/close! ch))
  ch)

(extend-protocol p/Async
  #?(:cljs cljs.core.async.impl.channels.ManyToManyChannel
     :clj clojure.core.async.impl.channels.ManyToManyChannel)
  (then [ch f]
    (let [out-ch (async/promise-chan)]
      (fmap ch #(offer! out-ch (f %)))
      out-ch))

  (catch [ch f]
    (let [out-ch (async/promise-chan)]
      (fmap ch
            #(offer! out-ch
                     (cond-> %
                       (exception? %)
                       (f %))))
      out-ch)))

(defn execute-chan
  "Like `exoscale.interceptor/execute` but ensures we always get a
  core.async channel back"
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
