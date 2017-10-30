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

(defn property-value-query 
  "Returns a partial datomic query in map form containing only a where clause that interconnects the :jcr.node entity ?e with its :jcr.property entity named ?property-name and the value of :jcr.value entity of that property."
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

(defn ^:private child-node-name-query 
  "Returns a partial datomic query in map form containing only a where clause that interconnects the :jcr.node entity ?e and its childnode entities ?c and the childnodes name ?name"
  [?e ?c ?name]
  {:where [[?e :jcr.node/children ?c]
           [?c :jcr.node/name ?name]]})

(defn node-type-query 
  "Returns a partial datomic query in map form containing only a where clause that interconnects the :jcr.node entity ?e and its property entity value ?v"
  [?e ?v]
  (merge-queries
   (property-value-query ?e "jcr:primaryType" "nt:nodeType")
   (property-value-query ?e "jcr:nodeTypeName" ?v)))

(defn ^:private property-query [?e property-name]
  (let [?pv (gensym "?pv")]
    (merge-queries {:find [?pv]}
                   (property-value-query ?e property-name ?pv))))

(defn nodetype-property-query [nodetype-name property-name]
  (let [?pv (gensym "?pv")
        ?e  (gensym "?e")]  
    (merge-queries {:find [?pv]}
                   (property-value-query ?e property-name ?pv)
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

(defn all-child-nodes [id]
  (let [?c (gensym "?c")]
    (merge-queries {:find [?c]}
                   (child-node-query id ?c))))

(defn node-name-query [id]
  (let [?n (gensym "?n")]
    (merge-queries {:find [?n]}
                   (attribute-value-query id :jcr.node/name ?n))))

(defn child-query [parent-entity-id childname index]
  (let [?c (gensym "?c")]
    (merge-queries {:find [?c]}
                   (child-node-name-query parent-entity-id ?c childname)
                   (if (< 0 index) (position-query ?c index)))))

(defn all-property-values [db id property-name]
  (as-> id x
    (property-query x property-name)
    (d/q x db) 
    (map first x)))

(defn first-property-value [db id property-name]
  (as-> (all-property-values db id property-name) x
    (first x)))


