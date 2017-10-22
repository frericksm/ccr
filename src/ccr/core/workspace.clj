(ns ccr.core.workspace
  (:require [ccr.api.workspace]
            [ccr.core.transaction-recorder :as tr]
            [ccr.core.nodetype :as nt]
            [datomic.api :as d  :only [q db]]))

(defrecord Workspace [session workspace-name workspace-entity-id]
  ccr.api.workspace/Workspace
  (node-type-manager [this]
    (nt/->NodeTypeManager (tr/calc-current-db session))))

(defn create-workspace [conn workspace-name]
  (as-> conn x
        (d/transact x
                    [{:db/id #db/id[:db.part/user -4]
                      :jcr.value/position 0
                      :jcr.value/string "nt:unstructured"}
                     {:db/id #db/id[:db.part/user -3]
                      :jcr.property/name "jcr:primaryType"
                      :jcr.property/value-attr :jcr.value/string
                      :jcr.property/values #db/id[:db.part/user -4]}
                     {:db/id #db/id[:db.part/user -2]
                      :jcr.node/name ""
                      :jcr.node/properties #db/id[:db.part/user -3]}
                     {:db/id #db/id[:db.part/user -1]
                      :jcr.workspace/name workspace-name
                      :jcr.workspace/rootNode #db/id[:db.part/user -2]}])
        (deref x)
        (:db-after x)))

(defn workspace [session workspace-name]
  (as-> (d/q '[:find ?e 
               :in $ ?name
               :where 
               [?e :jcr.workspace/name ?name]
               ]                   
             (tr/calc-current-db session) 
             workspace-name)
        x
        (ffirst x)
        (->Workspace session workspace-name x)
        ))
