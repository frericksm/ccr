(ns ccr.import
  (:require [clojure.data.xml :as xml]
            [datomic.api :as d  :only [q db]]
            [clojure.java.io :as io]
            [clojure.instant :as instant]
            [net.cgrand.enlive-html :as html]
            [clojure.data.codec.base64 :as b64]))

(defn translate-value [v]
  ;; Returns a vector of two elements:
  ;; 1. The replacement for V (new :db/id value if V is a map,
  ;;    a vector with maps replaced by :db/id's if V is a vector, etc.)
  ;; 2. The sequence of maps which were replaced by their new :db/id's,
  ;;    each map already contains the :db/id.
  (letfn [(translate-values [values]
            (let [mapped (map translate-value values)]
              [(reduce conj [] (map first mapped))
               (reduce concat '() (map second mapped))]))]
    (cond (map? v) (let [id (d/tempid :db.part/user)
                         translated-vals (translate-values (vals v))
                         translated-map (zipmap (keys v)
                                                (first translated-vals))]
                     [id (cons (assoc translated-map :db/id id)
                               (second translated-vals))])
          (vector? v) (translate-values v)
          :else [v nil])))
 
(defn to-transaction [data-map]
  (vec (second (translate-value data-map))))


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
  "Print a byte array."
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

(defn trans-single-value [type val]
  (case type
       "Date" (instant/read-instant-timestamp val) 
       "Long" (Long/valueOf val)
       "String" val
       "Name" val
       "Path" val
       "Reference" val
       "Boolean" (Boolean/valueOf val)
       "Binary" (decode64 val)))

(defn trans-prop-val [type val cardinality-many]
  (if cardinality-many
    (map (partial trans-single-value type) val)
    (trans-single-value type val)))

(defn prop-value-in-cardinality
  "Returns a single value or a list of values"
  [property]
  (as-> property x
        (html/select x [:sv/value html/text-node])
        (if (= (count x) 1) (first x) x)))

(defn jcr-value-attr [type many]
  (let [card (if many "s" "")
        t (str (clojure.string/lower-case type) card)]
    (->> (format "jcr.value/%s"  t )
         keyword)))

(defn props [node]
  (as-> node x
        (html/select x [:> :node :> :property])
        (map (fn [p] (let [a (:attrs p)
                          type (:sv/type a)
                          name (:sv/name a)
                          value (prop-value-in-cardinality p)
                          card-many (coll? value) 
                          val-attr (jcr-value-attr type card-many)
                          val (trans-prop-val type value card-many)]
                      {:jcr.property/name (:sv/name a)
                       :jcr.property/value-attr val-attr
                       val-attr val})) x)))

(defn prop-val [node prop-name]
  (as-> node x
        (html/select x [:> :node :> [:property (html/attr= :sv/name prop-name)] html/text-node])
        (first x)))

(defn node-as-entity
  "Creates the transaction from an element with tag :node"
  [position node]
  (let [node-tx {:jcr.node/name (->> node :attrs :sv/name)
                 :jcr.node/properties (vec (props node))
                 :jcr.node/children (->> (html/select node [:> :node :> :node])
                                         (map-indexed (fn [i n] (node-as-entity
                                                                i n)))
                                         vec)}] 
    (if (nil? position)
      node-tx
      (assoc node-tx :jcr.node/position position))))

(defn to-nested-entitites
  " "
  [node]
  (as-> node x
        (html/select x [:> :node])
        (map (partial node-as-entity nil) x)
        (reduce (fn [a c] (concat a c) ) x)
        ;(first x)
        ))

(defn parse-import-file
  "Reads and parses a xml file containing the system view exported from a jcr repository"
  [import-file]
  (as-> import-file x
        (io/file x)
        (io/input-stream x)
        (xml/parse x)))

(defn nested-entities [import-file]
  (as-> import-file x
        (parse-import-file x)
        (to-nested-entitites x)))

(defn add-tx [node-id child-id]
  [{:db/id             node-id
    :jcr.node/children child-id}
   [:append-position-in-scope node-id :jcr.node/children child-id :jcr.node/position]
   ])


(defn adjust-refs [tx out-of-reach-node-dbid]
  (let [refed_uuids   (as-> tx x
                            (filter (fn [e] (not
                                            (nil?
                                             (get e :jcr.value/reference)))) x)
                            (map (fn [e] (get e :jcr.value/reference)) x)
                            (set x))

        uuid2propdbid (as-> tx x
                            (filter (fn [e]
                                      (and (= (get e :jcr.property/name)
                                              "jcr:uuid")
                                           (contains? refed_uuids
                                                      (get e :jcr.value/string))))
                                    x)
                            (map (fn [e] [(get e :jcr.value/string)
                                         (get e :db/id)]) x)
                            (into {} x))
        propdbids     (set (vals uuid2propdbid))

        propdbid2nodedbid
        
        (as-> tx x
              (filter (fn [e] (not (nil? (get e :jcr.node/properties)))) x)
              (map (fn [e] [(:db/id e) (clojure.set/intersection
                                       propdbids
                                       (set (get e :jcr.node/properties)))]) x)
              (filter (fn [[dbid isect]] (not (empty? isect))) x)
              (map (fn [[dbid isect]] [(first isect) dbid]) x)
              (into {} x))]
    (as-> tx x
          (map (fn [e]
                 (cond (and (map? e)
                            (= :jcr.value/reference
                               (:jcr.property/value-attr e)))
                       (update-in e [:jcr.value/reference]
                                  (fn [old_value]
                                    (as-> old_value x
                                         (get uuid2propdbid x)
                                         (get propdbid2nodedbid x
                                              out-of-reach-node-dbid)))) 
                       :else e)) x))))

(defn import-tx
  "Liefert die Transaction mit der das file importiert wird"
  [parent-node file]
  (let [tx-data (translate-value (nested-entities file))
        import-root-db-id (first tx-data)
        tx-data-with-refs-adjusted (as-> (second tx-data) x
                                         (adjust-refs x nil))
        parent-new-node-link-tx (add-tx (:db/id parent-node) import-root-db-id)]
    (concat parent-new-node-link-tx tx-data-with-refs-adjusted)))

(defn import-xml
  "Importiert den Inhalt der XML-Datei (System-View) als Child der parent-node.
  Liefert eine session, die den veränderten Zustand berücksichtigt"
  [session parent-node file]
  (let [conn (get-in session [:repository :connection])
        {:keys [db-after]} (deref  (d/transact conn (import-tx parent-node file)))]
    (assoc session :db db-after)))


(defn dump-import-tx
  "Converts the systemview  xml data from file 'systemview-xml-file' to a datomic transaction and
   dumps this transaction data to file 'tx-file'"
  [rn systemview-xml-file tx-file]
  (as-> (import-tx rn systemview-xml-file) x
        (map pr-str x)
        (with-open [w (clojure.java.io/writer tx-file)]
          (.write w "[" )
          (doseq [line x]
            (.write w line)
            (.newLine w))
          (.write w "]" ))
        ))

(defn load-tx-file
  "Transacts the transaction data cotained in the tx-file"
  [session tx-file]
  (as-> tx-file x
        (clojure.java.io/reader x)
        (java.io.PushbackReader. x)
        (clojure.edn/read {:readers *data-readers*} x)
        (let [conn (get-in session [:repository :connection])
              {:keys [db-after]}
              (deref (d/transact conn x))]
          (assoc session :db db-after))
        ))
