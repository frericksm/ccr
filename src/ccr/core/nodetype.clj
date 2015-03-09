(ns ccr.core.nodetype
  (:require [ccr.api.nodetype]
            [ccr.core.cnd :as cnd]
            [datomic.api :as d  :only [q db]]
            ))

(defn merge-queries
  "Merges datomic queries (which are in map form)"
  [& queries]
  (apply merge-with (comp vec concat) queries))

(defn node-type-query [?e ?v]
  {:where [['?p1 :jcr.property/name "jcr:primaryType"]
           ['?p1 :jcr.property/value-attr '?va1]
           ['?p1 :jcr.property/values '?values1]
           ['?values1 '?va1 "nt:nodeType"]
           [?e :jcr.node/properties '?p1]
           
           ['?p2 :jcr.property/name "jcr:nodeTypeName"]
           ['?p2 :jcr.property/value-attr '?va2]
           ['?p2 :jcr.property/values '?values2]
           ['?values2 '?va2 ?v]
           [?e :jcr.node/properties '?p2]]})

(defn property-value-query [property-name ?e ?pv]
  {:where [['?p3 :jcr.property/name property-name]
           ['?p3 :jcr.property/value-attr '?va3]
           ['?p3 :jcr.property/values '?values3]
           ['?values3 '?va3 '?pv]
           [?e :jcr.node/properties '?p3]]})

(defn child-node-query [?e ?c ?name]
  {:where [[?c :jcr.node/name ?name]
           [?e :jcr.node/children ?c]]})

(defn abstract?-query [nodetype-name]
  (merge-queries {:find ['?pv]
                  :in   ['$]}
                 (property-value-query "jcr:isAbstract" '?e '?pv)
                 (node-type-query '?e nodetype-name)))

(defn mixin?-query [nodetype-name]
  (merge-queries {:find ['?pv]
                  :in   ['$]}
                 (property-value-query "jcr:isMixin" '?e '?pv)
                 (node-type-query '?e nodetype-name)))

(defn primary-iten-name-query [nodetype-name]
  (merge-queries {:find ['?pv]
                  :in   ['$]}
                 (property-value-query "jcr:primaryItemName" '?e '?pv)
                 (node-type-query '?e nodetype-name)))

(defn declared-supertypes-query [nodetype-name]
  (merge-queries {:find ['?pv]
                  :in   ['$]}
                 (property-value-query "jcr:supertypes" '?e '?pv)
                 (node-type-query '?e nodetype-name)))

(defn declared-property-definitions-query [nodetype-name]
  (merge-queries {:find ['?c]
                  :in   ['$]}
                 (child-node-query '?e '?c "jcr:propertyDefinition")
                 (node-type-query '?e nodetype-name)))

(defn declared-child-node-definitions-query [nodetype-name]
  (merge-queries {:find ['?c]
                  :in   ['$]}
                 (child-node-query '?e '?c "jcr:childNodeDefinition")
                 (node-type-query '?e nodetype-name)))

(defrecord NodeTypeImpl [db node-type-name]
  ccr.api.nodetype/NodeType

  (node-type? [this nodeTypeName]
    (= nodeTypeName node-type-name))
  
  ccr.api.nodetype/NodeTypeDefinition

  (node-type-name [this]
    node-type-name)
  
  (declared-supertype-names [this]
    (as-> node-type-name x
          (declared-supertypes-query x)
          (d/q x db) 
          (map first x)))

  (abstract? [this]
    (as-> node-type-name x
          (abstract?-query x)
          (d/q x db) 
          (map first x)
          (first x)
          (true? x)))

  (mixin? [this]
    (as-> node-type-name x
          (mixin?-query x)
          (d/q x db) 
          (map first x)
          (first x)
          (true? x)))

  (primary-item-name [this]
    (as-> node-type-name x
          (primary-iten-name-query x)
          (d/q x db) 
          (map first x)
          (first x)))

  (declared-property-definitions [this]
    (as-> node-type-name x
          (declared-property-definitions-query x)
          (d/q x db) 
          (map first x)))

  (declared-child-node-definitions [this]
    (as-> node-type-name x
          (declared-child-node-definitions-query x)
          (d/q x db) 
          (map first x)))
  )

(defn nodetype [db node-type-name]
  (->NodeTypeImpl db node-type-name))

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

