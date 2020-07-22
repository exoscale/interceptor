(ns exoscale.interceptor.utils
  "Terrible namespace name, to be changed"
  (:require [exoscale.interceptor :as ix]
            [clojure.tools.logging :as log]
            [exoscale.interceptor.protocols :as p]))

(defn- time-as
  [k]
  (fn [ctx] (assoc ctx k (java.time.Instant/now))))

(defn with-timer
  "Wrap every stage on every interceptor in a chain with timers"
  [chain]
  (into []
        (comp (map p/interceptor)
              (map (fn [{:as ix :keys [enter leave error name]
                         :or {name ix}}]
                     (cond-> ix
                       (ifn? enter)
                       (assoc :enter
                              (ix/wrap enter
                                       {:before (time-as [name ::enter ::start])
                                        :after (time-as [name ::enter ::end])}))

                       (ifn? leave)
                       (assoc :leave
                              (ix/wrap leave
                                       {:before (time-as [name ::leave ::start])
                                        :after (time-as [name ::leave ::end])}))

                       (ifn? error)
                       (assoc :error
                              (ix/wrap error
                                       {:before (time-as [name ::error ::start])
                                        :after (time-as [name ::error ::end])}))))))
        chain))

(defn- log-as
  [k {:as _opts :keys [level fmt]
      :or {level :debug
           fmt (fn [ctx k] k)}}]
  (fn [ctx]
    (log/ level (fmt ctx k))
    ctx))

(defn with-log
  ([chain] (with-log chain {}))
  ([chain opts]
   (into []
         (comp (map p/interceptor)
               (map (fn [{:as ix :keys [enter leave error name]
                          :or {name ix}}]
                      (cond-> ix
                        (ifn? enter)
                        (assoc :enter
                               (ix/wrap enter
                                        {:before (log-as [name ::enter] opts)
                                         :after (log-as [name ::enter] opts)}))

                        (ifn? leave)
                        (assoc :leave
                               (ix/wrap leave
                                        {:before (log-as [name ::leave] opts)
                                         :after (log-as [name ::leave] opts)}))

                        (ifn? error)
                        (assoc :error
                               (ix/wrap error
                                        {:before (log-as [name ::error] opts)
                                         :after (log-as [name ::error] opts)}))))))
         chain)))

(do
  (def inc-a (fn [ctx] (update ctx :a inc)))
  (def inc-b #'inc-a)
  (def inc-c #'inc-a)
  (prn  (ix/execute {:a 1} (-> [inc-a inc-b inc-c]
                               (with-timer)
                               (with-log)))))
