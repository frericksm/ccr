(ns ccr.core.repository
  (:require [ccr.api.repository]
            [ccr.core.nodetype]
            [ccr.core.session]
            [ccr.core.workspace]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [datomic.api :as d  :only [q db]])
  (:import [ccr.session.Session]))

(def schema-tx (read-string (slurp (io/resource "jcr.dtm"))))

(defn create-schema [conn]
  @(d/transact conn schema-tx))

(defrecord ImmutableRepository [uri connection]
  ccr.api.repository/Repository
  
  ;;"Erzeugt eine Session."
  (login [this credentials workspace-name]
    (let [db   (d/db connection)
          workspace (ccr.core.workspace/workspace db workspace-name)]
       (ccr.core.session.Session. this workspace db)))
  
  (login [this]
     (ccr.api.repository/login this nil "default")))

(defn repository [parameters]
  (try
    (if-let [uri (get parameters "ccr.datomic.uri")]
      (let [created (d/create-database uri)
            conn    (d/connect uri)]
        (if created (do  (create-schema conn)
                         ;;(ccr.nodetype/load-builtin-node-types conn)
                         (ccr.core.workspace/create-workspace conn "default")))
        (->ImmutableRepository uri conn)))
    (catch datomic.impl.Exceptions$IllegalArgumentExceptionInfo e
      (log/error e)
      (throw (java.lang.IllegalArgumentException. e ))
      )))

