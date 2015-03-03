(ns ccr.core.transaction-recorder
  (:require [datomic.api :as api]))

(defn current-db
  "Returns a datomic db. If recording is not empty then the :db-after value of the last entry in recording is returned. Otherwise a fresh (datomic.api/db connection) is returned"
  [connection recording]
  (if-let [last-db (->> recording first :tx-result :db-after)]
    last-db
    (api/db connection)))

(defn to-datomic-transaction [session-transaction]
  (apply (:trans-fn session-transaction)
         (:entity-ids session-transaction)))

(defn apply-transaction
  "Applies a session-transaction to a session. Transactions applied to a session are not directly persisted in datomic. They are applied using the datomic.api/with function.

 command-history is a vector of maps. Each map has values to the keys
  :transact-result (like the return value of the transact function),
  :as-if-transction (transaction using entity-ids form previous as-if-db) and
  :tempid-transction (transaction where all as-if-entity-ids are replaced by the initial temp-id if available). "
  [conn transaction-recorder-atom session-transaction]
  (swap! transaction-recorder-atom
         (fn [current-value
              conn
              session-transaction]
           (let [db (current-db conn current-value)
                 transaction (to-datomic-transaction session-transaction)
                 new-transact-result (api/with db transaction)]
             (cons {:tx-result new-transact-result
                    :session-transaction session-transaction}
                   current-value)))
         conn
         session-transaction))

(defn intermediate-db-id-to-tempid-map [recording]
  (->> recording
       (map (fn [r]
              (let [tempids (->> r :tx-result :tempids )
                    db      (->> r :tx-result :db-after )
                    ids     (->> r :session-transaction :entity-ids)]
                (reduce (fn [a v] (assoc a
                                   (api/resolve-tempid db tempids v) v))
                        {} ids))))
       (apply merge)
       ))

(defn save-recorded-transactions [session]
  (let [recording-value @(:recording session)
        i2temp (intermediate-db-id-to-tempid-map recording-value)
        t (->> recording-value
               (map :session-transaction)
               (map (fn [st] (apply (:trans-fn st)
                                   (->> (:entity-ids st)
                                        (map #(get i2temp % %))
                                        ))))
               (reverse)
               (apply concat))
        ]
    (api/transact (:connection session) t )))
