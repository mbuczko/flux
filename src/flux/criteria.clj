(ns flux.criteria
  (:refer-clojure :exclude [<= >= and or])
  (:require [clojure.string :as s])
  (:require [flux.query :as q]))

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

(defn- chain-criteria [body]
  (if (= (count body) 1)
    (first body)
    (join-op " AND " body)))

(defn- query-str [x]
  (if (nil? x) {:q "*:*"} (if (string? x) {:q x} x)))

(defn- prefix-keys [prefix opts]
  (when-let [p (name prefix)]
    (for [[k v] opts] {(keyword (str p "." (name k))) v})))

(defn- names [keywords]
  (map name keywords))

(defn queried? [arg]
  (clojure.core/or (string? arg) (:q arg)))

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
  (list {:facet.query (chain-criteria body)}))

(defn pivots [& body]
  (let [[maybe-opts args] (take-when map? body)]
    (concat (prefix-keys :facet.pivot maybe-opts)
            (map #(hash-map :facet.pivot [(s/join "," (names %))]) args))))

(defn fields [& body]
  (let [[maybe-opts args] (take-when map? body)]
    (concat (prefix-keys :facet maybe-opts)
            (map #(hash-map :facet.field [(name %)]) args))))

(defn with-criteria [& body]
  (let [[maybe-query args] (take-when queried? body)]
    ;; (#'q/create-solr-params
    (merge-with concat 
                {:fq (chain-criteria args)}
                (query-str maybe-query))))

(defn with-facets [& body]
  (let [[maybe-query args] (take-when queried? body)
        [maybe-opts funcs] (take-when map? args)]
    (apply merge-with concat 
           {:facet true}
           (query-str maybe-query)
           (flatten funcs))))

(defn with-options [& body]
  (let [[maybe-query [opts]] (take-when queried? body)
        rows (:rows opts 100)
        page (:page opts 0)
        sort (:sort opts "score desc")]
    (merge-with concat
                maybe-query
                 {:sort sort}
                 {:rows rows}
                 {:start (* page rows)})))
