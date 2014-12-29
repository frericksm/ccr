(ns ccr.tree
  (:require [datomic.api :as d  :only [q db]])
  ;;(:require clojure.pprint)
  )


(defn node? [item]
  (contains? item :jcr.node/name))

(defn item? [item]
  (contains? item :jcr.property/name))

(defn item-parent [ item]
  (cond
   (node? item) (->> (get item :jcr.node/_children) )
   (item? item) (->> (get item :jcr.node/_properties) )))

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
          (apply str x))))

(defn query-entities-by-property [db property-name property-value]
  (->> (d/q '[:find ?e 
              :in $ ?pn ?pv
              :where 
              [?e :jcr.node/properties ?p]
              [?p :jcr.property/name ?pn]
              [?p _ ?pv]
              ]                   
            db                
            property-name
            property-value)
       (map first)
       (map #(d/entity db  %))))

(defn node-by-uuid [db uuid]
  (->> (query-entities-by-property db "jcr:uuid" uuid)  
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
       (first)))

(defn properties [node]
  (get node :jcr.node/properties))

(defn property [node property-name]
  (->> (get node :jcr.node/properties)
       (filter #(= property-name (get % :jcr.property/name)))
       first
       ))

(defn property-value [property]
  (->> (keys property)
       (filter #(.startsWith (name %) "value"))
       (map #(get property %))
       (first)))

(defn property-name [property]
  (:jcr.property/name property)
)

