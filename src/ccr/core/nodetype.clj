(ns ccr.core.nodetype
  (:require [ccr.api.nodetype]
            [ccr.core.cnd :as cnd]
            [ccr.core.model :as m]
            [ccr.core.transactor-support :as ts]
            [ccr.core.transaction-recorder :as tr]
            [datomic.api :as d  :only [q db pull pull-many transact]]
            ))

(defn debug [m x] (println m x) x)

(declare nodetype)

(defn load-builtin-node-types
  "Loads the builtin nodetypes to datomic connection"
  [connection]
  (as-> (cnd/builtin-nodetypes) x
    (cnd/node-to-tx x)
    (d/transact connection x)))

(defn ^:private declaring-node-type-name-query 
  "Returns a partial datomic query in map form containing only a where clause that interconnects the :jcr.node entity ?e and its property entity value ?v"
  [child-entity-id]
  (let [?e (gensym "?e")
        ?v (gensym "?v")]
    (m/merge-queries {:find [?v]}
                   (m/child-node-query ?e child-entity-id)
                   (m/property-value-join-query ?e "jcr:primaryType" "nt:nodeType")
                   (m/property-value-join-query ?e "jcr:nodeTypeName" ?v))))

(defn ^:private declaring-node-type-by-item-id [db id]
  (as-> (declaring-node-type-name-query id) x
    (d/q x db) 
    (map first x)
    (first x)
    (nodetype db x)))

(defrecord NodeDefinition [db id]

  ccr.api.nodetype/NodeDefinition

  (allows-same-name-siblings? [this]
    (m/first-property-value db id "jcr:sameNameSiblings"))

  (default-primary-type [this]
    (ts/default-primary-type db id))

  (required-primary-type-names [this]
    (ts/required-primary-type-names db id))

  (required-primary-types [this]
    (as-> (ts/required-primary-type-names db id) x
      (map (fn [name] (nodetype db name)) x)
      (set x)))

  ccr.api.nodetype/ItemDefinition

  (declaring-node-type [this]
    (declaring-node-type-by-item-id db id))

  (child-item-name [this]
    (ts/child-item-name db id))

  (on-parent-version [this]
    (m/first-property-value db id "jcr:onParentVersion"))
  
  (auto-created? [this]
    (ts/autocreated? db id))

  (mandatory? [this]
    (ts/mandatory? db id))

  (protected? [this]
    (m/first-property-value db id "jcr:protected")))

(defrecord PropertyDefinition [db id]

  ccr.api.nodetype/PropertyDefinition

  ccr.api.nodetype/ItemDefinition
  (declaring-node-type [this]
    (declaring-node-type-by-item-id db id))

  (child-item-name [this]
    (ts/child-item-name db id))

  (on-parent-version [this]
    (m/first-property-value db id "jcr:onParentVersion"))
  
  (auto-created? [this]
    (ts/autocreated? db id))

  (mandatory? [this]
    (ts/mandatory? db id))

  (protected? [this]
    (m/first-property-value db id "jcr:protected")))



(defrecord NodeType [db node-type-name]
  ccr.api.nodetype/NodeType

  (can-add-child-node?
    [this childNodeName]
    (ts/can-add-child-node? db node-type-name childNodeName))
  
  (can-add-child-node?
    [this childNodeName nodeTypeName]
    (ts/can-add-child-node? db node-type-name childNodeName nodeTypeName))

  (can-remove-node? [this nodeName])

  (can-remove-property? [this propertyName]
    )

  (can-set-property? [this propertyName & values]
    )

  (child-node-definitions 
    [this]
    (as-> (ts/child-node-definition-ids db node-type-name) x
      (map (fn [id] (->NodeDefinition db id)) x)))
  
  (supertypes [this]
    (let [mix (ccr.api.nodetype/mixin? this)]
      (as-> (ts/supertype-names db node-type-name) x 
        (map (fn [supertype-name] (nodetype db supertype-name)) x)
        )))

  (node-type? [this nodeTypeName]
    (as-> (ts/supertype-names db node-type-name) x
          (cons node-type-name x)
          (set x)
          (contains? x nodeTypeName)))
  
  ccr.api.nodetype/NodeTypeDefinition

  (node-type-name [this]
    node-type-name)
  
  (declared-supertype-names [this]
    (ts/declared-supertype-names db node-type-name)
    )

  (abstract? [this]
    (as-> node-type-name x
      (m/nodetype-property-query x "jcr:isAbstract")
      (d/q x db) 
      (map first x)
      (first x)
      (true? x)))

  (mixin? [this]
    (ts/mixin? db node-type-name))

  (orderable-child-nodes? [this]
    (as-> node-type-name x
      (m/nodetype-property-query x "jcr:hasOrderableChildNodes")
      (d/q x db) 
      (map first x)
      (first x)
      (true? x)))

  (primary-item-name [this]
    (as-> node-type-name x
      (m/nodetype-property-query x "jcr:primaryItemName")
      (d/q x db) 
      (map first x)
      (first x)))

  (declared-property-definitions [this]
    (as-> (ts/read-property-definition-ids
           db node-type-name) x
         (map (fn [id] (->PropertyDefinition db id)) x)))

  (declared-child-node-definitions [this]
    (as-> (ts/read-child-node-definition-ids
           db node-type-name) x
      (map (fn [id] (->NodeDefinition db id)) x))))

(defn nodetype [db nodetype-name]
  (cond (nil? nodetype-name) 
        (throw (IllegalArgumentException. 
                "Parameter 'nodetype-name' must not be null"))
        (not (ts/exists? db nodetype-name))
        (throw (IllegalArgumentException. 
                (format "Nodetype %s does not exist" nodetype-name))))
  (->NodeType db nodetype-name))


(defrecord NodeTypeManager [session]
  ccr.api.nodetype/NodeTypeManager
  
  (node-type [this nodeTypeName]
    (let [db (tr/current-db session)]    
      (nodetype db nodeTypeName))
    ))
