(ns ccr.import
  (:require [clojure.data.xml :as xml]
            [clojure.data.zip.xml :as zx]
            [clojure.data.zip :as z]
            [clojure.zip :as zip]
            [datomic.api :as d  :only [q db]]
            [clojure.java.io :as io]
            [clojure.instant :as instant]
            [clojure.pprint :as pp]))

(defn trans-prop-val [uuid2tempid type val]
  (case type
    "Date" (instant/read-instant-timestamp val) 
    "Long" (Long/valueOf val)
    "String" val
    "Name" val
    "Reference" (get uuid2tempid val)
    "Boolean" (Boolean/valueOf val)
    )
  )

(defn trans-prop-name [name]
  (-> name
      (clojure.string/replace ":" "/")
      keyword))

(defn props-2 [loc]
  (as-> loc x
        (zx/xml-> x z/children :property)
        (map (fn [p] {:name (zx/attr p :sv/name)
                     :type (zx/attr p :sv/type)
                     :value (zx/text p)}) x)))

(defn props [uuid2tempids loc]
  (as-> loc x
        (zx/xml-> x z/children :property)
        (reduce (fn [a p] (assoc a (trans-prop-name (zx/attr p :sv/name))
                           (trans-prop-val uuid2tempids (zx/attr p :sv/type) (zx/text p)))) {} x)))

(defn prop-val [loc prop-name]
  (as-> loc x
        (zx/xml-> x 
                  z/children
                  :property
                  (zx/attr= :sv/name prop-name))
        (map zx/text x)
        (first x)))

(defn node-tx [uuid2tempid parent2children loc]
  (let [ps (props loc)
        uuid (get ps :jcr/uuid)
        nid (get uuid2tempid uuid)
        children-ids (->> (get parent2children uuid) (map uuid2tempid))
        ]
    (merge ps 
           {:db/id nid
            :jcr/uuid uuid
            :jcr.node/name (zx/attr loc :sv/name)
            :jcr.node/children children-ids})))

(defn jcr-value-attr [type many]
  (let [card (if many "values" "value")
        t (clojure.string/lower-case type)]
    (->> (format "jcr.property.%s/%s"  t card )
         keyword)))

(defn node-tx-2 [uuid2tempid parent2children loc]
  (let [ps           (props-2 loc)
        ps2tempids   (reduce (fn [a p] (assoc a (:name  p)
                                             (d/tempid ":db.part/user")))
                             {} ps)        
        ps-tx        (->> ps 
                          (map (fn [p]
                                 (let [type (:type p)
                                       name (:name p)
                                       value (:value p)]
                                   {:db/id (get ps2tempids name)
                                    :jcr.property/name name
                                    :jcr.property/type type
                                    (jcr-value-attr type false)
                                    (trans-prop-val uuid2tempid type value)
                                    }))))
        uuid         (->> ps
                          (filter #(= "jcr:uuid" (:name %)))
                          (map :value)
                          first)
        nid          (get uuid2tempid uuid)
        children-ids (->> (get parent2children uuid) (map uuid2tempid))
        properties-ids (vals ps2tempids)
        ]
    (cons {:db/id nid
           :jcr.node/name (zx/attr loc :sv/name)
           :jcr.node/properties properties-ids
           :jcr.node/children children-ids}
          ps-tx)))

(defn parent-child-relations
  "Liefert eine Map, die die UUID einer Node auf die UUIDs der Child-Nodes abbildet"
  [loc]
  (as-> loc x
        (zx/xml-> x z/descendants :node)
        (map (fn [n] (vector (prop-val n "jcr:uuid")
                            (->> (zx/xml-> n z/children :node)
                                 (map #(prop-val % "jcr:uuid")))))
             x)
        (into {} x)))

(defn uuid-tempid-map
  "Liefert eine Map die UUIDs auf Datomic Tempids abbildet.
   Dazu werden alle Properties mit dem Namen \"jcr:uuid\" ermittlet. "
  [loc]
  (as-> loc x
        (zx/xml-> x 
                  z/descendants
                  :property
                  (zx/attr= :sv/name "jcr:uuid"))
        (map (fn [p] (vector (zx/text p) (d/tempid ":db.part/user"))) x)
        (into {} x)))

(defn to-tx-2 [uuid2tempid parent2children loc]
  (as-> loc x
        (zx/xml-> x z/descendants :node)
        (map (partial node-tx-2 uuid2tempid parent2children) x)
        (apply concat x)
        ))

(defn import-file-zipper [import-file]
  (as-> import-file x
        (io/file x)
        (io/input-stream x)
        (xml/parse x)
        (zip/xml-zip x)))


(def builins-file "C:/Projekte/isp-head/ContentEditor/Prod/META-INF/repository/builtin.xml_")
(def custom-file "C:/jcr111/repository/nodetypes/custom_nodetypes.xml")

(defn load-node-types []
  (let [builtins (as->  builins-file x
                        (io/input-stream x)
                        (xml/parse x))
        customs (as->  custom-file x
                        (io/input-stream x)
                        (xml/parse x))
        ]))

(defn tx-data [import-file]
  (let [s (import-file-zipper import-file)
        u2t (uuid-tempid-map s)
        p2c (parent-child-relations s)]
    (to-tx-2 u2t p2c s)))

(defn start [system]
  (as-> system x
        (:import-file x)
        (tx-data x)
        (with-open [o (io/writer  (:tx-out-file system))]
          (binding [*out* o]
            (pr x)))
        ))




