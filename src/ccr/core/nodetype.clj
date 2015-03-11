(ns ccr.core.nodetype
  (:require [ccr.api.nodetype]
            [ccr.core.cnd :as cnd]
            [datomic.api :as d  :only [q db]]
            ))

(defn merge-queries
  "Merges datomic queries (which are in map form)"
  [& queries]
  (apply merge-with into queries))


;; https://www.youtube.com/watch?v=YHctJMUG8bI 
;; Queries as data

(defn property-value-query [?e ?property-name ?value]
  (let [?p  (gensym "?p")
        ?a  (gensym "?a")
        ?vs (gensym "?vs")]
    {:where [[?e :jcr.node/properties ?p]
             [?p :jcr.property/name ?property-name]
             [?p :jcr.property/value-attr ?a]
             [?p :jcr.property/values ?vs]
             [?vs ?a ?value]]}))

(defn child-node-query [?e ?c ?name]
  {:where [[?e :jcr.node/children ?c]
           [?c :jcr.node/name ?name]]})

(defn node-type-query [?e ?v]
  (merge-queries
   (property-value-query ?e "jcr:primaryType" "nt:nodeType")
   (property-value-query ?e "jcr:nodeTypeName" ?v)))

(defn property-query [?e property-name]
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
                   (child-node-query ?e ?c childname)
                   (node-type-query ?e nodetype-name))))


(defn effective-node-type [db node-type-name])

(defrecord NodeDefinition [db id]

  ccr.api.nodetype/NodeDefinition

  ccr.api.nodetype/ItemDefinition
  (child-item-name [this] 
    (as-> id x
      (property-query x "jcr:name")
      (d/q x db) 
      (map first x)
      (first x))
    )
)

(defrecord PropertyDefinition [db id]

  ccr.api.nodetype/PropertyDefinition

  ccr.api.nodetype/ItemDefinition
  (child-item-name [this] "ups"
    )
)

(defrecord NodeType [db node-type-name]
  ccr.api.nodetype/NodeType

  (can-add-child-node?
    [this childNodeName]
    (as-> (ccr.api.nodetype/declared-child-node-definitions this) x
      (filter (fn [nd] (or (= (ccr.api.nodetype/child-item-name nd) childNodeName)
                           (= (ccr.api.nodetype/child-item-name nd) "*"))) x)
      (not (empty? x))))
  
  (can-add-child-node?
    [this childNodeName nodeTypeName])

  (node-type? [this nodeTypeName]
    (= nodeTypeName node-type-name))
  
  ccr.api.nodetype/NodeTypeDefinition

  (node-type-name [this]
    (as-> node-type-name x
      (nodetype-property-query x "jcr:nodeTypeName")
      (d/q x db) 
      (map first x)
      (first x)))
  
  (declared-supertype-names [this]
    (as-> node-type-name x
      (nodetype-property-query x "jcr:supertypes")
      (d/q x db) 
      (map first x)))

  (abstract? [this]
    (as-> node-type-name x
      (nodetype-property-query x "jcr:isAbstract")
      (d/q x db) 
      (map first x)
      (first x)
      (true? x)))

  (mixin? [this]
    (as-> node-type-name x
      (nodetype-property-query x "jcr:isMixin")
      (d/q x db) 
      (map first x)
      (first x)
      (true? x)))

  (orderable-child-nodes? [this]
    (as-> node-type-name x
      (nodetype-property-query x "jcr:hasOrderableChildNodes")
      (d/q x db) 
      (map first x)
      (first x)
      (true? x)))

  (primary-item-name [this]
    (as-> node-type-name x
      (nodetype-property-query x "jcr:primaryItemName")
      (d/q x db) 
      (map first x)
      (first x)))

  (declared-property-definitions [this]
    (as-> node-type-name x
      (nodetype-child-query x "jcr:propertyDefinition")
      (d/q x db) 
      (map first x)
      (map (fn [id] (->PropertyDefinition db id)) x)))

  (declared-child-node-definitions [this]
    (as-> node-type-name x
      (nodetype-child-query x "jcr:childNodeDefinition")
      (d/q x db) 
      (map first x)
      (map (fn [id] (->NodeDefinition db id)) x)))
  )

(defn nodetype [db node-type-name]
  (->NodeType db node-type-name))

(defn load-node-types
  "Loads the builtin nodetypes to datomic connection"
  [connection tx-data]
  (d/transact connection tx-data))

(defn load-builtin-node-types
  "Loads the builtin nodetypes to datomic connection"
  [connection]
  (load-node-types connection  
                   (as-> (cnd/builtin-nodetypes) x
                         (cnd/node-to-tx x))))

