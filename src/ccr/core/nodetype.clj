(ns ccr.core.nodetype
  (:require [ccr.api.nodetype]
            [ccr.core.cnd :as cnd]
            [datomic.api :as d  :only [q db]]
            ))

(declare nodetype)

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

(defn calc-supertype-names [db node-type]
  (loop [dst1 (set (ccr.api.nodetype/declared-supertype-names node-type))]
    (let [dst2 (as-> dst1 x
                 (map (fn [supertyname] (nodetype db supertyname)) x)
                 (map (fn [nt] 
                        (ccr.api.nodetype/declared-supertype-names nt)) x)
                 (apply concat x)
                 (set x))]
      (if (= (count dst1) (count  dst2))
        dst1
        (recur dst2)))))


(defn first-property-value [db id property-name]
  (as-> id x
    (property-query x property-name)
    (d/q x db) 
    (map first x)
    (first x)))

(defrecord NodeDefinition [db id]

  ccr.api.nodetype/NodeDefinition

  ccr.api.nodetype/ItemDefinition
  (child-item-name [this] 
    (first-property-value db id "jcr:name"))
  )



(defrecord PropertyDefinition [db id]

  ccr.api.nodetype/PropertyDefinition

  ccr.api.nodetype/ItemDefinition
  (child-item-name [this] 
    (first-property-value db id "jcr:name")))

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


  (supertypes [this]
     (as-> (calc-supertype-names db this) x
       (map (fn [supertype-name] (nodetype db supertype-name)) x)))

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


