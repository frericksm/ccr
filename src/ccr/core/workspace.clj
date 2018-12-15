(ns ccr.core.workspace
  (:require [ccr.api.workspace]
            [ccr.core.transaction-recorder :as tr]
            [ccr.core.nodetype :as nt]
            [datomic.api :as d  :only [q db]]))

(defrecord Workspace [session workspace-name workspace-entity-id]
  ccr.api.workspace/Workspace
  (node-type-manager [this]
    (nt/->NodeTypeManager session)))

(defn create-workspace [conn workspace-name]
  (let [t1 (d/tempid :db.part/user)
        t2 (d/tempid :db.part/user)
        t3 (d/tempid :db.part/user)
        t4 (d/tempid :db.part/user)]
    (as-> conn x
      (d/transact x
                  [{:db/id t4
                    :jcr.value/position 0
                    :jcr.value/string "nt:unstructured"}
                   {:db/id t3
                    :jcr.property/name "jcr:primaryType"
                    :jcr.property/value-attr :jcr.value/string
                    :jcr.property/values [t4]}
                   {:db/id t2
                    :jcr.node/name ""
                    :jcr.node/properties t3}
                   {:db/id t1
                    :jcr.workspace/name workspace-name
                    :jcr.workspace/rootNode t2}])
      (deref x)
      (:db-after x))))

(defn workspace [session workspace-name]
  (let [db (tr/current-db session)]    
    (as-> (d/q '[:find ?e 
                 :in $ ?name
                 :where 
                 [?e :jcr.workspace/name ?name]
                 ]                   
               db
               workspace-name)
        x
      (ffirst x)
      (->Workspace session workspace-name x)
      )))
