(ns exoscale.interceptor.protocols)

;; Async implementers should also impl. IDerefs

(defprotocol Async
  (then [x f])
  (catch [x f]))

(defn async?
  [x]
  (satisfies? Async x))

(defprotocol Interceptor
  (interceptor [x] "Produces an interceptor from value"))
