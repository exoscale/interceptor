(ns exoscale.interceptor.utils
  {:no-doc true})

(defmacro compile-when-available
  "Evaluate `exp` and if it returns logical true and doesn't error, expand to
  `then`.  Else expand to `else`.
  (compile-if (Class/forName \"java.util.concurrent.ForkJoinTask\")
    (do-cool-stuff-with-fork-join)
    (fall-back-to-executor-services))"
  {:style/indent 1}
  [ns & body]
  (when (try (nil? (require ns))
             (catch Exception _#))
    `(do ~@body)))

(defn exception?
  [e]
  (instance? #?(:clj Exception
                :cljs js/Error)
             e))
