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

(defn chain-criteria* [body]
  (join-op " AND " body))

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

(defn any [& args]
  (make-criteria #(str %1 ":[" (s/join " " %2) "]") args))

(defn none [& args]
  (negate any args))

(defn between [& args]
  (make-criteria #(str %1 ":[" (first %2) " TO " (second %2) "]") args))

(defn take-when [pred [x & more :as fail]]
  (if (pred x) [x more] [nil fail]))

(defn create-criteria [& body]
  (chain-criteria* body))
