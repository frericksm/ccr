(ns ccr.core.session
  (:require [ccr.api.session]
            [ccr.core.node]
            [datomic.api :as d  :only [q db]]
            ))


(deftype Session [repository ws db]
  ccr.api.session/Session
  (root-node [this]
    (let [weid  (get ws :workspace-entitiy-id)
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
  
  (workspace [this] ws))






