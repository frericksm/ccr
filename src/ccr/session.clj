(ns ccr.session
  (:require [ccr]
            [datomic.api :as d  :only [q db]]
            ))


(deftype Session [repository ws db]
  ccr/Session
  (root-node [this]
    (let [weid  (get ws :workspace-entitiy-id)]
      (->> (d/q '[:find ?r
                  :in $ ?weid
                  :where
                  [?weid :jcr.workspace/rootNode ?r]
                  ]                   
                db
                weid
                )
           (map #(d/entity db (first %)))    
           first)))
  (workspace [this] ws))






