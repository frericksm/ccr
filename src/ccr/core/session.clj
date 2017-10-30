(ns ccr.core.session
  (:require [ccr.api.session]
            [ccr.core.node]
            [ccr.core.workspace :as w]
            [ccr.core.transaction-recorder :as tr]
            [datomic.api :as d  :only [q db]]
            ))

(defrecord Session 
  [repository workspace-name conn transaction-recorder-atom]
  
  ccr.api.session/Session
  (root-node [this]
    (let [db (tr/current-db this)
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
      (ccr.core.node/new-node this eid)))
  
  (workspace [this] (w/workspace this workspace-name)))






