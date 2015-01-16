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
   (node? item) (->> (get item :jcr.node/_children) first) 
   (item? item) (->> (get item :jcr.node/_properties) first )))

(defn node-name [node]
  (let [n (get node :jcr.node/name)]
    (if (= n "jcr:root")
      ""
      n)))

(defn item-name [item]
  (cond
   (node? item) (node-name item)
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
              [?p :jcr.property/name ?pn]
              [?p _ ?pv]
              [?e :jcr.node/properties ?p]
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
  (let [position (->> (re-find #".*\[(\d+)\]" relPath) second)
        index    (if (nil? position) 0 (dec (Integer/parseInt position)))
        basePathSegment (if (nil? position)
                          relPath
                          (.substring relPath 0 (.indexOf relPath "[")))
        ]
    (as-> (get node :jcr.node/children) x
          (filter #(= basePathSegment (get % :jcr.node/name)) x)
          (sort-by :jcr.node/position x)
          (nth x index))))


(defn item-by-path [session path]
  (let [path_of_names (as-> (clojure.string/split path #"/") x
                            (filter #(not (empty? %)) x))
        rn (->> session :workspace :jcr.workspace/rootNode)]
    (reduce (fn [current_node nodename]
              (if-let [c (child-node current_node nodename)]
                c
                (throw (java.lang.IllegalArgumentException.
                        (format "No node for path '%s'. Unkown path segment '%s'"
                                path nodename)))))
            rn path_of_names)))


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

(defn node-summary [node]
  (as-> node x
        (vector (->> x
                     (child-nodes)
                     (map item-name)
                     (sort-by :jcr.node/position ))
                (->> x
                     (properties)
                     (map (fn [p] (vector
                                  (get p :jcr.property/name)
                                  (->> (get p :jcr.property/values)
                                       (sort-by :jcr.value/position)
                                       (map #(get % (:jcr.property/value-attr p)))))))))))
