(ns ccr.import
  (:require [clojure.data.xml :as xml]
            [clojure.data.zip.xml :as zx]
            [clojure.data.zip :as z]
            [clojure.zip :as zip]
            [datomic.api :as d  :only [q db]]
            [clojure.java.io :as io]
            [clojure.instant :as instant]
            [clojure.pprint :as pp]
            [net.cgrand.enlive-html :as html]))

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

(defn import-file-zipper [import-file]
  (as-> import-file x
        (io/file x)
        (io/input-stream x)
        (xml/parse x)
        (zip/xml-zip x)))

(defn trans-single-value [uuid2tempid type val]
  (case type
       "Date" (instant/read-instant-timestamp val) 
       "Long" (Long/valueOf val)
       "String" val
       "Name" val
       "Reference" (get uuid2tempid val)
       "Boolean" (Boolean/valueOf val)
       ))

(defn trans-prop-val [uuid2tempid type val cardinality-many]
  (if cardinality-many
    (map (partial trans-single-value uuid2tempid type) val)
    (trans-single-value uuid2tempid type val)))

(defn trans-prop-name [name]
  (-> name
      (clojure.string/replace ":" "/")
      keyword))

(defn props [loc]
  (as-> loc x
        (zx/xml-> x z/children :property)
        (map (fn [p] {:name (zx/attr p :sv/name)
                     :type (zx/attr p :sv/type)
                     :value (zx/text p)}) x)))

(defn prop-val [loc prop-name]
  (as-> loc x
        (zx/xml-> x 
                  z/children
                  :property
                  (zx/attr= :sv/name prop-name))
        (map zx/text x)
        (first x)))

(defn jcr-value-attr [type many]
  (let [card (if many "s" "")
        t (str (clojure.string/lower-case type) card)]
    (->> (format "jcr.value/%s"  t )
         keyword)))

(defn node-tx
  "Erzeugt eine Transaction für die Node an der Location 'loc'."
  [uuid2tempid parent2children uuid2position loc]
  (let [ps           (props loc)
        ps2tempids   (reduce (fn [a p] (assoc a (:name  p)
                                             (d/tempid ":db.part/user")))
                             {} ps)        
        ps-tx        (->> ps 
                          (map (fn [p]
                                 (let [type (:type p)
                                       name (:name p)
                                       value (:value p)
                                       cardinality-many (coll? value) 
                                       val-attr (jcr-value-attr type cardinality-many)]
                                   {:db/id (get ps2tempids name)
                                    :jcr.property/name name
                                    :jcr.property/value-attr val-attr
                                    val-attr
                                    (trans-prop-val uuid2tempid type value cardinality-many)
                                    }))))
        uuid         (->> ps
                          (filter #(= "jcr:uuid" (:name %)))
                          (map :value)
                          first)
        nid          (get uuid2tempid uuid)
        children-ids (->> (get parent2children uuid) (map uuid2tempid))
        properties-ids (vals ps2tempids)
        position       (get uuid2position uuid)
        node-tx [{:db/id nid
                  :jcr.node/name (zx/attr loc :sv/name)
                  :jcr.node/properties properties-ids
                  :jcr.node/children children-ids
                  }]]
    (as-> node-tx x
          (if position (cons {:db/id nid
                              :jcr.node/position position}
                             x) x)
          (concat x ps-tx))))

(defn to-tx [uuid2tempid parent2children uuid2position loc]
  (as-> loc x
        (zx/xml-> x z/descendants :node)
        (map (partial node-tx uuid2tempid parent2children uuid2position) x)
        (reduce (fn [a c] (concat a c) ) x)
        ))

(defn uuid-to-position
  "Liefert eine Map, die die UUID einer Node auf die Position dieser Node als Child des Parent hat"
  [loc]
  (as-> loc x
        (zx/xml-> x z/descendants :node)
        (map (fn [n]
               (map-indexed (fn [ix c] (vector c ix))
                            (->> (zx/xml-> n z/children :node)
                                 (map #(prop-val % "jcr:uuid")))))
             x)
        (apply concat x)
        (into {} x)))

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

(defn tx-data [import-file]
  (let [s   (import-file-zipper import-file)
        u2t (uuid-tempid-map s)
        u2p (uuid-to-position s)
        import-root-uuid (prop-val s "jcr:uuid") ;; die uuid der root-node aus dem import file
        p2c (parent-child-relations s)
        import-tx-data (to-tx u2t p2c u2p s)]
    {:import-tx-data import-tx-data
     :import-root-db-id (get u2t import-root-uuid) 
     }))

(defn add-tx [node-id child-id]
  [{:db/id             node-id
    :jcr.node/children child-id}
   [:append-position-in-scope node-id :jcr.node/children child-id :jcr.node/position]
   ])

(defn import-tx
  "Liefert die Transaction mit der das file importiert wird"
  [parent-node file]
  (let [{:keys [import-tx-data import-root-db-id]} (tx-data file)
        parent-new-node-link-tx (add-tx (:db/id parent-node) import-root-db-id)]
    (concat parent-new-node-link-tx import-tx-data)))

(defn import-xml
  "Importiert den Inhalt der XML-Datei (System-View) als Child der parent-node.
  Liefert eine session, die den veränderten Zustand berücksichtigt"
  [session parent-node file]
  (let [conn (get-in session [:repository :connection])
        {:keys [db-after]} (deref  (d/transact conn (import-tx parent-node file)))]
    (assoc session :db db-after)))




