(ns flux.criteria
  (:refer-clojure :exclude [<= >= and or])
  (:require [clojure.string :as s]))

(def ^{:dynamic true} *hyphen* "+")

(defn- parenthesize [criteria]
  (str "(" criteria ")"))

(defn- negate [fn args]
  (binding [*hyphen* "-"]
    (let [criteria (apply fn args)]
      (if (= \- (first criteria)) criteria (str "-" criteria)))))

(defn- join-op [op args]
  (let [maybe-vector (first args)]
    (if (vector? maybe-vector)
      (parenthesize (s/join op maybe-vector))
      (s/join op (map parenthesize args)))))

(defn- make-criteria [cfn args]
  (let [maybe-map (first args)]
    (if (map? maybe-map)
      (->> maybe-map
           (map #(str *hyphen* (make-criteria cfn %)))
           (s/join " "))
      (let [field (name (first args))]
        (apply cfn field (rest args))))))

(defn- chain-criteria* [body]
  (if (= (count body) 1)
    (first body)
    (join-op " AND " body)))

(defn chain-facets* [facets opts & args]
  (map #(hash-map :facet.field (name %)) facets))

(defn chain-query* [& args]
  (into #{} (map (fn [x] (if (nil? x) {:q "*:*"} (if (string? x) {:q x} x))) args)))

(defn !tag [field tag]
  (str "{!tag=" tag "}" (name field)))

(defn !ex [field exclude]
  (str "{!ex=" exclude "}" (name field)))

(defn facet [field]
  (str "facet.field=" field))

(defn or [& args]
  (join-op " OR " args))

(defn and [& args]
  (join-op " AND " args))

(defn <= [& args]
  (make-criteria #(str %1 ":[* TO " %2 "]") args))

(defn >= [& args]
  (make-criteria #(str %1 ":[" %2 " TO *]") args))

(defn has [& args]
  (make-criteria #(str %1 ":\"" %2  "\"") args))

(defn is [& args]
  (make-criteria #(str %1 ":" %2) args))

(defn is-not [& args]
  (negate is args))

(defn any-of [& args]
  (make-criteria #(str %1 ":[" (s/join " " %2) "]") args))

(defn none-of [& args]
  (negate any-of args))

(defn between [& args]
  (make-criteria #(str %1 ":[" (first %2) " TO " (second %2) "]") args))

(defn take-when [pred [x & more :as body]]
  (if (pred x) [x more] [nil body]))

(defn query? [arg]
  (clojure.core/or (string? arg) (set? arg)))

(defmacro with-criteria [& body]
  (let [[maybe-query args] (take-when query? body)]
    (chain-query* maybe-query {:fq (chain-criteria* args)})))

(defmacro with-facets [& body]
  (let [[maybe-query [facets & tail]] (take-when query? body)
        [maybe-opts args] (take-when map? tail)]
    `(->> (apply chain-facets* ~facets ~maybe-opts ~args)
          (apply chain-query* ~maybe-query {:facet true}))))

(defn query [& body]
  {:facet.query (chain-criteria* body)})

