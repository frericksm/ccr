(ns ccr.session
  (:require [datomic.api :as d  :only [q db]])
  (:require clojure.pprint))


(defn workspace [db workspace-name]
  (->> (d/q '[:find ?e 
              :in $ ?name
              :where 
              [?e :jcr.workspace/name ?name]
              ]                   
            db
            workspace-name)
       (map #(d/entity db (first %)))    
       first))

(defn login
  "Erzeugt eine Session."
  ([repository credentials workspace-name]
     (let [conn (:connection repository)
           db   (d/db conn)
           ws   (workspace db workspace-name)]
       {:repository repository
        :workspace ws
        :db db}))
  ([repository]
     (login repository nil "default")))

(defn root
  ([session]
     (let [db (get-in session [:db])
           name (get-in session [:workspace :jcr.workspace/name ])]
       (->> (d/q '[:find ?r 
                   :in $ ?name
                   :where
                   [?e :jcr.workspace/name ?name]
                   [?e :jcr.workspace/rootNode ?r]
                   ]                   
                 db
                 name
                 )
            (map #(d/entity db (first %)))    
            first))))






