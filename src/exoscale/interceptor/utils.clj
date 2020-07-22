(ns exoscale.interceptor.utils
  "Terrible namespace name, to be changed"
  (:require [exoscale.interceptor :as ix]
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

(comment
  (def inc-a (fn [ctx] (update ctx :a inc)))
  (def inc-b #'inc-a)
  (def inc-c #'inc-a)
  (ix/execute {:a 1} (with-timer [inc-a inc-b inc-c])))
