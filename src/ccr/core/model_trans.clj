(ns ccr.core.model-trans
  (:require [datomic.api :as d  :only [q db pull pull-many transact]]))

(defn debug [m x] (println m x) x)

(defn update-values
  "Calculates the neccessary addition and retraction"
  [prop-id old-value-entities new-values value-attr]
  (let [old-values-set (into #{} (map (fn [e] (get e value-attr)) old-value-entities))
        new-values-set (into #{} new-values)
        add-tx     (map-indexed (fn [idx v] {value-attr v
                                             :jcr.value/position idx}) new-values)
        retract    (clojure.set/difference old-values-set new-values-set)
        retract-tx-val-ent (as-> old-value-entities x
                             (filter (fn [e] (contains? retract (get e value-attr))) x)
                             (map (fn [e] [:db/retract prop-id :jcr.property/values (:db/id e)]) x))
        retract-tx-val-attr (as-> old-value-entities x
                              (filter (fn [e] (contains? retract (get e value-attr))) x)
                              (map (fn [e] [:db/retract (:db/id e)
                                            value-attr (get e value-attr)]) x))
        retract-tx-pos-attr (as-> old-value-entities x
                              (filter (fn [e] (contains? retract (get e value-attr))) x)
                              (filter (fn [e] (contains? e :jcr.value/position)) x)
                              (map (fn [e] [:db/retract (:db/id e)
                                            :jcr.value/position (get e :jcr.value/position)]) x))]
    (as-> {:retractions (concat retract-tx-val-ent retract-tx-val-attr retract-tx-pos-attr)
           :additions (vec add-tx)} x
      )))

(defn property-transaction [name value-attr values]
  {:jcr.property/name name
   :jcr.property/value-attr value-attr
   :jcr.property/values (as-> values x
                          (map-indexed (fn [idx v] {value-attr v
                                                    :jcr.value/position idx}) x)
                          (vec x))})

(defn node-transaction [name children properties]
  {:jcr.node/name name
   :jcr.node/children children
   :jcr.node/properties properties})
