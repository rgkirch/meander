(ns ^:no-doc meander.util.zeta
  (:require
   [clojure.walk :as walk]))

(defn cljs-env?
  "true if compiling ClojureScript or in a ClojureScript setting,
  false otherwise."
  [env]
  #?(:clj (some? (:ns env))
     :cljs true))

#?(:clj
   (defn cljs-ns-from-clj-ns
     "Given a `clojure.lang.Namespace` clj-ns, return a map which
  partially emulates the :ns key of a ClojureScript &env
  environment. The map has the structure

    {:name symbol?
     :requires {symbol? qualified-symbol?}
     :uses {symbol? qualified-symbol?}}

  This is used to provide a canonical format for symbol
  resolution and reasoning in cases where using `*ns*` is
  inappropriate or does not work."
     [clj-ns]
     (let [requires (into {}
                          (map (fn [[k v]] [k (ns-name v)]))
                          (ns-aliases *ns*))
           required-ns-names (set (vals requires))
           uses (into {}
                      (keep (fn [[sym x]]
                              (if (var? x)
                                (let [var-ns-name (ns-name (:ns (meta x)))]
                                  (if (contains? required-ns-names var-ns-name)
                                    [sym var-ns-name])))))
                      (ns-refers *ns*))]
       ;; Incomplete.
       {:name (ns-name *ns*)
        :requires requires
        :uses uses})))

#?(:clj
   (defmacro canonical-ns
     "Return the current compile time canonical namespace data."
     []
     (if (cljs-env? &env)
       `(quote ~{:ns (select-keys (get &env :ns) [:name :requires :uses])})
       `(quote ~(cljs-ns-from-clj-ns *ns*)))))

(defn prewalk
  "Like `clojure.walk/prewalk` but stops walking if f returns a
  `reduced?`."
  [f form]
  (walk/walk
   (fn [x]
     (if (reduced? x)
       x
       (prewalk f x)))
   (fn [x]
     (if (reduced? x)
       (deref x)
       x))
   (f form)))

(defn fix [f]
  (fn [x]
    (let [x* (f x)]
      (if (= x x*)
        x
        (recur x*)))))

(defn one [x fs]
  (reduce
   (fn [x f]
     (let [x* (f x)]
       (if (= x x*)
         x
         (reduced x*))))
   x
   fs))


(defn mix*
  "Helper function for mix."
  {:private true}
  [colls]
  (if (seq colls)
    (if (seq (first colls))
      (lazy-seq (cons (ffirst colls)
                      (mix* (conj (subvec colls 1)
                                  (rest (first colls))))))
      (recur (subvec colls 1)))
    ()))

(defn mix
  "Like clojure.core/interleave but exhausts each supplied
  collection.

    (interleave [1 2 3] [\"a\" \"b\"] [:w :x :y :z])
    ;; =>
    (1 \"a\" :w 2 \"b\" :x)

    (mix [1 2 3] [\"a\" \"b\"] [:w :x :y :z])
    ;; =>
    (1 \"a\" :w 2 \"b\" :x 3 :y :z)"
  {:arglists '([coll1 coll2 & more-colls])}
  ([coll1 coll2]
   (if (seq coll1)
     (lazy-seq (cons (first coll1) (mix coll2 (rest coll1))))
     coll2))
  ([coll1 coll2 coll3]
   (if (seq coll1)
     (lazy-seq (cons (first coll1) (mix coll2 coll3 (rest coll1))))
     (mix coll2 coll3)))
  ([coll1 coll2 coll3 coll4]
   (if (seq coll1)
     (lazy-seq (cons (first coll1) (mix coll2 coll3 coll4 (rest coll1))))
     (mix coll2 coll3 coll4)))
  ([coll1 coll2 coll3 coll4 & more-colls]
   (mix* (into [coll1 coll2 coll3 coll4] more-colls))))

(defn flatten-with [branch? children root]
  (remove branch? (tree-seq branch? children root)))