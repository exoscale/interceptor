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
   (execute-chan (impl/assoc-queue ctx interceptors)))
  ([ctx]
   (let [ch (async/promise-chan)
         done #(async/offer! ch %)]
     (impl/execute ctx done done)
     ch)))
