(ns exoscale.interceptor.impl
  (:require [exoscale.interceptor.protocols :as p])
  (:import (clojure.lang PersistentQueue)))

;;; Core api

(def empty-queue
  #?(:clj (clojure.lang.PersistentQueue/EMPTY)
     :cljs #queue []))

(defn try-f
  [ctx f err]
  (if f
    (try
      (let [ctx' (if err
                   (f ctx err)
                   (f ctx))]
        (cond-> ctx'
          (p/async? ctx')
          (p/catch (fn [e] (assoc ctx :exoscale.interceptor/error e)))))
      (catch #?(:clj Exception :cljs :default) e
        (assoc ctx :exoscale.interceptor/error e)))
    ctx))

(defn leave [ctx]
  (if (p/async? ctx)
    (p/then ctx leave)
    (let [stack (:exoscale.interceptor/stack ctx)]
      (if-let [interceptor (peek stack)]
        (recur (let [err (:exoscale.interceptor/error ctx)]
                 (try-f (assoc ctx :exoscale.interceptor/stack (pop stack))
                        (get interceptor (if err :error :leave))
                        err)))
        ctx))))

(defn enter [ctx]
  (if (p/async? ctx)
    (p/then ctx enter)
    (let [queue (:exoscale.interceptor/queue ctx)
          stack (:exoscale.interceptor/stack ctx)
          interceptor (peek queue)]
      (if (or (not interceptor)
              (:exoscale.interceptor/error ctx))
        ctx
        (-> (assoc ctx
                   :exoscale.interceptor/queue (pop queue)
                   :exoscale.interceptor/stack (conj stack interceptor))
            (try-f (:enter interceptor) nil)
            recur)))))

(defn complete
  [ctx]
  (if (p/async? ctx)
    (p/then ctx complete)
    (if-let [err (:exoscale.interceptor/error ctx)]
      (throw err)
      ctx)))

(defn execute
  ([ctx]
   (-> ctx
       (enter)
       (leave)
       (complete)))
  ([ctx interceptors]
   (-> ctx
       (assoc :exoscale.interceptor/queue (into empty-queue
                                                (keep p/interceptor)
                                                interceptors))
       (execute))))
