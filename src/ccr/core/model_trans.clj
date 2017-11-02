(ns ccr.core.model-trans
  (:require [datomic.api :as d  :only [q db pull pull-many transact]]))

(defn update-values
  "Calculates the necca"
  [old-value-entities new-values value-attr]
  (let [old-values (map (fn [e] (get e value-attr)) old-value-entities)
        add (clojure.set/difference new-values old-values)
        add-tx (map (fn [v] {type v}) add)
        retract (clojure.set/difference old-values new-values)
        retract-tx (map (fn [e] [:db/retract (:db/id e)
                                 value-attr (get e value-attr)]) retract)]
    (as-> (concat retract-tx add-tx) x
      (vec x))))

(defn property-transaction [name type values]
  {:jcr.property/name name
   :jcr.property/value-attr type
   :jcr.property/values (as-> values x
                          (map (fn [v] {type v}) x)
                          (vec x))})

(defn node-transaction [name children properties]
  {:jcr.node/name name
   :jcr.node/children children
   :jcr.node/properties properties})
