(ns ccr.core.import
  (:require [clojure.data.xml :as xml]
            [clojure.instant :as instant]
            [clojure.java.io :as io]
            [ccr.core.cnd :as cnd]
            [ccr.core.transaction-utils :as tu]
            [datomic.api :as d  :only [q db]]
            [net.cgrand.enlive-html :as html]
            ))

(defn trans-single-value [type val]
  (case type
       "Date" (instant/read-instant-timestamp val) 
       "Long" (Long/valueOf val)
       "String" val
       "Name" val
       "Path" val
       "Reference" val
       "Boolean" (Boolean/valueOf val)
       "Binary" (tu/decode64 val)))



(defn props [node]
  (as-> node x
        (html/select x [:> :node :> :property])
        (map (fn [p]
               (let [a         (:attrs p)
                     type      (:sv/type a)
                     name      (:sv/name a)
                     val-attr  (cnd/jcr-value-attr type)]
                 {:jcr.property/name name
                  :jcr.property/value-attr val-attr
                  :jcr.property/values
                  (as-> p y
                        (html/select y [:sv/value html/text-node])
                        (map-indexed
                         (fn [i v] {:jcr.value/position i
                                   val-attr (trans-single-value type v)}) y)
                        (vec y))}))
             x)))

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
  "Transforms the 'node' (parsed xml) to a nested clojure map"
  [node]
  (as-> node x
        (html/select x [:> :node])
        (map (partial node-as-entity nil) x)
        (reduce (fn [a c] (concat a c) ) x)))

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

(defn referenced-uuids [tx]
  (as-> tx x
        (map (fn [e] (get e :jcr.value/reference)) x)
        (filter (comp not nil?) x)
        (set x)))

(defn uuid-2-value-dbid [tx refed_uuids]
  (as-> tx x
        (filter (fn [e]
                  (contains? refed_uuids
                             (get e :jcr.value/string))) x)
        (map (fn [e] [(get e :jcr.value/string)
                     (get e :db/id)]) x)
        (into {} x)))

(defn value-dbid-2-prop-dbid [tx value_dbids]
  (as-> tx x
        (filter (fn [e] (not (nil? (get e :jcr.property/values)))) x)
        (map (fn [e] [(:db/id e) (clojure.set/intersection
                                 value_dbids
                                 (set (get e :jcr.property/values)))]) x)
        (filter (fn [[dbid isect]] (not (empty? isect))) x)
        (map (fn [[dbid isect]] [(first isect) dbid]) x)
        (into {} x)))

(defn prop-dbid-2-node-dbid [tx prop_dbids]
  (as-> tx x
        (filter (fn [e] (not (nil? (get e :jcr.node/properties)))) x)
        (map (fn [e] [(:db/id e) (clojure.set/intersection
                                 prop_dbids
                                 (set (get e :jcr.node/properties)))]) x)
        (filter (fn [[dbid isect]] (not (empty? isect))) x)
        (map (fn [[dbid isect]] [(first isect) dbid]) x)
        (into {} x)))

(defn adjust-refs [tx]
  (let [refed_uuids            (referenced-uuids tx)
        uuid_2_value_dbid      (uuid-2-value-dbid tx refed_uuids)
        value_dbid_2_prop_dbid (value-dbid-2-prop-dbid
                                tx
                                (set (vals uuid_2_value_dbid)))
        prop_dbid_2_node_dbid  (prop-dbid-2-node-dbid
                                tx
                                (set (vals value_dbid_2_prop_dbid)))
        uuid_2_node_dbid (as-> refed_uuids x
                               (map #(vector % (->> %
                                                    (get uuid_2_value_dbid)
                                                    (get value_dbid_2_prop_dbid)
                                                    (get prop_dbid_2_node_dbid)))
                                    x)
                               (into {} x))]
    (map (fn [e]
           (cond (and (map? e)
                      (not (nil? (get e :jcr.value/reference))))
                 (update-in e [:jcr.value/reference]
                            (fn [old_value]
                              (get uuid_2_node_dbid old_value old_value))) 
                 :else e))
         tx)))

(defn import-tx
  "Liefert die Transaction mit der das file importiert wird"
  [file]
  (let [tx-data (tu/translate-value (nested-entities file))
        import-root-db-id (first tx-data)
        tx-data-with-refs-adjusted (as-> (second tx-data) x
                                         (adjust-refs x))]
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
  "Loads the transaction 'tx' as child node of parent-node.
  The value of 'tx' file contains an edn data structure like this: (#db/id [...] [...])
  The first element of that list is the tempid of the root node to be imported.
  The second element of the list contains the transaction data."
  [conn parent-node root-node-db-id tx]
  (let [parent-new-node-link-tx (tu/add-tx (:db/id parent-node) root-node-db-id)
        full-tx (concat parent-new-node-link-tx tx)]
    (d/transact-async conn full-tx)))

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
                 (second x))))


(defn import-xml
  "Importiert den Inhalt der XML-Datei (System-View) als Child der parent-node.
  Liefert eine session, die den veränderten Zustand berücksichtigt"
  [session parent-node file]
  (let [conn (get-in session [:repository :connection])
        [root-node-db-id tx] (import-tx file)]
    (load-tx conn  parent-node root-node-db-id tx)))
