# Interceptor

The design is *heavily* inspired by
[pedestal.interceptor](http://pedestal.io/reference/interceptors) &
[sieparri](https://github.com/metosin/sieppari), both excellent libraries.
They are also quite opinionated, in ways that didn't fit our
usage and the core of the concept being around hundred lines we
decided to roll our own, with its own specificities.

It mimics pedestal interceptor behaviour, but adds async lib agnostic
support with implementations for
manifold/core.async/CompletableFuture. It differs from pedestal by
removing suppressed, terminators, not wrapping errors at every throw
level and not catching throwable.

If you're familiar with sieparri it differs by following pedestal
style error handling instead (modulo wrapping/rethrowing) and by not
having a :request/:response backed in, we just have context in ->
context out, or whatever you call as termination, from there we think
you can emulate other usage patterns more easily.

## How it works:

It follows the interceptor pattern.

`execute` takes a context (map) and chains it to the "interceptors"
that can modify it and ultimately returns a value or the context
itself.

An interceptor is just a map, with `:enter` key and potentially
`:leave` `:error` and `:name` (+ whatever you want to add).

### Stages

`:enter` and `:leave` are function of the context and return a new
context. `:error` takes 2 arguments, the context and the error that
triggered it and return potentially a new context.

When you execute it will pass the context to the `:enter` handlers first,
they return a new updated context; then the `:leave` handlers in reverse
(if present).

Something like that:

``` text
enter A -> enter B -> enter C -> leave C -> leave B -> leave A

```

### Errors

Upon encountering an error it will trigger the next `:error` handler
available, so either one on the interceptor that triggered it, if
there is one, or the next one on the stack. If you just return the
context from the `:error` handler it will resume with the execution of
the stack with that context; if you rethrow the error, or assoc it/one
under ::error (or via `(error ctx err)`) in the context and return it,
it will trigger the next error handler available on the stack.

The rule of thumb is to use error handlers to manage errors that
should trigger a termination of the processing and have control on the
flow of that termination (instantaneous return or propagating).  You
should still use normal error handling in your `:enter` handlers if
you want to let the normal flow of execution continue (ex if you need
to go to the next `:enter` handler).

This works exactly to what's defined in the original interceptor
pattern.

## Manifold, core.async, CompletableFuture support

You can mix steps that return deferreds/CompletableFutures/Channels
with normal steps like it will work transparently.

note: if you use the normal execute the first async interceptor in the
chain will dict the return value type (if it starts with a deferred
you get a deferred back). You can control the return values via
`exoscale.interceptor.manifold/execute-deferred`,
`exoscale.interceptor.core-async/execute-chan` and
`exoscale.interceptor.auspex/execute-future` and they still allow you
to mix internal steps with whatever lib you like/use.

## Helpers

You can apply lenses/guards on handlers via `in` `out` `lens` `when`
`discard` functions.

They are just middlewares to the handlers so you can apply them
separately to the handlers and have full control over their execution
order.

## Interceptors definition

You can define interceptors as maps as described earlier but we also
by default allow to specify them in other formats/types.

* Keyword -> {:enter k}
* Function -> {:enter f}
* Symbol -> resolved to anything that should implement the Interceptor protocol
* Var -> deref'ed to something that should implement the Interceptor protocol

## Usage


```clj
(def interceptor-A {:name :A
                    :enter (fn [ctx] (update ctx :a inc))
                    :leave (fn [ctx] (assoc ctx :foo :bar))
                    :error (fn [ctx err] ctx)})

(def interceptor-B {:name :B
                    :enter (fn [ctx] (update ctx :b inc))
                    :error (fn [ctx err] ctx)})

(def interceptor-C {:name :C
                    :enter (fn [ctx] (d/success-deferred (update ctx :c inc)))})

(def interceptor-D {:name :D
                    :enter (fn [ctx] (update ctx :d inc))})


(execute {:a 0 :b 0 :c 0 :d 0}
         [interceptor-A
          interceptor-B
          interceptor-C
          interceptor-D])

;; because we have an async step it will return a deferred
=> << {:a 1, :b 1, :c 1, :d 1, :foo :bar} >>

;; no async step, direct result
(execute {:a 0 :b 0 :d 0}
         [interceptor-A
          interceptor-B
          interceptor-D])


=> {:a 1, :b 1, :d 1, :foo :bar}

;; lens

(execute {:a 0}
         [{:name :foo :enter (lens inc [:a])}])
=> {:a 1}

;; same using in/out

(execute {:request 0}
         [{:name :foo
           :enter (-> inc
                      (in [:request])
                      (out [:response]))}])
=> {:request 0 :response 1}

;; guard/when

(execute {:a 0}
         [{:name :foo
           :enter (-> (fn [ctx] (update ctx :a inc))
                      (when #(contains? % :a)))}])
=> {:a 1}

;; discard output, just return ctx

(execute {:a 0}
         [{:name :foo
           :enter (-> (fn [ctx] (prn :yolo))
                      (discard))}])
=> {:a 0}

```
## Implementing custom interceptor types

Interceptors creation is behind a protocol so if you keep writing the
same "lenses" all the time you might be better of writing something
tailored to your needs.

For instance for an hypothetical interceptor type that would pull
from/to :request/:response in the context so you can just define them
as normal ring handlers.

``` clj
(defrecord RingInterceptor [enter]
  Interceptor
  (interceptor [{:as i :keys [enter]}]
    (cond-> i
      enter
      (assoc :enter (fn [ctx]
                      (assoc ctx :response (enter (:request ctx))))))))

;; then
(execute {:request {:params ...}} [(RingInterceptor. (fn [request] {:body "yolo"}))])
```

You can imagine holding state/dependencies at that level too if that's
something you desire (that's doable with context too).
## Api docs

[![cljdoc badge](https://cljdoc.xyz/badge/exoscale/interceptor)](https://cljdoc.xyz/d/exoscale/interceptor/CURRENT)

## Installation

[![Clojars Project](https://img.shields.io/clojars/v/exoscale/interceptor.svg)](https://clojars.org/exoscale/interceptor)
