(ns exoscale.interceptor.utils)

(defmacro compile-when-available
  "Evaluate `exp` and if it returns logical true and doesn't error, expand to
  `then`.  Else expand to `else`.
  (compile-if (Class/forName \"java.util.concurrent.ForkJoinTask\")
    (do-cool-stuff-with-fork-join)
    (fall-back-to-executor-services))"
  [ns & body]
  (when (try (nil? (require ns))
             (catch Exception _#))
    `(do ~@body)))
