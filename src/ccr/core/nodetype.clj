(ns ccr.core.nodetype
  (:require [ccr.api.nodetype]
            [ccr.core.cnd :as cnd]
            [ccr.core.transaction-recorder :as tr]
            [datomic.api :as d  :only [q db]]
            ))

(defn idp [m x] (println m x) x)

(declare nodetype)

(defn load-builtin-node-types
  "Loads the builtin nodetypes to datomic connection"
  [connection]
  (as-> (cnd/builtin-nodetypes) x
    (cnd/node-to-tx x)
    (d/transact connection x)))

(defn ^:private merge-queries
  "Merges datomic queries. The queries are exptected to be in map form"
  [& queries]
  (apply merge-with into queries))


;; https://www.youtube.com/watch?v=YHctJMUG8bI 
;; Queries as data

(defn ^:private property-value-query 
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

(defn ^:private child-node-query 
  "Returns a partial datomic query in map form containing only a where clause that interconnects the :jcr.node entity ?e and its childnode entities ?c"
  [?e ?c]
  {:where [[?e :jcr.node/children ?c]]})

(defn ^:private child-node-name-query 
  "Returns a partial datomic query in map form containing only a where clause that interconnects the :jcr.node entity ?e and its childnode entities ?c and the childnodes name ?name"
  [?e ?c ?name]
  {:where [[?e :jcr.node/children ?c]
           [?c :jcr.node/name ?name]]})

(defn ^:private node-type-query 
  "Returns a partial datomic query in map form containing only a where clause that interconnects the :jcr.node entity ?e and its property entity value ?v"
  [?e ?v]
  (merge-queries
   (property-value-query ?e "jcr:primaryType" "nt:nodeType")
   (property-value-query ?e "jcr:nodeTypeName" ?v)))

(defn ^:private declaring-node-type-name-query 
  "Returns a partial datomic query in map form containing only a where clause that interconnects the :jcr.node entity ?e and its property entity value ?v"
  [child-entity-id]
  (let [?e (gensym "?e")
        ?v (gensym "?v")]
    (merge-queries {:find [?v]}
                   (child-node-query ?e child-entity-id)
                   (property-value-query ?e "jcr:primaryType" "nt:nodeType")
                   (property-value-query ?e "jcr:nodeTypeName" ?v))))

(defn ^:private property-query [?e property-name]
  (let [?pv (gensym "?pv")]
    (merge-queries {:find [?pv]}
                   (property-value-query ?e property-name ?pv))))

(defn ^:private nodetype-property-query [nodetype-name property-name]
  (let [?pv (gensym "?pv")
        ?e  (gensym "?e")]  
    (merge-queries {:find [?pv]}
                   (property-value-query ?e property-name ?pv)
                   (node-type-query ?e nodetype-name))))

(defn ^:private nodetype-child-query [nodetype-name childname]
  (let [?c (gensym "?c")
        ?e (gensym "?e")]
    (merge-queries {:find [?c]}
                   (child-node-name-query ?e ?c childname)
                   (node-type-query ?e nodetype-name))))

(defn ^:private parent-query [child-entity-id]
  (let [?e (gensym "?e")]
    (merge-queries {:find [?e]}
                   (child-node-query ?e child-entity-id))))

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
    (merge-queries {:find [?e]}
                   (node-type-query ?e nodetype-name))))

(defn ^:private exists? [db nodetype-name]
  (as-> nodetype-name x
    (exists-query x)
    (d/q x db) 
    (empty? x)
    (not x)))

(defn ^:private all-property-values [db id property-name]
  (as-> id x
    (property-query x property-name)
    (d/q x db) 
    (map first x)))

(defn ^:private first-property-value [db id property-name]
  (as-> (all-property-values db id property-name) x
    (first x)))

(defn ^:private declaring-node-type-by-item-id [db id]
  (as-> (declaring-node-type-name-query id) x
    (d/q x db) 
    (map first x)
    (first x)
    (nodetype db x)))

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
    (declaring-node-type-by-item-id db id))

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
    (declaring-node-type-by-item-id db id))

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

(defn ^:private read-child-node-definitions [db node-type-name]
      (as-> node-type-name x
        (nodetype-child-query x "jcr:childNodeDefinition")
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
    (let [nd (ccr.api.nodetype/child-node-definitions this)
          name-match-cndefs (filter (fn [cd] 
                                      (and (= childNodeName
                                              (ccr.api.nodetype/child-item-name cd))
                                           (contains? (ccr.api.nodetype/required-primary-type-names cd) nodeTypeName))) nd)
          residuals (filter (fn [cd] 
                              (and (= "*"
                                       (ccr.api.nodetype/child-item-name cd))
                                   (contains? (ccr.api.nodetype/required-primary-type-names cd) nodeTypeName))) nd)]
      (or (= 1 (count name-match-cndefs))
          (and (= 0 (count name-match-cndefs))
               (= 1 (count residuals))))))

  (can-remove-node? [this nodeName]
    )

  (can-remove-property? [this propertyName]
    )

  (can-set-property? [this propertyName & values]
    )

  (child-node-definitions [this]
    ;; (see https://docs.adobe.com/content/docs/en/spec/jcr/2.0/3_Repository_Model.html#3.7.6.8 Item Definitions in Subtypes)
    (let [st-tree (idp "tree" (calc-supertype-tree db node-type-name))
          cndefs (idp "tree2" (as-> st-tree x
                                (keys x)
                                (map (fn [nt-name] (vector nt-name
                                                           (read-child-node-definitions db nt-name))) x)
                                (into {} x))) ;;child-node-defintiion je nodetype
          ]
      (loop [result []
             next-to-merge [node-type-name]]
        (if (empty? next-to-merge)
          result
          (let [merge-candidates (as-> next-to-merge x
                                   (map (fn [n] (get cndefs n)) x)
                                   (flatten x))
                new_next-to-merge (as-> next-to-merge x
                                   (map (fn [n] (get st-tree n)) x)
                                   (flatten x)) 


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
