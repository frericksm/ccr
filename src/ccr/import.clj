(ns ccr.import
  (:require [clojure.data.xml :as xml]
            [datomic.api :as d  :only [q db]]
            [clojure.java.io :as io]
            [clojure.instant :as instant]
            [net.cgrand.enlive-html :as html]
            [clojure.data.codec.base64 :as b64]))


(defn encode64 [ba]
  (let [is (java.io.ByteArrayInputStream. ba)
        os (java.io.ByteArrayOutputStream.)]
    (with-open [in is
                out os]
      (b64/encoding-transfer in out))
    (->> os
         (.toByteArray)
         (String. ))))

(defn decode64 [b64-string]
  (let [is (java.io.ByteArrayInputStream. (.getBytes b64-string))
        os (java.io.ByteArrayOutputStream.)]
    (with-open [in is
                out os]
      (b64/decoding-transfer in out))
    (.toByteArray os)))


(defn- print-byte-array
  "Print a java.util.Date as RFC3339 timestamp, always in UTC."
  [^bytes ba, ^java.io.Writer w]
  (.write w "#base64 \"")
  (.write w (encode64 ba))
  (.write w "\""))

(defmethod print-method (class (byte-array 1)) 
  [ba, ^java.io.Writer w]
  (print-byte-array ba w))

(defmethod print-dup (class (byte-array 1))
  [ba, ^java.io.Writer w]
  (print-byte-array ba w))

(defn trans-prop-name [name]
  (-> name
      (clojure.string/replace ":" "/")
      keyword))

(defn prop-value-in-cardinality
  "Returns a single value or a list of values"
  [property]
  (as-> property x
        (html/select x [:sv/value html/text-node])
        (if (= (count x) 1) (first x) x)))

(defn props [node]
  (as-> node x
        (html/select x [:> :node :> :property])
        (map-indexed (fn [i p] (let [a (:attrs p)]
                      {:name (:sv/name a)
                       :type (:sv/type a)
                       :position i
                       :value (prop-value-in-cardinality p)})) x)))

(defn prop-val [node prop-name]
  (as-> node x
        (html/select x [:> :node :> [:property (html/attr= :sv/name prop-name)] html/text-node])
        (first x)))

(defn jcr-value-attr [type many]
  (let [card (if many "s" "")
        t (str (clojure.string/lower-case type) card)]
    (->> (format "jcr.value/%s"  t )
         keyword)))

(defn uuid-tempid-map
  "Liefert eine Map die UUIDs auf Datomic Tempids abbildet.
   Dazu werden alle Properties mit dem Namen \"jcr:uuid\" ermittlet. "
  [node]
  (as-> node x
        (html/select x [[:property (html/attr= :sv/name "jcr:uuid")] html/text-node])
        (map (fn [uuid] (vector uuid (d/tempid ":db.part/user"))) x)
        (into {} x)))

(defn uuid-to-position
  "Liefert eine Map, die die UUID einer Node auf die Position dieser Node als Child des Parent hat"
  [node]
  (as-> node x
        (html/select x [:node])
        (map (fn [n]
               (map-indexed (fn [ix c] (vector c ix))
                            (->> (html/select n [:> :node :> :node])
                                 (map #(prop-val % "jcr:uuid")))))
             x)
        (apply concat x)
        (into {} x)))

(defn parent-child-relations
  "Liefert eine Map, die die UUID einer Node auf die UUIDs der Child-Nodes abbildet"
  [node]
  (as-> node x
        (html/select x [:node])
        (map (fn [n]
               (vector (prop-val n "jcr:uuid")
                       (->> (html/select n [:> :node :> :node])
                            (map #(prop-val % "jcr:uuid")))))
             x)
        (into {} x)))



(defn trans-single-value [uuid2tempid type val]
  (case type
       "Date" (instant/read-instant-timestamp val) 
       "Long" (Long/valueOf val)
       "String" val
       "Name" val
       "Path" val
       "Reference" (get uuid2tempid val)
       "Boolean" (Boolean/valueOf val)
       "Binary" (decode64 val)))

(defn trans-prop-val [uuid2tempid type val cardinality-many]
  (if cardinality-many
    (map (partial trans-single-value uuid2tempid type) val)
    (trans-single-value uuid2tempid type val)))


(defn node-tx
  "Creates the transaction from an element with tag :node"
  [uuid2tempid parent2children uuid2position node]
  (let [ps           (props node)
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
                  :jcr.node/name (->> node :attrs :sv/name)
                  :jcr.node/properties properties-ids
                  :jcr.node/children children-ids
                  }]]
    (as-> node-tx x
          (if position (cons {:db/id nid
                              :jcr.node/position position}
                             x) x)
          (concat x ps-tx))))

(defn to-tx [uuid2tempid parent2children uuid2position node]
  (as-> node x
        (html/select x [:node])
        (map (partial node-tx uuid2tempid parent2children uuid2position) x)
        (reduce (fn [a c] (concat a c) ) x)
        ))

(defn parse-import-file
  "Parst eine XML-Datei, die eine Systemview einer Node aus einem JCR-Repository enthält"
  [import-file]
  (as-> import-file x
        (io/file x)
        (io/input-stream x)
        (xml/parse x)))

(defn tx-data [import-file]
  (let [node (parse-import-file import-file)
        u2t  (uuid-tempid-map node)
        u2p  (uuid-to-position node)
        import-root-uuid (prop-val node "jcr:uuid") ;; die uuid der root-node aus dem import file
        p2c (parent-child-relations node)
        import-tx-data (to-tx u2t p2c u2p node)]
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


(defn dump-import-tx [rn in-file out-file]
  (as-> (import-tx rn in-file) x
        (map pr-str x)
        (with-open [w (clojure.java.io/writer "c:/1111/FIMS-tx.edn")]
          (doseq [line x]
            (.write w line)
            (.newLine w)))
        ))

(defn load-tx-file [session filename]
  (as-> filename x
        (clojure.java.io/reader x)
        (java.io.PushbackReader. x)
        (clojure.edn/read {:readers *data-readers*} x)
        (let [conn (get-in session [:repository :connection])
              {:keys [db-after]}
              (deref (d/transact conn x))]
          (assoc session :db db-after))
        ))
