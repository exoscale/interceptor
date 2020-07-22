(ns exoscale.interceptor.utils
  "Terrible namespace name, to be changed"
  (:require [exoscale.interceptor :as ix]
            [clojure.tools.logging :as log]
            [exoscale.interceptor.protocols :as p]))

(defn- time-as
  [k]
  (fn [ctx] (assoc ctx (into [::timer] k) (java.time.Instant/now))))

(defn with-timer
  "Wrap every stage on every interceptor in a chain with timers"
  [chain]
  (into []
        (comp (map p/interceptor)
              (map (fn [{:as ix :keys [name]
                         :or {name ix}}]
                     (reduce (fn [ix k]
                               (if-let [stage (get ix k)]
                                 (assoc ix k
                                        (ix/wrap stage
                                                 {:before (time-as [name k :start])
                                                  :after (time-as [name k :end])}))
                                 ix))
                             ix
                             [:enter :leave :error]))))
        chain))

(defn- log-as
  [k {:as _opts :keys [level fmt]
      :or {level :debug
           fmt (fn [ctx k] k)}}]
  (fn [ctx]
    (log/log level (fmt ctx k))
    ;; (prn :LOG level (fmt ctx k))
    ctx))

(defn with-log
  ([chain] (with-log chain {}))
  ([chain opts]
   (into []
         (comp (map p/interceptor)
               (map (fn [{:as ix :keys [name]
                         :or {name ix}}]
                     (reduce (fn [ix k]
                               (if-let [stage (get ix k)]
                                 (assoc ix k
                                        (ix/wrap stage
                                                 {:before (log-as [name k :start] opts)
                                                  :after (log-as [name k :end] opts)}))
                                 ix))
                             [:enter :leave :error]))))
         chain)))

#_(do
  ;; (prn :foo)
  (def inc-a {:enter (fn [ctx]
                       ;; (throw (ex-info "boom" {}))
                       (update ctx :a inc)
                       )
              :leave nil
              :error (fn [ctx err] ctx)})
  (def inc-b (assoc inc-a :name ::b))
  (def inc-c (assoc inc-a :name ::c))
  (ix/execute {:a 1}
              (-> [inc-a inc-b inc-c]
                  (with-log {:fmt (fn [ctx k]
                                    ;; (str (into [::timer] k))
                                    k
                                    ;; (str k "/timer:"
                                    ;;      (get-in ctx (into [::timer] k)))
                                    )})
                  (with-timer)

                  )))
