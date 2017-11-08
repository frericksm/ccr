(ns ccr.core.node
  (:require [ccr.api.node]
            [ccr.core.transaction-recorder :as transaction-recorder]
            [ccr.core.model :as model]
            [ccr.core.transactor-support :as transactor-support]
            [ccr.core.path :as path]
            [datomic.api :as datomic]
            ))

(defn debug [m x] (println m x) x)

(declare new-node)
(declare new-property)
 
(defn find-property [tx-result name])

(defn find-new-child-id 
  "Returns the id of the newly added child to node with 'node-id' at rel-path"
  [tx-result node-id rel-path]
  (let [segments (path/to-path rel-path)
        parent-segments (drop-last segments)
        basename (last segments)
        
        db-before (:db-before tx-result)
        db-after  (:db-after tx-result)
        
        parent-node-id (transactor-support/node-by-path db-before node-id parent-segments)
        child-nodes-before (->> (datomic/q (model/all-child-nodes parent-node-id) db-before)
                                (map first)
                                (set))
        child-nodes-after (->> (datomic/q (model/all-child-nodes parent-node-id) db-after)
                               (map first)
                               (set))
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
    (as-> [[:add-node id (datomic/tempid :db.part/user) relPath primaryNodeTypeName]] x ;; transaction function :add-node
      (transaction-recorder/record-tx session x)
      (find-new-child-id (:tx-result x) id relPath)
      (new-node session x)))
  
  (definition [this])
  
  (identifier [this]
    (str id))
  
  (index [this])
  
  (mixinNodeTypes [this])

  (node [this relPath]
    (let [db (transaction-recorder/current-db session)]
      (as-> (transactor-support/node-by-path db id (path/to-path relPath)) x
        (new-node session x))))


  (nodes [this]
    (let [db (transaction-recorder/current-db session)]
      (as-> (transactor-support/nodes db id) x
        (map (partial new-node session) x))))

  (nodes [this namePattern])

  (primary-item [this])

  (primary-nodetype [this])

  (property [this relPath]
    (let [db (transaction-recorder/current-db session)]
      (as-> (transactor-support/item-by-path db id (path/to-path relPath)) x
        (new-property session x))))

  (properties [this]
    (let [db (transaction-recorder/current-db session)]
      (as-> (transactor-support/properties db id) x
        (map (partial new-property session) x))))  
  
  (set-property-value [this name value jcr-type]
    ;; transaction function :set-property
    (as-> [[:set-property id (datomic/tempid :db.part/user) name [value] jcr-type false]] x 
      (transaction-recorder/record-tx session x)
      (find-property (:tx-result x) name)
      (new-property session x)))

  (set-property-values [this name values jcr-type]
    ;; transaction function :set-property
    (as-> [[:set-property id (datomic/tempid :db.part/user) name values jcr-type true]] x 
      (transaction-recorder/record-tx session x)
      (find-property (:tx-result x) name)
      (new-property session x)))

  ccr.api.node/Item

  (ancestor [this depth])

  (depth [this])

  (item-name [this]
    (let [db (transaction-recorder/current-db session)]
      (as-> (model/node-name-query id) x
        (datomic/q x db)
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


(deftype PropertyImpl [session id]
  ccr.api.node/Property

  ccr.api.node/Item

  (ancestor [this depth])

  (depth [this])

  (item-name [this]
    (let [db (transaction-recorder/current-db session)]
      (as-> (model/property-name-query (debug "id" id)) x
        (debug "q" x)
        (datomic/q x db)
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
  
  (value [this]
    (let [db (transaction-recorder/current-db session)]
      (model/first-value db id))
    )

  (values [this]
    (let [db (transaction-recorder/current-db session)]
      (model/values db id))
    )
  )

(defn new-property [session id]
  (->PropertyImpl session id))
