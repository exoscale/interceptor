(ns ^:no-doc exoscale.interceptor.impl
  "Core implementation"
  (:require [exoscale.interceptor.protocols :as p]))

(defrecord Interceptor [enter leave error])

(extend-protocol p/Interceptor
  #?(:clj clojure.lang.IPersistentMap
     :cljs cljs.core.PersistentHashMap)
  (interceptor [m] (map->Interceptor m))

  clojure.lang.IRecord
  (interceptor [r] r)

  #?(:clj clojure.lang.Fn
     :cljs function)
  (interceptor [f]
    (p/interceptor {:enter f}))

  clojure.lang.Keyword
  (interceptor [f]
    (p/interceptor {:enter f}))

  clojure.lang.Var
  (interceptor [v]
    (p/interceptor (deref v))))

;; not working in cljs for some reason
#?(:clj
   (extend-protocol p/Interceptor
     clojure.lang.Symbol
     (interceptor [s]
       (p/interceptor (resolve s)))))

(def empty-queue
  #?(:clj clojure.lang.PersistentQueue/EMPTY
     :cljs #queue []))

(defn invoke-stage
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
                 (invoke-stage (assoc ctx :exoscale.interceptor/stack (pop stack))
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
            (invoke-stage interceptor :enter nil)
            recur)))))

(defn complete
  [ctx success error]
  (if (p/async? ctx)
    (p/then ctx #(complete % success error))
    (if-let [err (:exoscale.interceptor/error ctx)]
      (error err)
      (success ctx))))

(defn apply-middleware
  [{:keys [enter leave] :as ix} {:exoscale.interceptor/keys [middleware] :as ctx}]
  (cond-> ix
    (some? enter) (update :enter middleware ctx)
    (some? leave) (update :leave middleware ctx)))

(defn into-queue
  [q ctx interceptors]
  (into (or q empty-queue)
        (map #(cond-> (p/interceptor %)
                (some? (:exoscale.interceptor/middleware ctx))
                (apply-middleware ctx)))
        interceptors))

(defn enqueue
  [ctx interceptors]
  (update ctx
          :exoscale.interceptor/queue
          into-queue
          ctx
          interceptors))

(defn execute
  [ctx success error]
  (-> ctx
      (enter)
      (leave)
      (complete success error)))
