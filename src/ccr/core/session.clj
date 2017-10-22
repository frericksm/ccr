(ns ccr.core.session
  (:require [ccr.api.session]
            [ccr.core.node]
            [ccr.core.workspace :as w]
            [ccr.core.transaction-recorder :as tr]
            [datomic.api :as d  :only [q db]]
            ))

(deftype Session 
  [repository workspace-name conn transaction-recorder-atom]
  
  ccr.api.session/Session
  (root-node [this]
    (let [db (tr/calc-current-db conn (deref transaction-recorder-atom))
          weid  (get (w/workspace this workspace-name) :workspace-entity-id)
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
  
  (workspace [this] (w/workspace this workspace-name))
  
  tr/TransactionRecorder
  (current-db [this]
    (tr/calc-current-db conn (deref transaction-recorder-atom)))

  (record-tx [this tx]
    (tr/apply-transaction conn transaction-recorder-atom tx))
  )






