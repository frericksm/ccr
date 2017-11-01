(ns ccr.core.model-trans
  (:require [datomic.api :as d  :only [q db pull pull-many transact]]))

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
