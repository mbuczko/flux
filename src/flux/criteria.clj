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

(defn- chain-query* [& body]
  (mapv (fn [x] 
          (if (nil? x) {:q "*:*"} 
              (if (string? x) {:q x} x)))
        (flatten body)))

(defn- opts= [prefix opts]
  (let [p (name prefix)]
    (if (nil? opts) 
      nil 
      (for [[k v] opts] {(keyword (str p "." (name k))) v}))))

(defn- names= [keywords]
  (map #(name %) keywords))

(defn- chained? [arg]
  (clojure.core/or (string? arg) (vector? arg)))

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

(defn query [& body]
  (conj nil (hash-map :facet.query (chain-criteria* body))))

(defn pivots [& body]
  (let [[maybe-opts args] (take-when map? body)]
    (conj (opts= :facet.pivot maybe-opts)
          (map #(hash-map :facet.pivot (s/join "," (names= %))) args))))

(defn fields [& body]
  (let [[maybe-opts args] (take-when map? body)]
    (conj (opts= :facet maybe-opts)
          (map #(hash-map :facet.field (name %)) args))))

(defn with-criteria [& body]
  (let [[maybe-query args] (take-when chained? body)]
    (chain-query* maybe-query {:fq (chain-criteria* args)})))

(defn with-facets [& body]
  (let [[maybe-query args] (take-when chained? body)
        [maybe-opts funcs] (take-when map? args)]
    (chain-query* maybe-query funcs {:facet true})))

(defn with-options [& body]
  (let [[maybe-query [opts]] (take-when chained? body)
        rows (:rows opts 100)
        page (:page opts 0)
        sort (:sort opts)]
    (chain-query* maybe-query (conj 
                               (if (nil? sort) nil {:sort sort}) 
                               {:rows rows} 
                               {:start (* page rows)}))))
