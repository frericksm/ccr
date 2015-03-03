(ns ccr.core.node-old
  (:require [ccr.core.path :as p])  
  (:require [datomic.api :as api])
  (:require [ccr.core.transaction-recorder :as ctr])
  )

(defprotocol Node
  "The node's protocol"
  (rel-node  [node rel-path]
    "Returns the identifier of the child node with relative path rel-path")
  (add-node  [node new-node-name]
    "Adds a node as child of parent")
  )

(defn add-node-transaction
  "Creates the add-node transaction"
  [parent-db-id new-node-name]
  (if (p/absolute-path? new-node-name)
    (throw (RuntimeException. "absolute path"))
    (let [identifier (api/squuid)]
      {:entity-ids [parent-db-id (api/tempid :db.part/user)]
       :trans-fn   (fn [& eids]
                     (let [parent-db-id (first eids)
                           child-id     (second eids)]
                       [{:db/id child-id
                         :item/name new-node-name
                         :item/node? true
                         :node/identifier identifier}
                        {:db/id parent-db-id
                         :node/children child-id}
                        ]))})))


(defn read-node
  "Returns the :db/id of the child node with :item/name equals to 'name'"
  [db node-db-id name]
  (->> (api/q '[:find ?child
                :in $data ?node ?name
                :where
                [$data ?node :node/children ?child]
                [$data ?child :item/name ?name]
                [$data ?child :item/node? true]
                ]
              db node-db-id name)
       ;;(map #(api/entity db (first %)))
       (map first)
       (first)))

(defrecord DatomicNode
    [session node-db-id]

  Node
  
  (rel-node  [node rel-path]
    (if (p/absolute-path? rel-path)
      (throw (RuntimeException. "no relative path"))
      (let [db (ctr/current-db (:connection session) @(:recording session))]
        (->> (p/to-path rel-path)
             (reduce (fn [a name] (read-node db a name))
                     (:node-db-id node))
             (node session)))))
  
  (add-node
    [node new-node-name]
    (->> (add-node-transaction (:node-db-id node) new-node-name)
         (ctr/apply-transaction session)))
  )

(defn node [session node-db-id]
  (->DatomicNode session node-db-id))

