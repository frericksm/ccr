(ns ccr.repository
  (:require [clojure.java.io :as io]
            [ccr.api]
            [ccr.nodetypes]
            [ccr.session]
            [ccr.workspace]
            [datomic.api :as d  :only [q db]]
            )
  (:import [ccr.session.Session]))

(def schema-tx (read-string (slurp (io/resource "jcr.dtm"))))

(defn create-schema [conn]
  @(d/transact conn schema-tx))

(defrecord ImmutableRepository [uri connection]
  ccr.api/Repository
  
  ;;"Erzeugt eine Session."
  (login [this credentials workspace-name]
    (let [db   (d/db connection)
          workspace (ccr.workspace/workspace db workspace-name)]
       (ccr.session.Session. this workspace db)))
  
  (login [this]
     (ccr.api/login this nil "default")))

(defn repository [parameters]
  (if-let [uri (get parameters "ccr.datomic.uri")]
    (let [created (d/create-database uri)
          conn    (d/connect uri)]
      (if created (do  (create-schema conn)
                       ;;(ccr.nodetypes/load-builtin-node-types conn)
                       (ccr.workspace/create-workspace conn "default")))
      (->ImmutableRepository uri conn))))

