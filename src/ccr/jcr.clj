(ns ccr.jcr
  (:require [datomic.api :as d  :only [q db]])
  (:require clojure.pprint))


(def schema-tx (read-string (slurp "resources/jcr.dtm")))

(defn create-schema [conn]
  @(d/transact conn schema-tx))

(defn node? [item]
  (contains? item :jcr.node/name))

(defn item? [item]
  (contains? item :jcr.property/name))

(defn item-parent [item]
  (cond
   (node? item) (->> (get item :jcr.node/_children) first)
   (item? item) (->> (get item :jcr.node/_properties) first)))

(defn item-name [item]
  (cond
   (node? item) (get item :jcr.node/name)
   (item? item) (get item :jcr.property/name)))

(defn item-path-internal [item]
  (loop [i item
         path nil]
    (let [p (item-parent i)
          new_path (cons (item-name i) path)]
      (if (nil? p)
        new_path
        (recur p new_path ))
      )))

(defn item-path [item]
  (as-> item x
        (item-path-internal x)
        (filter #(not (nil? %)) x)
        (interpose "/" x)
        (if (empty? x)
          nil
          (apply str (cons "/" x)))))

(defn node-by-uuid [db uuid]
  (->> (d/q '[:find ?e 
              :in $ ?pn ?pv
              :where 
              [?e :jcr.node/properties ?p]
              [?p :jcr.property/name ?pn]
              [?p _ ?pv]
              ]                   
            db                
            "jcr:uuid" 
            uuid)
       (map #(d/entity db (first %)))    
       first))

(defn child-nodes
  ([node]
     (get node :jcr.node/children))

  ([node namePattern]
     (->> (get node :jcr.node/children)
          (filter #(re-matches (re-pattern namePattern)
                               (get % :jcr.node/name))))))

(defn child-node [node relPath]
  (->> (get node :jcr.node/children)
       (filter #(= relPath (get % :jcr.node/name)))
       ))

(defn properties [node]
  (get node :jcr.node/properties))

(defn property [node property-name]
  (->> (get node :jcr.node/properties)
       (filter #(= property-name (get % :jcr.property/name)))
       first
       ))

(defn value [property]
  (->> (keys property)
       (filter #(.startsWith (name %) "value"))
       (map #(get property %))
       (first)))

(defn workspace [db workspace-name ]
  (->> (d/q '[:find ?e 
              :in $ ?name
              :where 
              [?e :jcr.workspace/name ?name]
              ]                   
            db
            workspace-name)
       (map #(d/entity db (first %)))    
       first))

(defn repository-login
  ([repository workspace-name]
     (let [conn (:connection repository)
           db (d/db (:connection repository))]
       {:repository repository
        :workspace (workspace db workspace-name)}))
  ([repository]
     (repository-login repository "default")
     ))

(defn repository [uri]
  (let [created (d/create-database uri)
        conn (d/connect uri)]
    (if created (create-schema conn))
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
