(ns ccr.core.database-functions
  (:require [datomic.api :as datomic]
            [ccr.core.model-trans :as model-trans]
            [ccr.core.path :as path]
            [ccr.core.cnd :as cnd]
            [ccr.core.transaction-utils :as transaction-utils]
            [ccr.core.transaction-recorder :as transaction-recorder]
            [ccr.core.transactor-support :as transactor-support]))

(defn debug [m x] (println m x) x)

(defn add-node
  "Adds a childnode named 'basename' of nodetype 'primary-node-type' to the :jcr.node with entity-id 'parent-node-id'"
  [db node-id child-id rel-path primary-node-type] 
  #_(debug "add-node" (str node-id child-id rel-path primary-node-type))
  (let [segments (path/to-path rel-path)
        parent-segments (drop-last segments)
        basename (last segments)
        parent-node-id (transactor-support/node-by-path db node-id parent-segments)
        pnt (transactor-support/primary-type db parent-node-id)
        cnd-ids (transactor-support/child-node-definition-ids db pnt)
        matching-cnd-id (transactor-support/matching-cnd-id db cnd-ids basename primary-node-type)
        new-primary-node-type (if (not (nil? primary-node-type))
                                primary-node-type
                                (transactor-support/default-primary-type db matching-cnd-id))
        autocreated-childnodes (transactor-support/autocreated-childnodes db matching-cnd-id)
        autocreated-properties (transactor-support/autocreated-properties db new-primary-node-type)]
    #_(println autocreated-properties)
    (if (nil? new-primary-node-type) (throw (IllegalArgumentException. "no primary nodetype specified")))
    (as-> (model-trans/node-transaction basename autocreated-childnodes
                                        autocreated-properties) x
      (transaction-utils/translate-value2 x child-id)
      (let [child-node-tx (second x)
            tx (concat (transaction-utils/add-tx parent-node-id child-id) child-node-tx)]
        #_(println "tx" tx)
        tx
        ))))

(defn set-property
  "Sets the property of the node (with node-id) called name to the specified values. Even if this property is single-valued the parameter values has to be a coll"
  [db node-id property-id name values jcr-type multiple?]
  (do (when (nil? node-id) (throw (IllegalArgumentException. "Parameter 'node-id' must not be null")))
      (when (nil? name) (throw (IllegalArgumentException. "Parameter 'name' must not be null")))
      (when (nil? jcr-type) (throw (IllegalArgumentException. "Parameter 'jcr-type' must not be null")))
      (when (not (coll? values)) (throw (IllegalArgumentException. "Parameter 'values' must be a collection")))
      (let [pt (transactor-support/primary-type db node-id)
            propdef-ids (transactor-support/property-definition-ids db pt)
            matching-propdef-id (transactor-support/matching-propdef-id db propdef-ids name multiple? jcr-type)
            value-attr (cnd/jcr-value-attr jcr-type)
            prop-id (transactor-support/property-by-name db node-id name)]
        (if (nil? prop-id)

          ;; new property
          (as-> (model-trans/property-transaction name value-attr values) x
            (transaction-utils/translate-value2 x property-id)
            (let [ ;;property-id (first x)
                  property-tx (second x)
                  tx (concat (transaction-utils/add-prop-tx node-id property-id) property-tx)]
              #_(println "tx" tx)
              tx))

          ;; existing property
          (let [old-values-entities (transactor-support/value-entities db prop-id)
                {:keys [retractions additions]} (model-trans/update-values prop-id old-values-entities values value-attr)]
            (as-> additions x
              (transaction-utils/translate-value2 x nil)
              (let [value-ids (first x)
                    property-tx (second x)
                    tx (concat retractions (transaction-utils/add-vals-tx prop-id value-ids) property-tx)]
                #_(println "tx" tx)
                tx))))))
  )

(def entitity-attributes #{:db/id :jcr.value/reference :jcr.node/children :jcr.node/properties :jcr.property/values})

(defn ^:private replace-ids-in-map [idmap tx-segment-map]
  (as-> tx-segment-map x
    (map (fn [[k v]]
           (cond
             (and (contains? entitity-attributes k) (coll? v))        [k (map (fn [single-value] (get idmap single-value single-value)) v)]
             (and (contains? entitity-attributes k) (not (coll? v)))  [k (get idmap v v)]
             true                                                     [k v])) x)
    (into {} x)))

(defn ^:private replace-ids-in-coll [idmap tx-segment-vector]
  (as-> tx-segment-vector x
    (map (fn [v] (if (coll? v)
                   (replace-ids-in-coll idmap v)
                   (get idmap v v))) x)
    (vec x))) 

(defn replace-ids [idmap tx]
  (as-> tx x
    (map (fn [tx-segment]
           #_(debug "tx-segment" tx-segment)
           #_(debug "tx-segment-type" (type tx-segment))
           #_(debug "tx-segment-coll?" (coll? tx-segment))
           (cond
             (instance? datomic.db.DbId tx-segment)  (get idmap tx-segment tx-segment)
             (map? tx-segment) (replace-ids-in-map idmap tx-segment)
             (coll? tx-segment) (replace-ids-in-coll idmap tx-segment)
             true (get idmap tx-segment tx-segment))) x)))


#_(condp = (first tx) 
      :add-node     (as-> tx x
                      (assoc-in x [1] (get id2temp (nth tx 1) (nth tx 1)))
                      (assoc-in x [2] (get id2temp (nth tx 2) (nth tx 2))))
      :set-property (as-> tx x
                      (assoc-in x [1] (get id2temp (nth tx 1) (nth tx 1)))
                      (assoc-in x [2] (get id2temp (nth tx 2) (nth tx 2))))
      )

(defn ^:private detempidify
  "Replace all tempids in the datomic transaction (vector) 'tx' by entity-ids from the bijective map 'id2temp'"
  [id2temp tx]
  (let [temp2id (clojure.set/map-invert id2temp)]
    (as-> tx x
      (replace-ids temp2id x)
      (vec x))))

(defn ^:private tempidify
  "Replace all entity-ids in the datomic transaction (vector) 'tx' by tempids from the bijective map 'id2temp'"
  [id2temp tx]
  (debug "tempidify" tx)
  (as-> tx x
    (map  (partial replace-ids id2temp) x)
    (vec x)))

(defn save 
  "Save changes made in a session to the database by providing the transaction to be transacted against the connection"
  [db transactions]
  (loop [tx (debug "first" (first transactions))
         rest-transactions (debug "rest" (rest transactions))
         final-tx nil
         tx-results nil
         id2temp nil]
    (if (nil? tx)
      final-tx
      (do (debug "id2temp" id2temp)
        (let [db-after (get (first tx-results) :db-after)
              current-db (or db-after db-after db)
              tx-fn (get (datomic/entity current-db (first tx)) :db/fn)
              sub-tx (detempidify id2temp (rest tx))
              ;;_  (debug "sub-tx" (type sub-tx))
              temp-tx (apply tx-fn (cons current-db sub-tx))
              result (datomic/with current-db temp-tx) 
              new-tx-results (cons result tx-results)
              new-id2temp (transaction-recorder/intermediate-db-id-to-tempid-map {:tx-result [result]
                                                                                  :tx temp-tx}
                                                                                 id2temp)
              new-final-tx (cons (tempidify new-id2temp temp-tx) final-tx)]
          (println "temp-tx" temp-tx )
          (recur (first rest-transactions)
                 (rest rest-transactions)
                 new-final-tx
                 new-tx-results
                 new-id2temp))))))
