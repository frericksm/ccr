(ns ccr.core.nodetype
  (:require [ccr.api.nodetype]
            [ccr.core.cnd :as cnd]
            [ccr.core.model :as m]
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
                   (m/property-value-query ?e "jcr:primaryType" "nt:nodeType")
                   (m/property-value-query ?e "jcr:nodeTypeName" ?v))))




(defn ^:private calc-supertype-names [db node-type]
  (loop [dst1 (ccr.api.nodetype/declared-supertype-names node-type)]
    (let [dst2 (as-> dst1 x
                 (map (fn [supertype-name] (nodetype db supertype-name)) x)
                 (map (fn [nt] 
                        (ccr.api.nodetype/declared-supertype-names nt)) x)
                 (apply concat x)
                 (set x))]
      (if (= (set dst1) (clojure.set/union (set dst1) dst2))
        dst1
        (recur (concat dst1 dst2))))))

(defn ^:private supertype-names [db node-type-name]
  (let [nt (nodetype db node-type-name)
        mix (ccr.api.nodetype/mixin? nt)]
      (as-> (calc-supertype-names db nt) x
        (if mix x (cons "nt:base" x)))))

(defn ^:private calc-supertype-tree
  "Liefert zum node-type-name eine Map, die die Supertypestruktur abbildet. E.g
  {node-type-name [supertype1 supertype1]
   supertype1 [supertype3]
   supertype2 []
   supertype3 []}"
  [db node-type-name]
  (loop [h {::root [node-type-name]}]
    (let [not-visited (clojure.set/difference (set (flatten (vals h)))
                                              (set (keys h)))]
      (if (empty? not-visited)
        (dissoc h ::root)
        (let [new_h (reduce (fn [a v]
                              (let [dsn (ccr.api.nodetype/declared-supertype-names
                                          (nodetype db v))]
                                (assoc a v dsn)))
                            h
                            not-visited
                            )]
          (recur new_h))))))

(defn ^:private exists-query [nodetype-name]
  (let [?e  (gensym "?e")]  
    (m/merge-queries {:find [?e]}
                   (m/node-type-query ?e nodetype-name))))

(defn ^:private exists? [db nodetype-name]
  (as-> nodetype-name x
    (exists-query x)
    (d/q x db) 
    (empty? x)
    (not x)))

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
    (m/first-property-value db id "jcr:defaultPrimaryType"))

  (required-primary-type-names [this]
    (as-> "jcr:requiredPrimaryTypes" x
      (m/all-property-values db id x)
      (set x)))

  (required-primary-types [this]
    (as-> (ccr.api.nodetype/required-primary-type-names this) x
      (map (fn [name] (nodetype db name)) x)
      (set x)))

  ccr.api.nodetype/ItemDefinition

  (declaring-node-type [this]
    (declaring-node-type-by-item-id db id))

  (child-item-name [this] 
    (m/first-property-value db id "jcr:name"))

  (on-parent-version [this]
    (m/first-property-value db id "jcr:onParentVersion"))
  
  (auto-created? [this]
    (m/first-property-value db id "jcr:autoCreated"))

  (mandatory? [this]
    (m/first-property-value db id "jcr:mandatory"))

  (protected? [this]
    (m/first-property-value db id "jcr:protected")))

(defrecord PropertyDefinition [db id]

  ccr.api.nodetype/PropertyDefinition

  ccr.api.nodetype/ItemDefinition
  (declaring-node-type [this]
    (declaring-node-type-by-item-id db id))

  (child-item-name [this] 
    (m/first-property-value db id "jcr:name"))

  (on-parent-version [this]
    (m/first-property-value db id "jcr:onParentVersion"))
  
  (auto-created? [this]
    (m/first-property-value db id "jcr:autoCreated"))

  (mandatory? [this]
    (m/first-property-value db id "jcr:mandatory"))

  (protected? [this]
    (m/first-property-value db id "jcr:protected")))

(defn ^:private read-child-node-definitions [db node-type-name]
      (as-> node-type-name x
        (m/nodetype-child-query x "jcr:childNodeDefinition")
        (d/q x db) 
        (map first x)
        (map (fn [id] (->NodeDefinition db id)) x)))

(defrecord NodeType [db node-type-name]
  ccr.api.nodetype/NodeType

  (can-add-child-node?
    [this childNodeName]
    (let [nd (ccr.api.nodetype/child-node-definitions this)
          name-match-cndefs (filter (fn [cd] 
                                      (= childNodeName
                                         (ccr.api.nodetype/child-item-name cd))) nd)
          residuals (filter (fn [cd] 
                              (= "*"
                                 (ccr.api.nodetype/child-item-name cd))) nd)]
      (or (= 1 (count name-match-cndefs))
          (and (= 0 (count name-match-cndefs))
               (= 1 (count residuals))))))
  
  (can-add-child-node?
    [this childNodeName nodeTypeName]
    (let [type-hierarchy (set (cons nodeTypeName (supertype-names db nodeTypeName)))
          nd (ccr.api.nodetype/child-node-definitions this)
          name-match-cndefs (filter (fn [cd] 
                                      (and (= childNodeName
                                              (ccr.api.nodetype/child-item-name cd))
                                           (not (empty? 
                                                 (clojure.set/intersection (set (ccr.api.nodetype/required-primary-type-names cd)) 
                                                                           type-hierarchy))))) nd)
          residuals (filter (fn [cd] 
                              (and (= "*"
                                       (ccr.api.nodetype/child-item-name cd))
                                   (not (empty? 
                                         (clojure.set/intersection (set (ccr.api.nodetype/required-primary-type-names cd)) 
                                                                   type-hierarchy))))) nd)]
      (or (= 1 (count name-match-cndefs))         ;; exactly 1 node definition to the childNodeName 
          (and (= 0 (count name-match-cndefs))    ;; no node definition to the childNodeName
               (= 1 (count residuals))))))        ;; exactly 1 residual node definition

  (can-remove-node? [this nodeName]
    )

  (can-remove-property? [this propertyName]
    )

  (can-set-property? [this propertyName & values]
    )

  (child-node-definitions 
    ;;(see https://docs.adobe.com/content/docs/en/spec/jcr/2.0/3_Repository_Model.html#3.7.6.8 Item Definitions in Subtypes)
    [this]
    (let [st-tree (calc-supertype-tree db node-type-name)
          cndefs (as-> st-tree x
                   (keys x)
                   (map (fn [nt-name] (vector nt-name
                                              (read-child-node-definitions db nt-name))) x)
                   (into {} x)) ;;child-node-defintiion je nodetype
          ]
      (loop [result []
             next-to-merge [node-type-name]]
        (if (empty? next-to-merge)
          result
          (let [merge-candidates (as-> next-to-merge x
                                   (map (fn [n] (get cndefs n)) x)
                                   (apply concat x))
                new_next-to-merge (as-> next-to-merge x
                                   (map (fn [n] (get st-tree n)) x)
                                   (apply concat x)) 


                new_result (as-> merge-candidates x
                             (filter 
                              (fn [mc] 
                                (let [all-names (as-> result y
                                                  (map ccr.api.nodetype/child-item-name y)
                                                  (set y))
                                      mc-name (ccr.api.nodetype/child-item-name mc)]
                                  (or (= mc-name "*") ;;residual 
                                      (not (contains? all-names mc-name))))) x) 
                             (concat result x))]
            (recur new_result new_next-to-merge))))))
  
  (supertypes [this]
    (let [mix (ccr.api.nodetype/mixin? this)]
      (as-> (supertype-names db node-type-name) x 
        (map (fn [supertype-name] (nodetype db supertype-name)) x)
        )))

  (node-type? [this nodeTypeName]
    (as-> (supertype-names db node-type-name) x
          (cons node-type-name x)
          (set x)
          (contains? x nodeTypeName)))
  
  ccr.api.nodetype/NodeTypeDefinition

  (node-type-name [this]
    node-type-name)
  
  (declared-supertype-names [this]
    (as-> node-type-name x
      (m/nodetype-property-query x "jcr:supertypes")
      (d/q x db) 
      (map first x)))

  (abstract? [this]
    (as-> node-type-name x
      (m/nodetype-property-query x "jcr:isAbstract")
      (d/q x db) 
      (map first x)
      (first x)
      (true? x)))

  (mixin? [this]
    (as-> node-type-name x
      (m/nodetype-property-query x "jcr:isMixin")
      (d/q x db) 
      (map first x)
      (first x)
      (true? x)))

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
    (as-> node-type-name x
      (m/nodetype-child-query x "jcr:propertyDefinition")
      (d/q x db) 
      (map first x)
      (map (fn [id] (->PropertyDefinition db id)) x)))

  (declared-child-node-definitions [this]
    (read-child-node-definitions db node-type-name)
    )
  )

(defn nodetype [db nodetype-name]
  (if (not (exists? db nodetype-name))
    (throw (IllegalArgumentException. 
            (format "Nodetype %s does not exist" nodetype-name)))
    (->NodeType db nodetype-name)))


(defrecord NodeTypeManager [session]
  ccr.api.nodetype/NodeTypeManager
  
  (node-type [this nodeTypeName]
    (let [db (tr/current-db session)]    
      (nodetype db nodeTypeName))
    ))
