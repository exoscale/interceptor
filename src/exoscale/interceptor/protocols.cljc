(ns exoscale.interceptor.protocols)

(defprotocol AsyncContext
  (then [x f])
  (catch [x f]))

(defn async?
  [x]
  (satisfies? AsyncContext x))

(defprotocol Interceptor
  (interceptor [x] "Produces an interceptor from value"))
