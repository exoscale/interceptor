(ns ^:no-doc exoscale.interceptor.impl
  "Core implementation"
  (:require [exoscale.interceptor.protocols :as p]))

;;; Core api

(def empty-queue
  #?(:clj (clojure.lang.PersistentQueue/EMPTY)
     :cljs #queue []))

(defn run-stage
  [ctx interceptor stage err]
  (if-let [f (get interceptor stage)]
    (try
      (let [ctx' (if err
                   (f (dissoc ctx :exoscale.interceptor/error) err)
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
                 (run-stage (assoc ctx :exoscale.interceptor/stack (pop stack))
                            interceptor
                            (if err :error :leave)
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
            (run-stage interceptor :enter nil)
            recur)))))

(defn complete
  [ctx success error]
  (if (p/async? ctx)
    (p/then ctx #(complete % success error))
    (if-let [err (:exoscale.interceptor/error ctx)]
      (error err)
      (success ctx))))

(defn queue
  [q interceptors]
  (into (or q empty-queue)
        (map p/interceptor)
        interceptors))

(defn assoc-queue
  [ctx interceptors]
  (assoc ctx
         :exoscale.interceptor/queue
         (queue nil interceptors)))

(defn execute
  [ctx success error]
  (-> ctx
      (enter)
      (leave)
      (complete success error)))
