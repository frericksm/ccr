(ns ccr.core.node
  (:require [ccr.api.node]
            [ccr.core.transaction-recorder :as tr]
            [ccr.core.transaction-utils :as tu]
            [ccr.core.model :as m]
            [ccr.core.model-trans :as mt]
            [ccr.core.nodetype :as n]
            [ccr.core.path :as p]
            [ccr.api.nodetype]
            [datomic.api :as d  :only [q db pull pull-many transact]]
            ))

(defn debug [m x] (println m x) x)

(declare node)
 
(defn childnode-id-by-name
  "Returns the id of the child node with 'name' of node with parent-node-id"
  [db parent-node-id name]
  (let [g (re-find  #"(\w+)(\[(\d+)\])?" name)
        basename (second g)
        index (if (= 4 (count g)) (Integer/parseInt (nth g 3)) 0)]
    (as-> (m/child-query parent-node-id basename index) x
      (d/q x)
      (map first x))))

(defn ^:private matching-cnd
  [cnds basename primary-node-type]
  (as-> cnds x
    (filter (fn [c]
              (let [cin (ccr.api.nodetype/child-item-name c)
                    rpt (ccr.api.nodetype/required-primary-type-names c)
                    cin-residual? (= "*" cin)
                    cin-matches? (= basename cin)
                    covers-nt? (contains? (set rpt) primary-node-type)]
                (or (and  cin-residual? covers-nt?)
                    (and  cin-matches? covers-nt?)))
              ))
    ))

(defn ^:private autocreated-childnodes
  [db childnode-definition]
  []
  )

(defn ^:private autocreated-properties
  [db childnode-definition]
  []
  )

(defn ^:private add-node*
  "Add a node named 'basename' of nodetype 'primary-node-type' to the ccr.api.node/Node 'parent-node'"
  [db parent-node-id basename primary-node-type]
  (let [pnt (m/first-property-value db parent-node-id "jcr:primaryType")
        cnds (ccr.api.nodetype/child-node-definitions (n/nodetype db pnt))
        matching-cnd (matching-cnd cnds basename primary-node-type)
        autocreated-childnodes (autocreated-childnodes db matching-cnd)
        autocreated-properties (autocreated-properties db matching-cnd)]
    (mt/node basename autocreated-childnodes autocreated-properties))
  )

(defn ^:private node-by-path
  "Returns the child node of 'parent-node' denoted by 'rel-path-segments' (the result of ccr.core.path/to-path)"
  [db parent-node-id rel-path-segments]

  (loop [node-id  parent-node-id
         path-segments rel-path-segments]
    (if (nil? node-id)
      (throw (IllegalArgumentException. "Path not found"))
      (if (empty? path-segments)
        node-id
        (let [segment (first path-segments)
              new_path-segments (rest path-segments)
              new_node-id (childnode-id-by-name db node-id segment)]
          (recur new_node-id
                 new_path-segments))))))

(deftype NodeImpl [session id]
  ccr.api.node/Node

  (add-node [this relPath]
    (ccr.api.node/add-node this relPath nil))

  (add-node [this relPath primaryNodeTypeName]
    (let [db (tr/current-db session)
          segments (p/to-path relPath)
          parent-segments (drop-last segments)
          basename (last segments)
          parent-of-new-node (node-by-path db id parent-segments)]
      (as-> (add-node* db parent-of-new-node
                       basename primaryNodeTypeName) x
        (tu/translate-value x)
        (let [child-id (first x)
              child-node-tx (second x)]
          (as-> (concat (tu/add-tx id child-id) child-node-tx) y
            (tr/record-tx session y)
            (d/resolve-tempid (debug "db-after" (->> y :tx-result :db-after))
                              (debug "temp-ids" (->> y :tx-result :tempids))
                              child-id))
          (node session x)))))
  
  (definition [this]
    )
  
  (identifier [this]
    )
  
  (index [this]
    )
  
  (mixinNodeTypes [this]
    )

  (node [this relPath]
    (node-by-path session id (p/to-path relPath)))


  (nodes [this]
       )

  (nodes [this namePattern]
    )
  (primary-item [this]
    )

  (primary-nodetype [this]
    )
  

  
  ccr.api.node/Item

  (ancestor [this depth]
    )

  (depth [this]
    )

  (item-name [this]
    )
 

  (parent [this]
    )

  (path [this]
    )

  (session [this]
    )

  (modified? [this]
    )

  (new? [this]
    )

  (node? [this]
    )

  (same? [this otherItem]
    )

  (refresh [this keepChanges]
    )
  (remove-item [this]
    )

  )

(defn node [session id]
   (->NodeImpl session id))
