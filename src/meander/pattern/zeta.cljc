(ns meander.pattern.zeta
  (:refer-clojure :exclude [apply
                            assoc
                            merge
                            some])
  #?(:clj
     (:require [clojure.core :as clojure]
               [meander.algorithms.zeta :as m.algorithms])
     :cljs
     (:require [cljs.core :as clojure]
               [meander.algorithms.zeta :as m.algorithms])))

;; Environment helpers
;; ---------------------------------------------------------------------

(defn set-sequential [environment pattern]
  (clojure/assoc environment [pattern sequential?] true))

(defn set-associative [environment pattern]
  (clojure/assoc environment [pattern associative?] true))

(defn host-associative-predicate [environment pattern]
  (let [host (get environment :eval)]
    (if (true? (get environment [pattern associative?]))
      (host `clojure/any?)
      (host `clojure/associative?))))

(defn host-sequential-predicate [environment pattern]
  (let [host (get environment :eval)]
    (if (true? (get environment [pattern sequential?]))
      (host `clojure/any?)
      (host `clojure/sequential?))))

(defn host-coll-predicate [environment pattern]
  (let [host (get environment :eval)]
    (if (or (true? (get environment [pattern coll?]))
            (true? (get environment [pattern sequential?])))
      (host `clojure/any?)
      (host `clojure/coll?))))

;; Protocols
;; ---------------------------------------------------------------------

(defprotocol IQueryFunction
  (query-function [this kernel]))

(defprotocol IYieldFunction
  (yield-function [this kernel]))

;; Classes
;; ---------------------------------------------------------------------

(defrecord Anything []
  IQueryFunction
  (query-function [this kernel]
    (fn [pass fail state]
      (pass state)))

  IYieldFunction
  (yield-function [this kernel]
    (let [give (get kernel :give)
          make (get kernel :make)]
      (fn [pass fail state]
        (give state (make state) pass)))))

(defrecord Nothing []
  IQueryFunction
  (query-function [this kernel]
    (fn [pass fail state]
      (fail state)))

  IYieldFunction
  (yield-function [this kernel]
    (fn [pass fail state]
      (fail state))))

(defrecord Data [value]
  IQueryFunction
  (query-function [this kernel]
    (let [call (get kernel :call)
          data (get kernel :data)
          host (get kernel :eval)
          take (get kernel :take)
          test (get kernel :test)
          data-value (data value)
          host-equal (host `=)]
      (fn [pass fail state]
        (take state
          (fn [object]
            (call host-equal object data-value
              (fn [truth]
                (test truth
                  (fn [] (pass state))
                  (fn [] (fail state))))))))))

  IYieldFunction
  (yield-function [this kernel]
    (let [data (get kernel :data)
          give (get kernel :give)
          data-value (data value)]
      (fn [pass fail state]
        (give state data-value pass)))))

(defrecord Host [form]
  IQueryFunction
  (query-function [this kernel]
    (let [call (get kernel :call)
          data (get kernel :data)
          host (get kernel :eval)
          take (get kernel :take)
          test (get kernel :test)
          host-equal (host `clojure/=)]
      (fn [pass fail state]
        (take state
          (fn [object]
            (call host-equal object (host form)
              (fn [truth]
                (test truth
                  (fn [] (pass state))
                  (fn [] (fail state))))))))))

  IYieldFunction
  (yield-function [this kernel]
    (let [give (get kernel :give)
          host (get kernel :eval)]
      (fn [pass fail state]
        (give state (host form) pass)))))

(defrecord Variable [symbol fold-function unfold-function]
  IQueryFunction
  (query-function [this kernel]
    (let [save (get kernel :save)
          fold (fold-function kernel)]
      (fn [pass fail state]
        (save state symbol fold pass fail))))

  IYieldFunction
  (yield-function [this kernel]
    (let [load (get kernel :load)
          unfold (unfold-function kernel)]
      (fn [pass fail state]
        (load state symbol unfold pass fail)))))

(defrecord Pick [pattern-a pattern-b]
  IQueryFunction
  (query-function [this kernel]
    (let [query-a (query-function pattern-a kernel)
          query-b (query-function pattern-b kernel)]
      (fn [pass fail state]
        (query-a pass (fn [_] (query-b pass fail state)) state))))

  IYieldFunction
  (yield-function [this kernel]
    (let [yield-a (yield-function pattern-a kernel)
          yield-b (yield-function pattern-b kernel)]
      (fn [pass fail state]
        (yield-a pass (fn [_] (yield-b pass fail state)) state)))))

(defrecord Some [pattern-a pattern-b]
  IQueryFunction
  (query-function [this kernel]
    (let [query-a (query-function pattern-a kernel)
          query-b (query-function pattern-b kernel)
          join (get kernel :join)]
      (fn [pass fail state]
        (join (fn [] (query-a pass fail state))
              (fn [] (query-b pass fail state))))))

  IYieldFunction
  (yield-function [this kernel]
    (let [yield-a (yield-function pattern-a kernel)
          yield-b (yield-function pattern-b kernel)
          join (get kernel :join)]
      (fn [pass fail state]
        (join (fn [] (yield-a pass fail state))
              (fn [] (yield-b pass fail state)))))))

(defrecord Each [pattern-a pattern-b]
  IQueryFunction
  (query-function [this kernel]
    (let [query-a (query-function pattern-a kernel)
          query-b (query-function pattern-b kernel)]
      (fn [pass fail state]
        (query-a (fn [state] (query-b pass fail state)) fail state))))

  IYieldFunction
  (yield-function [this kernel]
    (let [join (get kernel :join)
          yield-a (yield-function pattern-a kernel)
          yield-b (yield-function pattern-b kernel)
          query-a (query-function pattern-a kernel)
          query-b (query-function pattern-b kernel)]
      (fn [pass fail state]
        (join (fn [] (yield-a (fn [state] (query-b pass fail state)) fail state))
              (fn [] (yield-b (fn [state] (query-a pass fail state)) fail state)))))))

(defrecord Apply [function-pattern arguments-pattern return-pattern]
  IQueryFunction
  (query-function [this kernel]
    (let [call (get kernel :call)
          host (get kernel :eval)
          pass (get kernel :pass)
          give (get kernel :give)
          take (get kernel :take)
          host-apply (host `clojure/apply)
          function-yield (yield-function function-pattern kernel)
          arguments-yield (yield-function arguments-pattern kernel)
          return-query (query-function return-pattern kernel)]
      (fn [pass fail state]
        (take state
          (fn [object]
            (function-yield
             (fn [function-state]
               (take function-state
                 (fn [function]
                   (arguments-yield
                    (fn [arguments-state]
                      (take arguments-state
                        (fn [arguments]
                          (call host-apply function object arguments
                            (fn [return-object]
                              (give state return-object
                                (fn [return-state]
                                  (return-query (fn [return-query-state]
                                                  (give return-query-state return-object pass))
                                                fail
                                                return-state))))))))
                    fail
                    state))))
             fail
             state))))))

  IYieldFunction
  (yield-function [this kernel]
    (let [call (get kernel :call)
          host (get kernel :eval)
          fail (get kernel :fail)
          give (get kernel :give)
          pass (get kernel :pass)
          take (get kernel :take)
          test (get kernel :test)
          host-apply (host `clojure/apply)
          host-seqable? (host `clojure/seqable?)
          host-fn? (host `clojure/fn?)
          function-yield (yield-function function-pattern kernel)
          arguments-yield (yield-function arguments-pattern kernel)
          return-query (query-function return-pattern kernel)]
      (fn [pass fail state]
        (function-yield
         (fn [function-state]
           (take function-state
             (fn [function-object]
               (call host-fn? function-object
                 (fn [truth]
                   (test truth
                     (fn []
                       (arguments-yield
                        (fn [arguments-state]
                          (take arguments-state
                            (fn [arguments-object]
                              (call host-seqable? arguments-object
                                (fn [truth]
                                  (test truth
                                    (fn []
                                      (call host-apply function-object arguments-object
                                        (fn [return-object]
                                          (give arguments-state return-object
                                            (fn [return-state]
                                              (return-query pass fail return-state))))))
                                    (fn []
                                      (fail state))))))))
                        fail
                        state))
                     (fn []
                       (fail state))))))))
         fail
         state)))))

(defrecord Predicate [predicate-pattern x-pattern]
  IQueryFunction
  (query-function [this kernel]
    (let [call (get kernel :call)
          fail (get kernel :fail)
          pass (get kernel :pass)
          take (get kernel :take)
          test (get kernel :test)
          predicate-yield (yield-function predicate-pattern kernel)
          x-query (query-function x-pattern kernel)]
      (fn [pass fail state]
        (take state
          (fn [object]
            (predicate-yield
             (fn [predicate-state]
               (take predicate-state
                 (fn [predicate-object]
                   (call predicate-object object
                     (fn [truth]
                       (test truth 
                         (fn [] (x-query pass fail state))
                         (fn [] (fail state))))))))
             fail
             state))))))

  IYieldFunction
  (yield-function [this kernel]
    (let [call (get kernel :call)
          fail (get kernel :fail)
          pass (get kernel :pass)
          take (get kernel :take)
          test (get kernel :test)
          predicate-yield (yield-function predicate-pattern kernel)
          x-yield (yield-function x-pattern kernel)]
      (fn [pass fail state]
        (predicate-yield
         (fn [predicate-state]
           (take predicate-state
             (fn [predicate-object]
               (x-yield
                (fn [x-state]
                  (take x-state
                    (fn [x]
                      (call predicate-object x
                        (fn [truth]
                          (test truth 
                            (fn [] (pass x-state))
                            (fn [] (fail x-state))))))))
                fail
                predicate-state))))
         fail
         state)))))

(defrecord Project [a-pattern b-pattern c-pattern]
  IQueryFunction
  (query-function [this kernel]
    (let [give (get kernel :give)
          pass (get kernel :pass)
          take (get kernel :take)
          a-yield (yield-function a-pattern kernel)
          b-query (query-function b-pattern kernel)
          c-query (query-function c-pattern kernel)]
     (fn [pass fail state]
        (take state
          (fn [object]
            (a-yield
             (fn [a-state]
               (take a-state
                 (fn [a-object]
                   (give state a-object
                     (fn [state]
                       (b-query (fn [b-state]
                                  (give b-state object
                                    (fn [state]
                                      (c-query pass fail state))))
                                fail
                                state))))))
             fail
             state))))))

  IYieldFunction
  (yield-function [this kernel]
    (let [give (get kernel :give)
          take (get kernel :take)
          a-yield (yield-function a-pattern kernel)
          b-query (query-function b-pattern kernel)
          c-yield (yield-function c-pattern kernel)]
      (fn [pass fail state]
        (a-yield
         (fn [a-state]
           (take a-state
             (fn [a-object]
               (give state a-object
                 (fn [state]
                   (b-query
                    (fn [b-state]
                      (c-yield pass fail b-state))
                    fail
                    state))))))
         fail
         state)))))

;; Regular Expression Patterns
;; ---------------------------------------------------------------------

(defrecord RegexEmpty []
  IQueryFunction
  (query-function [this kernel]
    (let [call (get kernel :call)
          host (get kernel :eval)
          take (get kernel :take)
          test (get kernel :test)
          host-seq (host `clojure/seq)
          host-sequential? (host-sequential-predicate kernel this)]
      (fn [pass fail state]
        (take state
          (fn [object]
            (call host-sequential? object
              (fn [truth]
                (test truth
                  (fn []
                    (call host-seq object
                      (fn [truth]
                        (test truth 
                          (fn [] (fail state))
                          (fn [] (pass state))))))
                  (fn []
                    (fail state))))))))))

  IYieldFunction
  (yield-function [this kernel]
    (let [data (get kernel :data)
          give (get kernel :give)
          empty-vector (data [])]
      (fn [pass fail state]
        (give state empty-vector pass)))))

(defrecord RegexCons [head-pattern tail-pattern]
  IQueryFunction
  (query-function [this kernel]
    (let [call (get kernel :call)
          data (get kernel :data)
          host (get kernel :eval)
          give (get kernel :give)
          take (get kernel :take)
          test (get kernel :test)
          head-query (query-function head-pattern kernel)
          tail-query (query-function tail-pattern (set-sequential kernel tail-pattern))
          host-nth (host `clojure/nth)
          host-tail (host `m.algorithms/tail)
          host-seq (host `clojure/seq)
          host-sequential? (host-sequential-predicate kernel this)
          data-0 (data 0)]
      (fn [pass fail state]
        (take state
          (fn [object]
            (call host-sequential? object
              (fn [truth]
                (test truth
                  (fn []
                    (call host-seq object
                      (fn [truth]
                        (test truth 
                          (fn []
                            (call host-nth object data-0
                              (fn [head-object]
                                (call host-tail object
                                  (fn [tail-object]
                                    (give state head-object
                                      (fn [state]
                                        (head-query (fn [head-state]
                                                      (give head-state tail-object
                                                        (fn [state]
                                                          (tail-query pass fail state))))
                                                    fail
                                                    state))))))))
                          (fn []
                            (fail state))))))
                  (fn []
                    (fail state))))))))))


  IYieldFunction
  (yield-function [this kernel]
    (let [call (get kernel :call)
          host (get kernel :eval)
          give (get kernel :give)
          take (get kernel :take)
          test (get kernel :test)
          head-yield (yield-function head-pattern kernel)
          tail-yield (yield-function tail-pattern kernel)
          host-cons (host `clojure/cons)
          tail-sequential? (host-sequential-predicate kernel tail-pattern)]
      (fn [pass fail state]
        (head-yield
         (fn [head-state]
           (take head-state
             (fn [head-object]
               (tail-yield
                (fn [tail-state]
                  (take tail-state
                    (fn [tail-object]
                      (call tail-sequential? tail-object
                        (fn [truth]
                          (test truth
                            (fn []
                              (call host-cons head-object tail-object
                                (fn [sequential-object]
                                  (give tail-state sequential-object pass))))
                            (fn []
                              (fail tail-state))))))))
                fail
                head-state))))
         fail
         state)))))

(defrecord RegexConcatenation [initial-patterns tail-pattern]
  IQueryFunction
  (query-function [this kernel]
    (let [bind (get kernel :bind)
          call (get kernel :call)
          data (get kernel :data)
          host (get kernel :eval)
          give (get kernel :give)
          take (get kernel :take)
          test (get kernel :test)
          pass (get kernel :pass)
          initial-queries (map (fn [pattern]
                                 (query-function pattern kernel))
                               initial-patterns)
          indexed-queries (map-indexed (fn [index query]
                                         [(data index) query])
                                       initial-queries)
          tail-query (query-function tail-pattern (set-sequential kernel tail-pattern))
          n* (count initial-patterns)
          m* (inc n*)
          n (data n*)
          m (data m*)
          <= (host `clojure/<=)
          nth (host `clojure/nth)
          bounded-count (host `clojure/bounded-count)
          drop (host `m.algorithms/drop)
          sequential? (host-sequential-predicate kernel this)]
      (fn [resolve reject state]
        (take state
          (fn [object]
            (call sequential? object
              (fn [truth]
                (test truth 
                  (fn []
                    (call bounded-count m object
                      (fn [m]
                        (call <= n m
                          (fn [truth]
                            (test truth 
                              (fn []
                                (call drop n object
                                  (fn [tail]
                                    (bind (fn [state]
                                            (give state tail
                                              (fn [state]
                                                (tail-query resolve reject state))))
                                          (reduce
                                           (fn [ma [index query]]
                                             (call nth object index
                                               (fn [x]
                                                 (bind (fn [state]
                                                         (give state x
                                                           (fn [state]
                                                             (query pass reject state))))
                                                       ma))))
                                           (pass state)
                                           indexed-queries)))))
                              (fn []
                                (reject state))))))))
                  (fn []
                    (reject state))))))))))

  IYieldFunction
  (yield-function [this kernel]
    (let [bind (get kernel :bind)
          call (get kernel :call)
          data (get kernel :data)
          host (get kernel :eval)
          give (get kernel :give)
          take (get kernel :take)
          pass (get kernel :pass)
          initial-yields (map (fn [pattern]
                                (yield-function pattern kernel))
                              initial-patterns)
          tail-yield (yield-function tail-pattern kernel)
          empty-vector (data [])
          conj (host `clojure/conj)
          concat (host `clojure/concat)]
      (fn [resolve reject state]
        (bind (fn [xs-state]
                (take xs-state
                  (fn [xs]
                    (tail-yield (fn [ys-state]
                                  (take ys-state
                                    (fn [ys]
                                      (call concat xs ys
                                        (fn [xs+ys]
                                          (give ys-state xs+ys resolve))))))
                                reject
                                xs-state))))
              (reduce
               (fn [m x-yield]
                 (bind (fn [state]
                         (take state
                           (fn [xs]
                             (x-yield (fn [x-state]
                                        (take x-state
                                          (fn [x]
                                            (call conj xs x
                                              (fn [xs+x]
                                                (give x-state xs+x pass))))))
                                      reject
                                      state))))
                       m))
               (give state empty-vector pass)
               initial-yields))))))

(defrecord RegexJoin [x-pattern y-pattern]
  IQueryFunction
  (query-function [this kernel]
    (let [bind (get kernel :bind)
          call (get kernel :call)
          data (get kernel :data)
          host (get kernel :eval)
          give (get kernel :give)
          test (get kernel :test)
          take (get kernel :take)
          scan (get kernel :scan)
          x-query (query-function x-pattern (set-sequential kernel x-pattern))
          y-query (query-function y-pattern (set-sequential kernel y-pattern))
          nth (host `clojure/nth)
          sequential? (host-sequential-predicate kernel this)
          partitions (host `meander.algorithms.zeta/partitions)
          zero (data 0)
          one (data 1)
          two (data 2)]
      (fn [resolve reject state]
        (take state
          (fn [object]
            (call sequential? object
              (fn [truth]
                (test truth
                  (fn []
                    (call partitions two object
                      (fn [partitions]
                        (scan (fn [partition]
                                (call nth partition zero
                                  (fn [x]
                                    (call nth partition one
                                      (fn [y]
                                        (give state x
                                          (fn [state]
                                            (x-query (fn [x-state]
                                                       (give x-state y
                                                         (fn [x-state]
                                                           (y-query resolve reject x-state))))
                                                     reject
                                                     state))))))))
                              partitions))))
                  (fn []
                    (reject state))))))))))

  IYieldFunction
  (yield-function [this kernel]
    (let [bind (get kernel :bind)
          call (get kernel :call)
          host (get kernel :eval)
          give (get kernel :give)
          give (get kernel :give)
          take (get kernel :take)
          test (get kernel :test)
          x-yield (yield-function x-pattern kernel)
          y-yield (yield-function y-pattern kernel)
          concat (host `clojure/concat)
          x-coll? (host-coll-predicate kernel x-pattern)
          y-coll? (host-coll-predicate kernel y-pattern)]
      (fn [resolve reject state]
        (x-yield
         (fn [x-state]
           (take x-state
             (fn [x]
               (call x-coll? x
                 (fn [truth]
                   (test truth
                     (fn []
                       (y-yield (fn [y-state]
                                  (take y-state
                                    (fn [y]
                                      (call y-coll? y
                                        (fn [truth]
                                          (test truth
                                            (fn []
                                              (call concat x y
                                                (fn [xy]
                                                  (give y-state xy resolve))))
                                            (fn []
                                              (reject y-state))))))))
                                reject
                                x-state))
                     (fn []
                       (reject x-state))))))))
         reject
         state)))))

(defrecord GreedyStar [subsequence-pattern tail-pattern]
  IQueryFunction
  (query-function [this environment]
    (let [bind (get environment :bind)
          call (get environment :call)
          data (get environment :data)
          host (get environment :eval)
          fail (get environment :fail)
          give (get environment :give)
          pass (get environment :pass)
          pick (get environment :pick)
          scan (get environment :scan)
          star (get environment :star)
          take (get environment :take)
          test (get environment :test)
          subsequence-query (query-function subsequence-pattern (set-sequential environment subsequence-pattern))
          tail-query (query-function tail-pattern (set-sequential environment tail-pattern))
          partitions (host `m.algorithms/partitions)
          nth (host `clojure/nth)
          rest (host `clojure/rest)
          seq (host `clojure/seq)
          sequential? (host-sequential-predicate environment this)
          zero (data 0)
          one (data 1)
          two (data 2)]
      (fn [resolve reject state]
        (take state
          (fn [object]
            (call sequential? object
              (fn [truth]
                (test truth
                  (fn []
                    (star state
                      (fn [rec state]
                        (take state
                          (fn [object]
                            (pick (fn []
                                    (call partitions two object
                                      (fn [partitions]
                                        (call rest partitions
                                          (fn [tail-partitions]
                                            (scan (fn [partition]
                                                    (call nth partition zero
                                                      (fn [a]
                                                        (call nth partition one
                                                          (fn [b]
                                                            (bind (fn [subsequence-state]
                                                                    (give subsequence-state b
                                                                      (fn [state]
                                                                        (call rec state identity))))
                                                                  (give state a
                                                                    (fn [a-state]
                                                                      (subsequence-query pass fail a-state)))))))))
                                                  tail-partitions))))))
                                  (fn []
                                    (tail-query resolve reject state))))))))
                  (fn []
                    (fail state))))))))))

  IYieldFunction
  (yield-function [this environment]
    (let [bind (get environment :bind)
          call (get environment :call)
          data (get environment :data)
          host (get environment :eval)
          fail (get environment :fail)
          give (get environment :give)
          pass (get environment :pass)
          pick (get environment :pick)
          star (get environment :star)
          take (get environment :take)
          test (get environment :test)
          subsequence-yield (yield-function subsequence-pattern environment)
          tail-yield (yield-function tail-pattern environment)
          concat (host `clojure/concat)
          subsequence-sequential? (host-sequential-predicate environment subsequence-pattern)
          tail-sequential? (host-sequential-predicate environment tail-pattern)
          empty-list (data ())]
      (fn [resolve reject state]
        (give state empty-list
          (fn [initial-state]
            (star initial-state
              (fn [rec state]
                    (take state
                      (fn [a]
                        (pick (fn []
                                (subsequence-yield
                                 (fn [subsequence-state]
                                   (take subsequence-state
                                     (fn [b]
                                       (call subsequence-sequential? b
                                         (fn [truth]
                                           (test truth
                                             (fn []
                                               (call concat a b
                                                 (fn [ab]
                                                   (give subsequence-state ab
                                                     (fn [state]
                                                       (call rec state identity))))))
                                             (fn []
                                               (reject state))))))))
                                 reject
                                 state))
                              (fn []
                                (tail-yield (fn [tail-state]
                                              (take tail-state
                                                (fn [b]
                                                  (call tail-sequential? b
                                                    (fn [truth]
                                                      (test truth
                                                        (fn []
                                                          (call concat a b
                                                            (fn [ab]
                                                              (give tail-state ab resolve))))
                                                        (fn []
                                                          (reject tail-state))))))))
                                            reject
                                            state)))))))))))))

(defrecord FrugalStar [subsequence-pattern tail-pattern]
  IQueryFunction
  (query-function [this environment]
    (let [bind (get environment :bind)
          call (get environment :call)
          data (get environment :data)
          host (get environment :eval)
          fail (get environment :fail)
          give (get environment :give)
          join (get environment :join)
          pass (get environment :pass)
          scan (get environment :scan)
          star (get environment :star)
          take (get environment :take)
          test (get environment :test)
          subsequence-query (query-function subsequence-pattern environment)
          tail-query (query-function tail-pattern environment)
          partitions (host `m.algorithms/partitions)
          nth (host `clojure/nth)
          rest (host `clojure/rest)
          seq (host `clojure/seq)
          sequential? (host-sequential-predicate environment this)
          identity (host `clojure/identity)
          zero (data 0)
          one (data 1)
          two (data 2)]
      (fn [resolve reject state]
        (take state
          (fn [object]
            (call sequential? object
              (fn [truth]
                (test truth
                  (fn []
                    (star state
                      (fn [rec state]
                        (take state
                          (fn [object]
                            (call seq object
                              (fn [truth]
                                (test truth 
                                  (fn []
                                    (join (fn []
                                            (tail-query resolve reject state))
                                          (fn []
                                            (call partitions two object
                                              (fn [partitions]
                                                (call rest partitions
                                                  (fn [tail-partitions]
                                                    (scan (fn [x]
                                                            (call nth x zero
                                                              (fn [a]
                                                                (call nth x one
                                                                  (fn [b]
                                                                    (give state a 
                                                                      (fn [state]
                                                                        (subsequence-query
                                                                         (fn [a-state]
                                                                           (give a-state b
                                                                             (fn [a-state*]
                                                                               (call rec a-state* identity))))
                                                                         fail
                                                                         state))))))))
                                                          tail-partitions))))))))
                                  (fn []
                                    (resolve state))))))))))
                  (fn []
                    (reject state))))))))))

  IYieldFunction
  (yield-function [this environment]
    (let [bind (get environment :bind)
          call (get environment :call)
          data (get environment :data)
          host (get environment :eval)
          fail (get environment :fail)
          give (get environment :give)
          join (get environment :join)
          pass (get environment :pass)
          star (get environment :star)
          take (get environment :take)
          test (get environment :test)
          subsequence-yield (yield-function subsequence-pattern environment)
          tail-yield (yield-function tail-pattern environment)
          concat (host `clojure/concat)
          subsequence-sequential? (host-sequential-predicate environment subsequence-pattern)
          tail-sequential? (host-sequential-predicate environment tail-pattern)
          empty-list (data ())]
      (fn [resolve reject state]
        (join (fn []
                (tail-yield (fn [tail-state]
                              (take tail-state
                                (fn [as]
                                  (call tail-sequential? as
                                    (fn [truth]
                                      (test truth
                                        (fn [] (resolve tail-state))
                                        (fn [] (reject tail-state))))))))
                            reject
                            state))
              (fn [] 
                (give state empty-list
                  (fn [state]
                    (star state
                      (fn [rec state]
                        (take state
                          (fn [as]
                            (subsequence-yield
                             (fn [subsequence-state]
                               (take subsequence-state
                                 (fn [bs]
                                   (call subsequence-sequential? bs
                                     (fn [truth]
                                       (test truth
                                         (fn []
                                           (tail-yield
                                            (fn [tail-state]
                                              (take tail-state
                                                (fn [tail]
                                                  (call tail-sequential? tail
                                                    (fn [truth]
                                                      (test truth
                                                        (fn []
                                                          (call concat as bs
                                                            (fn [cs]
                                                              (call concat cs tail
                                                                (fn [ds]
                                                                  (join (fn [] 
                                                                          (give tail-state ds resolve))
                                                                        (fn []
                                                                          (give subsequence-state cs
                                                                            (fn [next-state]
                                                                              (call rec next-state identity))))))))))
                                                        (fn []
                                                          (fail state))))))))
                                            fail
                                            subsequence-state))
                                         (fn []
                                           (fail state))))))))
                             reject
                             state)))))))))))))


(defrecord Assoc [map-pattern key-pattern val-pattern]
  IQueryFunction
  (query-function [this environment]
    (let [bind (get environment :bind)
          call (get environment :call)
          host (get environment :eval)
          fail (get environment :fail)
          give (get environment :give)
          pass (get environment :pass)
          scan (get environment :scan)
          take (get environment :take)
          test (get environment :test)
          map-query (query-function map-pattern (set-associative environment this))
          key-query (query-function key-pattern environment)
          val-query (query-function val-pattern environment)
          dissoc (host `clojure/dissoc)
          get (host `clojure/get)
          key (host `clojure/key)
          associative? (host-associative-predicate environment this)
          seq (host `clojure/seq)
          val (host `clojure/val)]
      (fn [resolve reject state]
        (take state
          (fn [object]
            (call associative? object
              (fn [truth]
                (test truth 
                  (fn []
                    (call seq object
                      (fn [entries]
                        (scan (fn [entry]
                                (call key entry
                                  (fn [k]
                                    (call val entry
                                      (fn [v]
                                        (call dissoc object k
                                          (fn [m]
                                            (give state k
                                              (fn [k-state]
                                                (key-query (fn [key-state]
                                                             (give key-state v
                                                               (fn [v-state]
                                                                 (val-query (fn [val-state]
                                                                              (give val-state m
                                                                                (fn [m-state]
                                                                                  (map-query resolve reject m-state))))
                                                                            reject
                                                                            v-state))))
                                                           reject
                                                           k-state))))))))))
                              entries))))
                  (fn []
                    (fail state))))))))))

  IYieldFunction
  (yield-function [this environment]
    (let [bind (get environment :bind)
          call (get environment :call)
          host (get environment :eval)
          fail (get environment :fail)
          give (get environment :give)
          pass (get environment :pass)
          scan (get environment :scan)
          take (get environment :take)
          test (get environment :test)
          map-yield (yield-function map-pattern environment)
          key-yield (yield-function key-pattern environment)
          val-yield (yield-function val-pattern environment)
          assoc (host `clojure/assoc)
          map? (host `clojure/map?)]
      (fn [resolve reject state]
        (map-yield (fn [map-state]
                     (take map-state
                       (fn [m]
                         (call map? m
                           (fn [truth]
                             (test truth
                               (fn []
                                 (key-yield (fn [key-state]
                                              (take key-state
                                                (fn [k]
                                                  (val-yield (fn [val-state]
                                                               (take val-state
                                                                 (fn [v]
                                                                   (call assoc m k v
                                                                     (fn [m*]
                                                                       (give val-state m* resolve))))))
                                                             reject
                                                             key-state))))
                                            reject
                                            map-state))
                               (fn []
                                 (reject map-state))))))))
                   reject
                   state)))))

(defrecord Merge [map-patterns]
  IQueryFunction
  (query-function [this environment]
    (let [bind (get environment :bind)
          call (get environment :call)
          data (get environment :data)
          host (get environment :eval)
          give (get environment :give)
          pass (get environment :pass)
          scan (get environment :scan)
          take (get environment :take)
          test (get environment :test)
          queries (map (fn [map-pattern]
                         (query-function map-pattern (set-associative environment map-pattern)))
                       map-patterns)
          indexed-queries (map-indexed (fn [index query]
                                         [(data index) query])
                                       queries)
          associative? (host-associative-predicate environment this)
          map-partitions (host `m.algorithms/map-partitions)
          nth (host `clojure/nth)
          n (data (count queries))]
      (fn [resolve reject state]
        (take state
          (fn [object]
            (call associative? object
              (fn [truth]
                (test truth 
                  (fn []
                    (call map-partitions object n
                      (fn [partitions]
                        (bind resolve
                              (scan (fn [partition]
                                      (reduce
                                       (fn [ma [index query]]
                                         (call nth partition index
                                           (fn [m]
                                             (bind (fn [state]
                                                     (give state m
                                                       (fn [m-state]
                                                         (query pass reject m-state))))
                                                   ma))))
                                       (pass state)
                                       indexed-queries))
                                    partitions)))))
                  (fn []
                    (reject state))))))))))

  IYieldFunction
  (yield-function [this environment]
    (let [bind (get environment :bind)
          call (get environment :call)
          data (get environment :data)
          host (get environment :eval)
          give (get environment :give)
          pass (get environment :pass)
          scan (get environment :scan)
          take (get environment :take)
          test (get environment :test)
          yields (map (fn [map-pattern]
                        [(yield-function map-pattern environment)
                         (host-associative-predicate environment map-pattern)])
                      map-patterns)
          merge (host `clojure/merge)
          empty-map (data {})]
      (fn [resolve reject state]
        (reduce
         (fn [ma [yield associative?]]
           (bind (fn [state]
                   (take state
                     (fn [m1]
                       (yield (fn [yield-state]
                                (take yield-state
                                  (fn [m2]
                                    (call associative? m2
                                      (fn [truth]
                                        (test truth
                                          (fn []
                                            (call merge m1 m2
                                              (fn [m3]
                                                (give state m3 pass))))
                                          (fn []
                                            (reject state))))))))
                              reject
                              state))))
                 ma))
         (give state empty-map pass)
         yields)))))

;; Rule
;; ----

(defrecord Rule [query-pattern yield-pattern]
  IQueryFunction
  (query-function [this environment]
    (query-function query-pattern environment))

  IYieldFunction
  (yield-function [this environment]
    (yield-function yield-pattern environment)))

;; Constructors
;; ---------------------------------------------------------------------

;; Atomic patterns
;; ---------------

(defn anything
  "Pattern which represents any object in the universe."
  []
  (->Anything))

(defn nothing
  "Pattern which represents no object in the universe."
  []
  (->Nothing))

(defn data
  "Pattern which represents value."
  [value]
  (->Data value))

(defn host
  "Pattern which represents the value of form as evaluated by the host
  language."
  [form]
  (->Host form))

;; Variable
;; --------

(defn logic-fold-function [kernel]
  (let [call (get kernel :call)
        host (get kernel :eval)
        none (get kernel :none)
        test (get kernel :test)
        host-equals (host `clojure/=)]
    (fn [old new pass fail]
      (call host-equals old none
        (fn [truth]
          (test truth
            (fn [] (pass new))
            (fn []
              (call host-equals new old
                (fn [truth]
                  (test truth
                    (fn [] (pass new))
                    (fn [] (fail old))))))))))))

(defn logic-unfold-function [kernel]
  (let [call (get kernel :call)
        host (get kernel :eval)
        none (get kernel :none)
        test (get kernel :test)
        = (host `clojure/=)]
    (fn [old pass fail]
      (call = old none
        (fn [truth]
          (test truth
            (fn [] (fail old))
            (fn [] (pass old old))))))))

(defn logic-variable
  ([]
   (->Variable (gensym "?__") logic-fold-function logic-unfold-function))
  ([symbol]
   {:pre [(symbol? symbol)]}
   (->Variable symbol logic-fold-function logic-unfold-function)))

(defn mutable-fold-function [kernel]
  (fn [old new pass fail]
    (pass new)))

(defn mutable-unfold-function [kernel]
  (let [call (get kernel :call)
        host (get kernel :eval)
        none (get kernel :none)
        test (get kernel :test)
        = (host `clojure/=)]
    (fn [old pass fail]
      (call = old none
        (fn [truth]
          (test truth
            (fn [] (fail old))
            (fn [] (pass old old))))))))

(defn mutable-variable
  ([]
   (mutable-variable (gensym "!__")))
  ([id]
   (->Variable id mutable-fold-function mutable-unfold-function)))


(defn fifo-fold-function [environment]
  (let [call (get environment :call)
        host (get environment :eval)
        none (get environment :none)
        test (get environment :test)
        = (host `clojure/=)
        vector (host `clojure/vector)
        conj (host `clojure/conj)]
    (fn [old new pass fail]
      (call = old none
        (fn [truth]
          (test truth
            (fn [] (call vector new pass))
            (fn [] (call conj old new pass))))))))

(defn fifo-unfold-function [environment]
  (let [call (get environment :call)
        host (get environment :eval)
        none (get environment :none)
        test (get environment :test)
        = (host `clojure/=)
        nth (host `clojure/nth)
        seq (host `clojure/seq)
        subvec (host `clojure/subvec)]
    (fn [old pass fail]
      (call = old none
        (fn [truth]
          (test truth
            (fn [] (fail old))
            (fn []
              (call seq old
                (fn [truth]
                  (test truth
                    (fn []
                      (call nth old 0
                        (fn [x]
                          (call subvec old 1
                            (fn [new] (pass x new))))))
                    (fn []
                      (fail none))))))))))))

(defn fifo-variable
  ([]
   (fifo-variable (gensym "<__")))
  ([id]
   (->Variable id fifo-fold-function fifo-unfold-function)))

;; Compound patterns
;; ---------------------------------------------------------------------

(defn pick
  "Pattern which represents either pattern-a or pattern-b but not both."
  [pattern-a pattern-b]
  (->Pick pattern-a pattern-b))

(defn some
  "Pattern which represents either pattern-a, pattern-b, or both."
  [pattern-a pattern-b]
  (->Some pattern-a pattern-b))

(defn each
  "Pattern which represents each of pattern-a and pattern-b."
  [pattern-a pattern-b]
  (->Each pattern-a pattern-b))

(defn project
  [yield-pattern query-pattern object-pattern]
  (->Project yield-pattern query-pattern object-pattern))

(defn apply
  [function-pattern arguments-pattern return-pattern]
  (->Apply function-pattern arguments-pattern return-pattern))

(defn predicate
  [function-pattern object-pattern]
  (->Predicate function-pattern object-pattern))

;; Regular Expression Constructors
;; -------------------------------

(defn regex-empty []
  (->RegexEmpty))

(defn regex-cons [head-pattern tail-pattern]
  (->RegexCons head-pattern tail-pattern))

(defn regex-concatenation [initial-patterns tail-pattern]
  {:pre [(clojure/sequential? initial-patterns)]}
  (->RegexConcatenation initial-patterns tail-pattern))

(defn regex-join [x-pattern y-pattern]
  (->RegexJoin x-pattern y-pattern))

(defn greedy-star [subsequence-pattern tail-pattern]
  (if (sequential? subsequence-pattern)
    (->GreedyStar (regex-concatenation subsequence-pattern (regex-empty)) tail-pattern)
    (->GreedyStar subsequence-pattern tail-pattern)))

(defn frugal-star [subsequence-pattern tail-pattern]
  (if (sequential? subsequence-pattern)
    (->FrugalStar (regex-concatenation subsequence-pattern (regex-empty)) tail-pattern)
    (->FrugalStar subsequence-pattern tail-pattern)))

;; Associative Constructors
;; ------------------------

(defn assoc [map-pattern key-pattern val-pattern]
  (->Assoc map-pattern key-pattern val-pattern))

(defn merge [map-pattern-a map-pattern-b]
  (->Merge [map-pattern-a map-pattern-b]))

;; Rule Constructors
;; -----------------

(defn rule [query-pattern yield-pattern]
  (->Rule query-pattern yield-pattern))

;; Query/Yield API
;; ---------------------------------------------------------------------

(defn run-query [pattern kernel object]
  (let [query (query-function pattern kernel)
        data (get kernel :data)
        fail (get kernel :fail)
        pass (get kernel :pass)
        seed (get kernel :seed)]
    (query pass fail (seed (data object)))))

(defn run-yield [pattern kernel]
  (let [yield (yield-function pattern kernel)
        data (get kernel :data)
        fail (get kernel :fail)
        pass (get kernel :pass)
        seed (get kernel :seed)]
    (yield pass fail (seed (data nil)))))

(defn run-rule [rule kernel object]
  (let [bind (get kernel :bind)
        pass (get kernel :pass)
        fail (get kernel :fail)
        seed (get kernel :seed)
        take (get kernel :take)
        query (query-function rule kernel)
        yield (yield-function rule kernel)]
    (query (fn [state]
             (yield pass fail state))
           fail
           (seed object))))

;; Local Variables:
;; eval: (put-clojure-indent 'call :defn)
;; eval: (put-clojure-indent 'test :defn)
;; eval: (put-clojure-indent 'take :defn)
;; eval: (put-clojure-indent 'give :defn)
;; eval: (put-clojure-indent 'star :defn)
;; End: