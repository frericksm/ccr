(ns ccr.core.node
  (:require [ccr.api.node]
            [ccr.core.transaction-recorder :as tr]
            [ccr.core.model :as m]
            [ccr.core.transactor-support :as ts]
            [ccr.core.path :as p]
            [datomic.api :as d  :only [q db pull pull-many transact]]
            ))

(defn debug [m x] (println m x) x)

(declare new-node)
 

(defn find-new-child-id 
  "Returns the id of the newly added child to node with 'node-id' at rel-path"
  [tx-result node-id rel-path]
  (let [segments (p/to-path rel-path)
        parent-segments (drop-last segments)
        basename (last segments)
        
        db-before (:db-before tx-result)
        db-after  (:db-after tx-result)
        
        parent-node-id (ts/node-by-path db-before node-id parent-segments)
        child-nodes-before (->> (d/q (m/all-child-nodes parent-node-id) db-before)
                                (map first))
        child-nodes-after (->> (d/q (m/all-child-nodes parent-node-id) db-after)
                               (map first))
        diff (clojure.set/difference child-nodes-after child-nodes-before)]
    (if (= 1 (count diff))
      (first diff)
      (throw (IllegalStateException. "No or more than one childnode added!")))
    )
)

(deftype NodeImpl [session id]
  ccr.api.node/Node

  (add-node [this relPath]
    (ccr.api.node/add-node this relPath nil))

  (add-node [this relPath primaryNodeTypeName]
    (as-> [[:add-node id relPath primaryNodeTypeName]] x ;; transaction function :add-node
      (tr/record-tx session x)
      (find-new-child-id (:tx-result x) id relPath)
      (new-node session x)))
  
  (definition [this]
    )
  
  (identifier [this]
    )
  
  (index [this]
    )
  
  (mixinNodeTypes [this]
    )

  (node [this relPath]
    (ts/node-by-path session id (p/to-path relPath)))


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
    (let [db (tr/current-db session)]
      (as-> (m/node-name-query id) x
        (d/q x db)
        (map first x)
        (first x))))
 

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

(defn new-node [session id]
  (->NodeImpl session id))
