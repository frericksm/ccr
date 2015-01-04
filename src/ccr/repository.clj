(ns ccr.repository
  (:require [datomic.api :as d  :only [q db]]
            [clojure.pprint]
            [clojure.java.io :as io]
            [ccr.nodetypes :as nt]
            ))


(def schema-tx (read-string (slurp (io/resource "jcr.dtm"))))

(defn create-schema [conn]
  @(d/transact conn schema-tx))

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

(defn repository [uri]
  (let [created (d/create-database uri)
        conn (d/connect uri)]
    (if created (do  (create-schema conn)
                     (nt/load-builtin-node-types conn)
                     (create-workspace conn "default")))
    {:uri uri
     :connection conn}))

(defn start [system]
  (let [uri (get-in system [:uri])
        repo (repository uri)]
    (assoc-in system [:repository] repo)))

(defn stop [system]
  (let [uri (get-in system [:db :uri])
        ;deleted (d/delete-database uri)
        ]
    (assoc-in system [:db :conn] nil)))
