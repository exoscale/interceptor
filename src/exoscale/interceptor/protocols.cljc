(ns exoscale.interceptor.protocols)

(defprotocol AsyncContext
  (then [x f])
  (catch [x f]))

(defprotocol InterceptorContext
  (async? [x]))

;; preserve the same behaviour as 0.1.14 (before we introduced InterceptorContext)
(extend-protocol InterceptorContext
  nil
  (async? [_] false))

(defprotocol Interceptor
  (interceptor [x] "Produces an interceptor from value"))

(extend-protocol InterceptorContext
  ;; we extend all Objects including Deferred, core.async channels and CompletableFuture
  ;; but when their respective namespaces load, they will call extend-protocol
  ;; thus implementing async? for their respective classes
  #?(:clj Object :cljs object)
  (async? [_] false))
