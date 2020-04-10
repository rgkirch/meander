(ns meander.dev.subst.zeta
  (:require [clojure.core :as clj]
            [meander.dev.kernel.zeta :as dev.kernel]
            [meander.dev.parse.zeta :as dev.parse]
            [meander.epsilon :as me]
            [meander.runtime.zeta :as m.runtime]))

(dev.kernel/defmodule generate-compile

  ;; :apply
  ;; ------

  [{:tag :apply :fn ?fn :pattern ?pattern} ?env]
  (`m.runtime/call ?fn (me/cata [?pattern ?env]))

  ;; :cat
  ;; ----

  [{:tag :cat :sequence [] :next ?next} ?env]
  (me/cata [?next ?env])

  [{:tag :cat :sequence [?x] :next ?next} ?env]
  (`m.runtime/call
   (`clj/fn [xs]
     (`clj/cons (`clj/nth xs 0) (`clj/nth xs 1)))
   (`m.runtime/pair
    (me/cata [?x ?env])
    (me/cata [?next ?env])))

  [{:tag :cat :sequence [?x & ?sequence] :next ?next} ?env]
  (`m.runtime/call
   (`clj/fn [xs]
     (`clj/cons (`clj/nth xs 0) (`clj/nth xs 1)))
   (`m.runtime/pair
    (me/cata [?x ?env])
    (me/cata [{:tag :cat :sequence ?sequence :next ?next} ?env])))

  ;; :cata
  ;; -----

  [{:tag :cata :pattern ?pattern} {:cata-symbol ?cata-symbol, :as ?env}]
  (`m.runtime/cata ?cata-symbol (me/cata [?pattern ?env]))

  ;; :entry
  ;; ------

  [{:tag :entry, :key-pattern ?key-pattern, :val-pattern ?val-pattern, :next ?next}
   ?env]
  (`m.runtime/call
   (`clj/fn [[m [k v]]] (assoc m k v))
   (`m.runtime/pair
    (me/cata [?next ?env])
    (`m.runtime/pair
     (me/cata [?key-pattern ?env])
     (me/cata [?val-pattern ?env]))))

  ;; :empty
  ;; ------

  [{:tag :empty} _]
  (`m.runtime/const [])

  ;; :fail
  ;; -----

  [{:tag :fail} _]
  `m.runtime/FAIL

  ;; :fold
  ;; -----

  [{:tag :fold
    :variable ?variable
    :initial-value {:form ?initial-value}
    :fold-function {:tag :host-expression
                    :form ?fold-function}}
   ?env]
  (`m.runtime/fold (me/cata [?variable ?env]) (me/cata ?initial-value) ?fold-function)

  ;; :host-expression
  ;; ----------------

  [{:tag :host-expression
    :expression ?expression}
   ?env]
  (`m.runtime/const ?expression)

  ;; :into
  ;; -----

  [{:tag :into, :memory-variable {:symbol ?symbol}} {?id ?symbol}]
  ;; Greedy star
  (`m.runtime/star (`m.runtime/call `clj/list ?id))

  [{:tag :into, :memory-variable {:symbol ?symbol}} ?env]
  ;; Greedy star
  (`m.runtime/star (`m.runtime/call `clj/list (`m.runtime/memory-variable ('quote ?symbol))))

  ;; :join
  ;; -----

  [{:tag :join :left ?left :right ?right} ?env]
  (`m.runtime/join (me/cata [?left ?env]) (me/cata [?right ?env]))

  ;; :let
  ;; ----

  [{:tag :let, :pattern ?pattern, :expression {:form ?expression,} :next ?next} ?env]
  (`m.runtime/letp (me/cata [?pattern ?env]) ?expression (me/cata [?next ?env]))

  ;; :literal
  ;; --------

  [{:tag :literal, :form ?form} _]
  (`m.runtime/const ('quote ?form))

  ;; :logic-variable
  ;; ---------------

  [{:tag :logic-variable :symbol ?symbol} {?id ?symbol}]
  ?id

  [{:tag :logic-variable :symbol ?symbol} _]
  (`m.runtime/logic-variable ('quote ?symbol))

  ;; :map
  ;; ----

  [{:tag :map :next ?next} ?env]
  (`m.runtime/call
   (`clj/fn [m]
    (`clj/into {} m))
   (me/cata [?next ?env]))

  (me/and [{:tag :entry, :key-pattern ?key, :val-pattern ?val, :next ?next} ?env]
          (me/let [?m (gensym)
                   ?k (gensym)
                   ?v (gensym)
                   ?e (gensym)
                   ?p (gensym)]))
  (`m.runtime/call
   (`clj/fn [?p]
    (`clj/let [?e (nth ?p 0)
               ?m (nth ?p 1)
               ?k (nth ?e 0)
               ?v (nth ?e 1)]
     (assoc ?m ?k ?v)))
   (`m.runtime/pair
    (`m.runtime/pair (me/cata [?key ?env]) (me/cata [?val ?env]))
    (me/cata [?next ?env])))

  ;; :memory-variable
  ;; ----------------

  [{:tag :memory-variable :symbol ?symbol} {?id ?symbol}]
  ?id

  [{:tag :memory-variable :symbol ?symbol} _]
  (`m.runtime/memory-variable ('quote ?symbol))

  ;; :mutable-variable
  ;; -----------------

  [{:tag :memory-variable :symbol ?symbol} {?id ?symbol}]
  ?id

  [{:tag :mutable-variable :symbol ?symbol} _]
  (`m.runtime/mutable-variable ('quote ?symbol))

  ;; :or
  ;; ---

  [{:tag :or, :left ?left, :right ?right} ?env]
  (`m.runtime/choice (me/cata [?left ?env]) (me/cata [?right ?env]))

  ;; :pass
  ;; -----

  [{:tag :pass} _]
  `m.runtime/PASS

  ;; :plus
  ;; -----

  [{:tag :plus :pattern ?pattern :next ?next :n ?n} ?env]
  (`m.runtime/join
   (`m.runtime/plus (me/cata [?pattern ?env]) ?n)
   (me/cata [?next ?env]))

  ;; :random-symbol
  ;; --------------

  [{:tag :random-symbol, :symbol ?symbol} ?env]
  (`m.runtime/random-symbol ('quote ?symbol))

  ;; :rest-map
  ;; ---------

  [{:tag :rest-map, :pattern ?pattern, :next ?next} ?env]
  (`m.runtime/call
   (`clj/fn [[x y]] (merge x y))
   (`m.runtime/pair
    (me/cata [?pattern ?env])
    (me/cata [?next ?env])))

  ;; :root
  ;; -----

  [{:tag :root :next ?next} ?env]
  (me/cata [?next ?env])

  ;; :seq
  ;; ----

  [{:tag :seq :next ?next} ?env]
  (`m.runtime/call `clojure.core/seq (me/cata [?next ?env]))

  ;; :some-map
  ;; ---------

  [{:tag :some-map} ?env]
  (`m.runtime/const {})

  ;; :star
  ;; -----

  [{:tag :star :pattern ?pattern :next ?next} ?env]
  (`m.runtime/join
   (`m.runtime/star (me/cata [?pattern ?env]))
   (me/cata [?next ?env]))

  ;; symbol
  ;; ------

  [{:tag :symbol :name (me/some ?name) :namespace (me/some ?namespace)} ?env]
  (`m.runtime/call (`clj/fn [[namespace name]] (`clj/symbol namespace name))
   [(me/cata [?namespace ?env])
    (me/cata [?name ?env])])

  [{:tag :symbol :name (me/some ?name)} ?env]
  (`m.runtime/call `clj/symbol (me/cata [?name ?env]))

  ;; :vector
  ;; -------

  [{:tag :vector :next ?next} ?env]
  (`m.runtime/call `clojure.core/vec (me/cata [?next ?env]))

  ;; Not implemented
  ;; ---------------

  ?x
  (throw (ex-info "No equation for" {:term ('quote ?x)})))