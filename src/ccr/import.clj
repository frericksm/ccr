(ns ccr.import
  (:require [clojure.data.xml :as xml]
            [datomic.api :as d  :only [q db]]
            [clojure.java.io :as io]
            [clojure.instant :as instant]
            [net.cgrand.enlive-html :as html]
            [ccr.transaction-utils :as tu]))

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

(defn jcr-value-attr [type]
  (->> type
       (clojure.string/lower-case)
       (format "jcr.value/%s" )
       keyword))

(defn props [node]
  (as-> node x
        (html/select x [:> :node :> :property])
        (map (fn [p] (let [a         (:attrs p)
                          type      (:sv/type a)
                          name      (:sv/name a)
                          val-attr  (jcr-value-attr type)]
                      {:jcr.property/name name
                       :jcr.property/value-attr val-attr
                       :jcr.property/values
                       (->> p x
                            (html/select x [:sv/value html/text-node])
                            (map-indexed
                             (fn [i v] {:jcr.value/position i
                                       val-attr (trans-single-value v)}))
                            vec)})) x)))

(defn node-as-entity
  "Creates the transaction from an element with tag :node"
  [position node]
  (let [node-tx {:jcr.node/name (->> node :attrs :sv/name)
                 :jcr.node/properties (vec (props node))
                 :jcr.node/children
                 (->> (html/select node [:> :node :> :node])
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

(defn nested-entities
  "Transforms the content of 'file' containing the system view exported from a jcr into a nested edn structure."
  [file]
  (as-> file x
        (parse-import-file x)
        (to-nested-entitites x)))




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
  [file]
  (let [tx-data (tu/translate-value (nested-entities file))
        import-root-db-id (first tx-data)
        tx-data-with-refs-adjusted (as-> (second tx-data) x
                                         (adjust-refs x nil))]
    (list import-root-db-id tx-data-with-refs-adjusted)))




(defn dump-import-tx
  "Converts the systemview  xml data from file 'systemview-xml-file' to a datomic transaction and
   dumps this transaction data to file 'tx-file'"
  [systemview-xml-file tx-file]
  (as-> (import-tx systemview-xml-file) x
        (let [import-root-node-db-id (first x)
              tx (second x)]
          (with-open [w (clojure.java.io/writer tx-file)]
            (.write w "[" )
            (.write w (pr-str import-root-node-db-id))
            (.newLine w)
            (.write w "[" )
            (doseq [line tx]
              (.write w (pr-str line))
              (.newLine w))
            (.write w "]" )
            (.write w "]" )))
        ))

(defn load-tx
  [conn parent-node root-node-db-id tx]
  (let [parent-new-node-link-tx (tu/add-tx (:db/id parent-node) root-node-db-id)
        full-tx (concat parent-new-node-link-tx tx)
        {:keys [db-after]} (deref (d/transact conn full-tx))]
    db-after))

(defn load-tx-file
  "Loads the transaction data contained in 'file' as child node of parent-node.
The file contains an edn data structure like this: (#db/id [...] [...])
The first element of that list is the tempid of the root node to be imported.
The second element of the list contains the transaction data."
  [session parent-node file]
  (as-> file x
        (clojure.java.io/reader x)
        (java.io.PushbackReader. x)
        (clojure.edn/read {:readers *data-readers*} x)
        (load-tx (get-in session [:repository :connection]) parent-node
                 (first x)
                 (second x))
        (assoc session :db x)))


(defn import-xml
  "Importiert den Inhalt der XML-Datei (System-View) als Child der parent-node.
  Liefert eine session, die den veränderten Zustand berücksichtigt"
  [session parent-node file]
  (let [conn (get-in session [:repository :connection])
        [root-node-db-id tx] (import-tx parent-node file)
        db-after (load-tx conn  parent-node root-node-db-id tx)]
    (assoc session :db db-after)))
