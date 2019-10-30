# Interceptor

disclaimer: So far just week-end/evenings experiment.

The code/design is heavily inspired from pedestal/sieparri.

It mimics pedestal interceptor behavior, but adds async lib agnostic
support with a default manifold impl.

Some minor differences with pedestal:

* no nested exceptions when :error handlers bubble up, no error wrapping either

* potential support for cljs easily achievable

* tiny codebase

* less is more: no :suppressed, :bindings, :terminators

## How it works:

It follows the interceptor pattern.

`execute` takes a context (map) and chains it to the "interceptors" .

An interceptor is just a map, with `:enter` key and potentially
`:leave` `:error` and `:name` (+ whatever you want to add).

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

Upon encountering an error it will trigger the `:error` handler of the
interceptor that triggered it and if you rethrow or re-assoc the error
in the context it will let the error flow on the other :error handlers
in the stack.

## Manifold support

You can mix steps that return deferreds with normal steps like you
would do in chain it will work transparently.

note: if you use the normal execute the first interceptor will dict
the return value type (if it starts with a deferred you get a deferred
back). If you want to ensure you always get a deferred back no matter
what you can call `execute-deferred`

## Helpers

You can apply lenses/guards on handlers via `in` `out` `lens` `guard`
`discard` functions.

They are just middlewares to the handlers so you can apply them
separately to the handlers and have full control over their execution
order.

An interceptor can also just be a function. In that case it will just
be a step with a `:enter` key as itself.

## Usage


```clj
(def interceptor-A {:name :A
                    :enter (fn [ctx] (update ctx :a inc))
                    :leave (fn [ctx] (assoc ctx :foo :bar))
                    :error (fn [ctx err] (throw err))})

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

;; guard

(execute {:a 0}
         [{:name :foo
           :enter (-> (fn [ctx] (update ctx :a inc))
                      (guard #(contains? % :a)))}])
=> {:a 1}


```



## License

Copyright © 2019 FIXME

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
