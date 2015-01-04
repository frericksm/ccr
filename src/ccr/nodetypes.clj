(ns ccr.nodetypes
  (:require [ccr.cnd :as cnd]
            [clojure.data.xml :as xml]
            [clojure.data.zip.xml :as zx]
            [clojure.data.zip :as z]
            [clojure.zip :as zip]
            [datomic.api :as d  :only [q db]]
            [clojure.java.io :as io]
            [clojure.instant :as instant]
            [clojure.pprint :as pp]))

(defn load-node-types
  ""
  [connection]
  (->> (cnd/builtin-nodetypes)
       (cnd/nodetypes)
       (d/transact connection)))
