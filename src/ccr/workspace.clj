(ns ccr.workspace
  (:require [datomic.api :as d  :only [q db]]
            [ccr.session :as s]
            [ccr]
            ))

(defrecord ImmutableWorkspace [workspace-name workspace-entitiy-id]
  ccr/Workspace)

(defn create-workspace [conn workspace-name]
  (as-> conn x
        (d/transact x
                    [{:db/id #db/id[:db.part/user -1]
                      :jcr.node/name ""}
                     {:db/id #db/id[:db.part/user -2]
                      :jcr.workspace/name workspace-name
                      :jcr.workspace/rootNode #db/id[:db.part/user -1]}])
        (deref x)
        (:db-after x)))

(defn workspace [db workspace-name]
  (as-> (d/q '[:find ?e 
               :in $ ?name
               :where 
               [?e :jcr.workspace/name ?name]
               ]                   
             db
             workspace-name)
        x
        (ffirst x)
        (->ImmutableWorkspace workspace-name x)
        ))
