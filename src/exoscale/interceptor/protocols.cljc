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

(extend-protocol Interceptor
  clojure.lang.IPersistentMap
  (interceptor [m] m)

  clojure.lang.IRecord
  (interceptor [r] r)

  clojure.lang.Fn
  (interceptor [f]
    {:enter f})

  clojure.lang.Keyword
  (interceptor [f]
    {:enter f}))
