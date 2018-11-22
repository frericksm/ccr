(ns ccr.core.database-functions
  (:require [datomic.api :as datomic]
            [ccr.core.model-trans :as model-trans]
            [ccr.core.path :as path]
            [ccr.core.cnd :as cnd]
            [ccr.core.transaction-utils :as transaction-utils]
            [ccr.core.transaction-recorder :as transaction-recorder]
            [ccr.core.transactor-support :as transactor-support]))

(defn debug [m x] #_(println m x) x)

(defn add-node
  "Adds a childnode named 'basename' of nodetype 'primary-node-type' to the :jcr.node with entity-id 'parent-node-id'"
  [db node-id child-id rel-path primary-node-type] 
  (debug "add-node:" "entry")
  (debug "add-node: node-id" node-id )
  (debug "add-node: db" db )
  (debug "add-node: child-id" child-id )
  (debug "add-node: rel-path" rel-path )
  (debug "add-node: primary-node-type" primary-node-type)
  (let [segments (path/jcr-path rel-path)
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
            add-tx (transaction-utils/add-tx parent-node-id child-id)
            result-tx (concat add-tx child-node-tx)]
        (debug "add-node: return " result-tx)))))

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


(defn save 
  "Save changes made in a session to the database by providing the transaction to be transacted against the connection"
  [db transactions]

  (debug "save:" "entry")
  (debug "save: db" db )
  (debug "save: transactions" transactions )


  (debug "save- return" (loop [tx (first transactions)
                                     rest-transactions (rest transactions)
                                     final-tx nil
                                     tx-results nil
                                     id2temp nil]
                          (if (nil? tx) 
                            final-tx
                            (let [db-after (get (first tx-results) :db-after) ;; falls vorhanden
                                  current-db (or db-after db)                 ;; sonst current-db
                                  dbfn-name (debug "dbfn-name" (first tx))    ;; db-fn name holen 
                                  dbfn-entity (debug "dbfn-entity" (datomic/entity current-db dbfn-name))  ;; db-fn entity  lesen
                                  tx-fn (debug "tx-fn" (get dbfn-entity :db/fn))                           ;; db-fn lesen
                                  tx-fn-args (transaction-recorder/detempidify id2temp (rest tx))          ;; args aufbereiten
                                  _ (debug "tx-fn-args" tx-fn-args)
                                  temp-tx (vec (apply tx-fn (cons current-db tx-fn-args)))                 ;; db-fn aufrufen
                                  _ (debug "temp-tx line: 104" temp-tx)                                    ;; das ergbenis des aufrufs
                                  detemp-tx (debug "detemp-tx" (transaction-recorder/detempidify id2temp temp-tx))

                                  result (datomic/with current-db detemp-tx) 
                                  new-tx-results (cons result tx-results)
                                  new-id2temp (transaction-recorder/intermediate-db-id-to-tempid-map
                                               [{:tx-result result
                                                 :tx temp-tx}]
                                               id2temp)
                                  new-final-tx (concat (transaction-recorder/tempidify new-id2temp temp-tx) final-tx)]

                              (recur (debug "recur- tx" (first rest-transactions))
                                     (debug "recur- rest-transactions" (rest rest-transactions))
                                     (debug "recur- final-tx" new-final-tx)
                                     (debug "recur- tx-results" new-tx-results)
                                     (debug "recur new-id2temp" new-id2temp)))))))
