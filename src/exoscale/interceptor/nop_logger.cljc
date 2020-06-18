(ns exoscale.interceptor.nop-logger
  "Nop logger for clojurescript")

;; same macros as clojure.tools.logging
(defmacro trace [& args])
(defmacro debug [& args])
(defmacro info [& args])
(defmacro warn [& args])
(defmacro error [& args])
(defmacro fatal [& args])

(defmacro tracef [& args])
(defmacro debugf [& args])
(defmacro infof [& args])
(defmacro warnf [& args])
(defmacro errorf [& args])
(defmacro fatalf [& args])

