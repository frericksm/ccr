(ns ccr.core.nodetype
  (:require   [ccr.api.nodetype]
              [ccr.core.cnd :as cnd]
              [datomic.api :as d  :only [q db]]
              ))

(defrecord NodeTypeImpl [db node-type-name]
  ccr.api.nodetype/NodeType

  (node-type? [this nodeTypeName]
    )
  
  ccr.api.nodetype/NodeTypeDefinition
  (node-type-name [this]
    node-type-name
    )

  )

(defn nodetype [db node-type-name]

  (->NodeTypeImpl db node-type-name)
  )


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

(defn nodetype-query [db nodetype-name]
  (as-> (d/q '[:find ?e 
               :in $ ?v
               :where  
               [?p1 :jcr.property/name "jcr:primaryType"]
               [?p1 :jcr.property/value-attr ?va1]
               [?p1 :jcr.property/values ?values1]
               [?values1 ?va1 "nt:nodeType"]
               [?p2 :jcr.property/name "jcr:nodeTypeName"]
               [?p2 :jcr.property/value-attr ?va2]
               [?p2 :jcr.property/values ?values2]
               [?values2 ?va2 ?v]
               [?e :jcr.node/properties ?p1]
               [?e :jcr.node/properties ?p2]]
             db nodetype-name) x
        (map first x)
        (first x)
        (d/pull db '[:* {:jcr.node/children [*]}] x )))
