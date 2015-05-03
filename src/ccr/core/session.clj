(ns ccr.core.session
  (:require [ccr.api.session]
            [ccr.core.node]
            [ccr.core.transaction-recorder :as tr]
            [datomic.api :as d  :only [q db]]
            ))

(defprotocol TransactionRecorder
 (current-db [this]
   "Returns the current value of db to execute queries")
 (record-tx [this tx]
   "Records the transaction tx to the recorder")
)

(deftype Session 
  [repository ws conn transaction-recorder-atom]
  
  ccr.api.session/Session
  (root-node [this]
    (let [db (current-db this)
          weid  (get ws :workspace-entity-id)
          eid   (->> (d/q '[:find ?r
                            :in $ ?weid
                            :where
                            [?weid :jcr.workspace/rootNode ?r]
                            ]                   
                          db
                          weid
                          )
                     ffirst)]
      (ccr.core.node/node this eid)))
  
  (workspace [this] ws)
  
  TransactionRecorder
  (current-db [this]
    (tr/current-db conn (deref transaction-recorder-atom)))

  (record-tx [this tx]
    (tr/apply-transaction conn transaction-recorder-atom tx))
  )






