(ns ccr.core.system
  (:require [ccr.core.nodetype :as nt]
            [datomic.api :as d  :only [q db]]
            [ccr.core.repository :as crepository]))

(defn system
  "Returns a new instance of the whole application."
  []
  {:db-uri "datomic:mem://jcr"}
  )


(defn start
  "Performs side effects to initialize the system, acquire resources,
  and start it running. Returns an updated instance of the system."
  [system]
  (let [db-uri (:db-uri system)
        created (d/create-database db-uri)
        conn (d/connect db-uri)]
    (if created (crepository/create-schema conn))
    (nt/load-builtin-node-types conn)
    (as-> system x
         (assoc x :conn conn))))

(defn stop
  "Performs side effects to shut down the system and release its
  resources. Returns an updated instance of the system."
  [system]
  (d/delete-database (:db-uri system))
  (as-> system x
       (dissoc x :conn)))
