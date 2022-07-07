(ns exoscale.interceptor
  (:refer-clojure :exclude [when remove])
  (:require [exoscale.interceptor.impl :as impl]
            [exoscale.interceptor.protocols :as p]))

;;; API

(defn execute
  "Executes a queue of Interceptors attached to the context. Context
  must be a map. Interceptors are added with
  `exoscale.interceptor/enqueue`.

  An Interceptor is a map or map-like object with the keys `:enter`,
  `:leave`, and `:error`. The value of each key is a function; missing
  keys or nil values are ignored. When executing a context, first all
  the `:enter` functions are invoked in order. As this happens, the
  Interceptors are pushed onto a stack.

  When execution reaches the end of the queue, it begins popping
  Interceptors off the stack and calling their `:leave` functions.
  Therefore `:leave` functions are called in the opposite order from
  `:enter` functions.

  Both the `:enter` and :leave functions are called on a single
  argument, the context map, and return an updated context.

  If any Interceptor function throws an exception, execution stops and
  begins popping Interceptors off the stack and calling their `:error`
  functions. The `:error` function takes two arguments: the context
  and the error triggering the call. If you rethrow the error or assoc
  it into the context under `:exoscale.interceptor/error` (or via the
  `exoscale.inceptor/error` fn) other :error handlers will be
  triggered from the stack, if you just return the context it will
  resume triggering `:leave` functions.

  If the `:error` reaches the end of the stack without being handled,
  execute will throw it."
  ([ctx interceptors]
   (execute (impl/enqueue ctx interceptors)))
  ([ctx]
   (impl/execute ctx identity #(throw %))))

;;; Error handling

(defn error
  "Adds error to context, potentially triggering `:error` stage on
  current/next interceptor"
  [ctx error]
  (assoc ctx ::error error))

;;; queue/stack manipulation

(defn terminate
  "Removes all remaining interceptors from context's execution queue.
  This effectively short-circuits execution of Interceptors' `:enter`
  functions and begins executing the `:leave` functions."
  [ctx]
  (assoc ctx ::queue nil))

(defn halt
  "Removes all remaining interceptors from context's execution queue and stack.
  This effectively short-circuits execution of
  Interceptors' `:enter`/`:leave` and returns the context"
  [ctx]
  (assoc ctx
         ::queue nil
         ::stack nil))

(defn xform-stack
  "Takes a context from execution and run xf (transducing fn) on stack, returns a
  new context "
  [ctx xf]
  (update ctx ::stack #(into (empty %) xf %)))

(defn xform-queue
  "Takes a context from execution and run xf (transducing fn) on queue, returns a
  new context "
  [ctx xf]
  (update ctx ::queue #(into (empty %) xf %)))

(defn xform
  "Takes a context from execution and run xf (transducing fn) on stack/queue,
  returns a new context "
  [ctx xf]
  (-> ctx
      (xform-queue xf)
      (xform-stack xf)))

(defn remove
  "Remove all interceptors matching predicate from stack/queue, returns context"
  [ctx pred]
  (xform ctx (clojure.core/remove pred)))

(defn enqueue
  "Adds interceptors to current context"
  [ctx interceptors]
  (impl/enqueue ctx interceptors))

;;; helpers/middlewares

(defn transform
  "Takes stage function, and wraps it with callback that will return a
  new context from the application of `f'` onto it. It can be useful
  to run a separate function after a stage returns and apply some
  transformation to it relative to the original context. `f'` takes
  the *initial* stage context and `f` realized return value as
  arguments."
  [f f']
  (fn [ctx]
    (let [x (f ctx)]
      (if (p/async? x)
        (p/then x #(f' ctx %))
        (f' ctx x)))))

(defn in
  "Modifies interceptor stage to *take in* specified path"
  [f path]
  (fn [ctx]
    (f (get-in ctx path))))

(defn out
  "Modifies interceptor stage to *return at* specified path"
  [f path]
  (transform f #(assoc-in %1 path %2)))

(defn when
  "Modifies interceptor stage to only run on ctx if pred returns true'ish"
  [f pred]
  (fn [ctx]
    (cond-> ctx
      (pred ctx)
      f)))

(defn lens
  "Modifies interceptor stage to take from path and return to path"
  [f path]
  (-> f
      (in path)
      (out path)))

(defn discard
  "Run function for side-effects only and return context"
  [f]
  (transform f (fn [ctx _] ctx)))

;;; stage middlewares

(defn before-stage
  "Wraps stage fn with another one, basically a middleware that will be run before
  a stage"
  [f before-f]
  (fn
    ([ctx] (f (before-f ctx)))
    ([ctx err] (f (before-f ctx err) err))))

(defn after-stage
  "Modifies context after stage function ran"
  [f after-f]
  (fn
    ([ctx]
     (let [ctx (f ctx)]
       (if (p/async? ctx)
         (p/then ctx #(after-f %))
         (after-f ctx))))
    ([ctx err]
     (let [ctx (f ctx err)]
       (if (p/async? ctx)
         (p/then ctx #(after-f % err))
         (after-f ctx err))))))

(defn into-stages
  "Applies fn `f` on all `stages` (collection of :enter, :leave and/or :error) of
  `chain`. This provides a way to apply middlewares to an entire interceptor
  chain at definition time.

  Useful when used in conjunction with, `after-stage`, `before-stage`.

  `f` will be a function of a `stage` function, such as the ones returned by
  `before-stage`, `after-stage` and an `execution context`. The stage function
  is a normal interceptor stage function, taking 1 or 2 args depending on stage
  of execution (enter/leave or error, error taking 2 args), can potentially be
  multi-arg if it has to be used for all stage types. The execution context is a
  map that will contain an `:interceptor` key with the value for the current
  interceptor and `:stage` to indicate which stage we're at (enter, leave or
  error).

  `(into-stages [...] [:enter :error] (fn [stage-f execution-ctx] (after-stage stage-f (fn [...] ...))))"
  [chain stages f]
  (into []
        (comp (map p/interceptor)
              (map (fn [ix]
                     (reduce (fn [ix k]
                               (if-let [stage (get ix k)]
                                 (assoc ix
                                        k
                                        (f stage {:interceptor ix :stage k}))
                                 ix))
                             ix
                             stages))))
        chain))
