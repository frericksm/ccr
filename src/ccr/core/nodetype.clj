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
  "Merges datomic queries. The queries are exptected to be in map form"
  [& queries]
  (apply merge-with into queries))


;; https://www.youtube.com/watch?v=YHctJMUG8bI 
;; Queries as data

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

(defn child-node-query 
  "Returns a partial datomic query in map form containing only a where clause that interconnects the :jcr.node entity ?e and its childnode entities ?c"
  [?e ?c ?name]
  {:where [[?e :jcr.node/children ?c]]})

(defn child-node-name-query 
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
                   (child-node-name-query ?e ?c childname)
                   (node-type-query ?e nodetype-name))))

(defn parent-query [child-entity-id]
  (let [?e (gensym "?e")]
    (merge-queries {:find [?e]}
                   (child-node-query ?e child-entity-id))))

(defn calc-supertype-names [db node-type]
  (loop [dst1 (set (ccr.api.nodetype/declared-supertype-names node-type))]
    (let [dst2 (as-> dst1 x
                 (map (fn [supertyname] (nodetype db supertyname)) x)
                 (map (fn [nt] 
                        (ccr.api.nodetype/declared-supertype-names nt)) x)
                 (apply concat x)
                 (set x))
          dst3 (clojure.set/union dst1 dst2)]
      (if (= (count dst1) (count dst3))
        dst1
        (recur dst3)))))


(defn all-property-values [db id property-name]
  (as-> id x
    (property-query x property-name)
    (d/q x db) 
    (map first x)))

(defn first-property-value [db id property-name]
  (as-> (all-property-values db id property-name) x
    (first x)))

(defrecord NodeDefinition [db id]

  ccr.api.nodetype/NodeDefinition

  (allows-same-name-siblings? [this]
    (first-property-value db id "jcr:sameNameSiblings"))

  (default-primary-type [this]
    (first-property-value db id "jcr:defaultPrimaryType"))

  (required-primary-type-names [this]
    (as-> "jcr:requiredPrimaryTypes" x
      (all-property-values db id x)
      (set x)))

  (required-primary-types [this]
    (as-> (ccr.api.nodetype/required-primary-type-names this) x
      (map (fn [name] (nodetype db name)) x)
      (set x)))

  ccr.api.nodetype/ItemDefinition

  (declaring-node-type [this]
    (as-> (parent-query id) x
      (d/q x db) 
      (map first x)
      (first x)
      (nodetype db x)))

  (child-item-name [this] 
    (first-property-value db id "jcr:name"))

  (on-parent-version [this]
    (first-property-value db id "jcr:onParentVersion"))
  
  (auto-created? [this]
    (first-property-value db id "jcr:autoCreated"))

  (mandatory? [this]
    (first-property-value db id "jcr:mandatory"))

  (protected? [this]
    (first-property-value db id "jcr:protected")))

(defrecord PropertyDefinition [db id]

  ccr.api.nodetype/PropertyDefinition

  ccr.api.nodetype/ItemDefinition
  (declaring-node-type [this]
    (as-> (parent-query id) x
      (d/q x db) 
      (map first x)
      (first x)
      (nodetype db x)))

  (child-item-name [this] 
    (first-property-value db id "jcr:name"))

  (on-parent-version [this]
    (first-property-value db id "jcr:onParentVersion"))
  
  (auto-created? [this]
    (first-property-value db id "jcr:autoCreated"))

  (mandatory? [this]
    (first-property-value db id "jcr:mandatory"))

  (protected? [this]
    (first-property-value db id "jcr:protected")))

(defrecord NodeType [db node-type-name]
  ccr.api.nodetype/NodeType

  (can-add-child-node?
    [this childNodeName]
    (as-> (ccr.api.nodetype/declared-child-node-definitions this) x
      (filter (fn [nd] (or (= (ccr.api.nodetype/child-item-name nd) childNodeName)
                           (= (ccr.api.nodetype/child-item-name nd) "*"))) x)
      (not (empty? x))))
  
  (can-add-child-node?
    [this childNodeName nodeTypeName]
    (let [supertypes-of-nodeTypeName 
          (as-> nodeTypeName y
            (nodetype db y)
            (ccr.api.nodetype/supertypes y)
            (map (fn [nt] (ccr.api.nodetype/node-type-name nt)) y)
            (cons nodeTypeName y)
            (set y))]
      (as-> (ccr.api.nodetype/declared-child-node-definitions this) x
        (filter (fn [nd] (as-> nd z
                           (clojure.set/intersection 
                            (ccr.api.nodetype/required-primary-type-names z)
                            supertypes-of-nodeTypeName)
                          (empty? z)
                          (not z))) x)
        (filter (fn [nd] 
                  (or (= (ccr.api.nodetype/child-item-name nd) 
                         childNodeName)
                      (= (ccr.api.nodetype/child-item-name nd) 
                         "*"))) x)
        (empty? x)
        (not x))))

  (supertypes [this]
    (let [mix      (ccr.api.nodetype/mixin? this)]
      (as-> (calc-supertype-names db this) x
        (map (fn [supertype-name] (nodetype db supertype-name)) x)
        (if mix x (cons (nodetype db "nt:base") x)))))

  (node-type? [this nodeTypeName]
    (as-> (ccr.api.nodetype/supertypes this) x
          (cons this x)
          (map (fn [nt] (ccr.api.nodetype/node-type-name nt)) x)
          (set x)
          (contains? x nodeTypeName)))
  
  ccr.api.nodetype/NodeTypeDefinition

  (node-type-name [this]
    node-type-name)
  
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


