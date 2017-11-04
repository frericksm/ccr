(ns ccr.core.model
  (:require [datomic.api :as d  :only [q db pull pull-many transact]]))

(defn debug [m x] (println m x) x)

(defn merge-queries
  "Merges datomic queries. The queries are expected to be in map form"
  [& queries]
  (apply merge-with into queries))


;; https://www.youtube.com/watch?v=YHctJMUG8bI 
;; Queries as data

(defn position-query 
  "Returns a partial datomic query in map form containing only a where clause that interconnects the :jcr.node entity ?e with its :jcr.node/position attribute named ?index."
  [?e ?index]
  {:where [[?e :jcr.node/position ?index]]})

(defn property-by-name-query 
  "Returns a partial datomic query in map form containing only a where clause that interconnects the :jcr.node entity ?e with its :jcr.property entity named ?property-name"
  [?e ?p ?property-name]
  {:where [[?e :jcr.node/properties ?p]
           [?p :jcr.property/name ?property-name]]})

(defn value-join-query 
  "Returns a partial datomic query in map form containing only a where clause that interconnects the :jcr.property ?e and the value of :jcr.value entity of that property."
  [?e ?value]
  (let [?a  (gensym "?a")
        ?vs (gensym "?vs")]
    {:where [[?e :jcr.property/value-attr ?a]
             [?e :jcr.property/values ?vs]
             [?vs ?a ?value]]}))

(defn property-value-join-query 
  "Returns a partial datomic query in map form containing only a where clause that interconnects the :jcr.node entity ?e and its :jcr.property entity named ?property-name and the value of :jcr.value entity of that property."
  [?e ?property-name ?value]
  (let [?p  (gensym "?p")
        ?a  (gensym "?a")
        ?vs (gensym "?vs")]
    {:where [[?e :jcr.node/properties ?p]
             [?p :jcr.property/name ?property-name]
             [?p :jcr.property/value-attr ?a]
             [?p :jcr.property/values ?vs]
             [?vs ?a ?value]]}))

(defn attribute-value-query 
  "Returns a partial datomic query in map form containing only a where clause that queries the entity ?e, attribute ?a and value ?v"
  [?e ?a ?v]
  {:where [[?e ?a ?v]]})

(defn child-node-query 
  "Returns a partial datomic query in map form containing only a where clause that interconnects the :jcr.node entity ?e and its childnode entities ?c"
  [?e ?c]
  {:where [[?e :jcr.node/children ?c]]})

(defn property-query 
  "Returns a partial datomic query in map form containing only a where clause that interconnects the :jcr.node entity ?e and its property entities ?p"
  [?e ?p]
  {:where [[?e :jcr.node/properties ?p]]})

(defn ^:private child-node-name-query 
  "Returns a partial datomic query in map form containing only a where clause that interconnects the :jcr.node entity ?e and its property entities ?c and the childnodes name ?name"
  [?e ?c ?name]
  {:where [[?e :jcr.node/children ?c]
           [?c :jcr.node/name ?name]]})

(defn node-type-query 
  "Returns a partial datomic query in map form containing only a where clause that interconnects the :jcr.node entity ?e and its property entity value ?v"
  [?e ?v]
  (merge-queries
   (property-value-join-query ?e "jcr:primaryType" "nt:nodeType")
   (property-value-join-query ?e "jcr:nodeTypeName" ?v)))

(defn ^:private value-query [?e]
  (let [?pv (gensym "?pv")]
    (merge-queries {:find [?pv]}
                   (value-join-query ?e ?pv))))

(defn ^:private property-value-query [?e property-name]
  (let [?pv (gensym "?pv")]
    (merge-queries {:find [?pv]}
                   (property-value-join-query ?e property-name ?pv))))

(defn nodetype-property-query [nodetype-name property-name]
  (let [?pv (gensym "?pv")
        ?e  (gensym "?e")]  
    (merge-queries {:find [?pv]}
                   (property-value-join-query ?e property-name ?pv)
                   (node-type-query ?e nodetype-name))))

(defn nodetype-child-query [nodetype-name childname]
  (let [?c (gensym "?c")
        ?e (gensym "?e")]
    (merge-queries {:find [?c]}
                   (child-node-name-query ?e ?c childname)
                   (node-type-query ?e nodetype-name))))

(defn ^:private parent-query [child-entity-id]
  (let [?e (gensym "?e")]
    (merge-queries {:find [?e]}
                   (child-node-query ?e child-entity-id))))

(defn property-by-name [node-id property-name]
  (let [?p (gensym "?p")]  
    (merge-queries {:find [?p]}
                   (property-by-name-query node-id ?p property-name))))

(defn all-child-nodes [id]
  (let [?c (gensym "?c")]
    (merge-queries {:find [?c]}
                   (child-node-query id ?c))))

(defn all-properties [id]
  (let [?p (gensym "?p")]
    (merge-queries {:find [?p]}
                   (property-query id ?p))))

(defn node-name-query [id]
  (let [?n (gensym "?n")]
    (merge-queries {:find [?n]}
                   (attribute-value-query id :jcr.node/name ?n))))

(defn property-name-query [id]
  (let [?n (gensym "?n")]
    (merge-queries {:find [?n]}
                   (attribute-value-query id :jcr.property/name ?n))))

(defn property-value-attribute-query [prop-id]
  (let [?v (gensym "?v")]
    (merge-queries {:find [?v]}
                   (attribute-value-query prop-id
                                          :jcr.property/value-attr
                                          ?v))))

#_(defn property-query [parent-entity-id name]
  (let [?p (gensym "?p")]
    (merge-queries {:find [?p]}
                   (property-by-name-query parent-entity-id ?p name))))

(defn child-query [parent-entity-id childname index]
  (let [?c (gensym "?c")]
    (merge-queries {:find [?c]}
                   (child-node-name-query parent-entity-id ?c childname)
                   (if (< 0 index) (position-query ?c index)))))

(defn all-property-values [db id property-name]
  (as-> id x
    (property-value-query x property-name)
    (d/q x db) 
    (map first x)))

(defn first-property-value [db id property-name]
  (as-> (all-property-values db id property-name) x
    (first x)))

(defn all-values [db property-id]
  (as-> (value-query property-id) x
    (d/q x db) 
    (map first x)))

(defn first-value [db property-id]
  (as-> (all-values db property-id) x
    (first x)))


(defn where-value-entity 
  "Returns a partial datomic query in map form containing only a where clause that interconnects the :jcr.property ?e and the value of :jcr.value entity of that property."
  [?p ?value]
  {:where [[?p :jcr.property/values ?value]]})

(defn value-entities
  "Returns the value entities of the property with prop-id"
  [db prop-id]
  (as-> (let [?value (symbol "?value")]
          (merge-queries '{:find [(pull ?value [*])]}
                         (where-value-entity prop-id ?value))) x
    (d/q x db)
    (map first x)
    (sort-by :jcr.value/position x)))


(defn values
  "Returns the value entities of the property with prop-id"
  [db prop-id]
  (let [value-attr (as-> (property-value-attribute-query prop-id) x
                     (d/q x db)
                     (map first x)
                     (first x))]
    (as-> (value-entities db prop-id) x
      (map (fn [e] (get e value-attr)) x))))
