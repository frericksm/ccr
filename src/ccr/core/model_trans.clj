(ns ccr.core.model-trans
  (:require [datomic.api :as d  :only [q db pull pull-many transact]]))

(defn debug [m x] (println m x) x)

(defn update-values
  "Calculates the neccessary addition and retraction"
  [old-value-entities new-values value-attr]
  (let [old-values (map (fn [e] (get e value-attr)) old-value-entities)
        add        (clojure.set/difference new-values old-values)
        add-tx     (map (fn [v] {value-attr v}) add)
        retract    (clojure.set/difference old-values new-values)
        retract-tx-val-attr (as-> old-value-entities x
                              (filter (fn [e] (contains? retract (get e value-attr))) x)
                              (map (fn [e] [:db/retract (:db/id e)
                                            value-attr (get e value-attr)]) x))
        retract-tx-pos-attr (as-> old-value-entities x
                              (filter (fn [e] (contains? retract (get e value-attr))) x)
                              (map (fn [e] [:db/retract (:db/id e)
                                            value-attr (get e value-attr)]) x))]
    (as-> (concat retract-tx-val-attr retract-tx-pos-attr add-tx) x
      (vec x)
      #_(debug "update-values" x)
      )))

(defn property-transaction [name value-attr values]
  {:jcr.property/name name
   :jcr.property/value-attr value-attr
   :jcr.property/values (as-> values x
                          (map (fn [v] {value-attr v}) x)
                          (vec x))})

(defn node-transaction [name children properties]
  {:jcr.node/name name
   :jcr.node/children children
   :jcr.node/properties properties})
