(ns ccr.core.system
  (:require [ccr.core.nodetype :as nt]
            [ccr.api]
            [datomic.api :as d  :only [q db]]
            [ccr.core.repository :as crepository]))

(defn system
  "Returns a new instance of the whole application."
  []
  {"ccr.datomic.uri" "datomic:mem://jcr"}
  )


(defn start
  "Performs side effects to initialize the system, acquire resources,
  and start it running. Returns an updated instance of the system."
  [system]
  (let [repository (ccr.api/repository system)]
    (assoc system :repository repository)))

(defn stop
  "Performs side effects to shut down the system and release its
  resources. Returns an updated instance of the system."
  [system]
  (d/delete-database (get system "ccr.datomic.uri"))
  (dissoc system :repository))
