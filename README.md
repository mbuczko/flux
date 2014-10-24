# flux

This is a fork of Matt Mitchell's Flux - a Clojure based Solr client + Criteria DSL to make SOLR queries bit sweeter. Current Apache Solr version support is `4.9.0`.

## Installation (Leiningen)

To include the Flux library, add the following to your `:dependencies`:

    [com.codesignals/flux "0.5.0"]

## Usage

###Http

```clojure
(require '[flux.http :as http])

(def conn (http/create "http://localhost:8983/solr" :collection1))
```

###Embedded

```clojure
(require '[flux.embedded :as embedded])

(def cc (embedded/create-core-container "path/to/solr-home" "path/to/solr.xml"))
```

####Core auto-discovery
Flux also supports `core.properties`. Just give `create-core` the solr-home path as the only argument.

  Note: It's important to call the `load` method on the resulting `CoreContainer` instance:

```clojure
(def cc (doto (embedded/create-core-container "path/to/solr-home")
              (.load))
```

Now create the embedded server instance:

```clojure
(def conn (embedded/create cc :collection1))
```

###Client
Once a connection as been created, use the `with-connection` macro to wrap client calls:

```clojure
(require '[flux.core :as flux]
          [flux.query :as q])

(flux/with-connection conn
    (flux/add [{:id 1} {:id 2}])
    (flux/commit)
    (flux/query "*:*")
    ;; Or set the path and/or method:
    (flux/request
      (q/create-query-request :post "/docs" {:q "etc"}))
```

### Criteria
As it's quite cumbersome to define filters using raw Solr syntax following is a neat criteria DSL to make things a bit smoother.

```clojure
(use 'flux.criteria)

;; make:Mercedes*

(create-criteria
  (is :make "Mercedes*"))

;; category:car/Mercedes/Sprinter

(create-criteria
  (is :category "car/Mercedes/Sprinter"))

;; category:car/Mercedes/Sprinter AND build_year:2010

(create-criteria
  (is :category "car/Mercedes/Sprinter")
  (is :build_year 2010))

this is equivalent to:

(create-criteria
  (and
    (is :category "car/Mercedes/Sprinter")
    (is :build_year 2010)))

and can be shortened even further:

(create-criteria
  (is {:category  "car/Mercedes/Sprinter" 
       :build_year 2010}))

this kind of "shortcut" applies to all handled functions listed below.

;; (-technical_condition:damaged) OR ((price:[* TO 5000]) AND (build_year:[* TO 2005]))

(create-criteria
  (or
    (is-not :technical_condition "damaged")
    (<= {:price 5000 :build_year 2005})))

;; make:[audi mercedes]

(create-criteria
   (any :make ["audi" "mercedes"]))

;; -make:[zaporożec wołga]

(create-criteria
   (none :make ["zaporożec" "wołga"]))

;; (description:"nówka sztuka") OR (price:[5000 TO 15000] AND currency:PLN))

(create-criteria
   (or
     (has :description "nówka sztuka")
     (and
        (between :price [5000 15000])
        (is :currency "PLN"))))

```

###javax.servlet/servlet-api and EmbeddedSolrServer

Unfortunately, EmbeddedSolrServer requires javax.servlet/servlet-api as an implicit dependency. Because of this, Flux adds this lib as a depedency.

  * http://wiki.apache.org/solr/Solrj#EmbeddedSolrServer
  * http://lucene.472066.n3.nabble.com/EmbeddedSolrServer-java-lang-NoClassDefFoundError-javax-servlet-ServletRequest-td483937.html

###Test
```shell
lein midje
```

## License

Copyright © 2013-2014 Matt Mitchell

Distributed under the Eclipse Public License, the same as Clojure.
