(ns exoscale.interceptor
  (:refer-clojure :exclude [when])
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

(defn enqueue
  "Adds interceptors to current context"
  [ctx interceptors]
  (impl/enqueue ctx interceptors))

;;; helpers/middlewares

(defn wrap
  "Runs `before` before the stage function runs, then `after` before we
  return the new modified context"
  [f {:keys [before after]
      :or {before identity
           after identity}}]
  (fn
    ([ctx]
     (let [x (f (before ctx))]
       (if (p/async? x)
         (p/then x #(after %))
         (after x))))
    ([ctx err]
     (let [x (f (before ctx) err)]
       (if (p/async? x)
         (p/then x #(after %))
         (after x))))))

(defn before
  "Wraps stage fn with another one"
  [f before-f]
  (wrap f {:before before-f}))

(defn after
  "Modifies context after stage function ran"
  [f before-f]
  (wrap f {:after before-f}))

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
