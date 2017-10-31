(ns ccr.core.transaction-recorder
  (:require [datomic.api :as api]
            [clojure.spec.alpha :as s]))

(defn debug [m x] (println m x) x)

(s/def ::tx-result (s/keys :req [::db-after ::db-before]))
(s/def ::recording (s/coll-of ::tx-result))

(defn ^:private calc-current-db
  "Returns a datomic db. If recording is not empty then the :db-after value of the last entry in recording is returned. Otherwise a fresh (datomic.api/db connection) is returned"
  [connection recording]
  (if-let [last-db (->> recording first :tx-result :db-after)]
    last-db
    (api/db connection))
  )

(defn ^:private datomic-transaction [session-transaction]
  (apply (:trans-fn session-transaction)
         (:entity-ids session-transaction)))

(defn apply-transaction
  "Applies a session-transaction to a session. A session-transaction is a normal datomic transaction except that entity-ids of the sessions's current-db are used to identify entities. Transactions applied to a session are not directly persisted in datomic. They are applied using the datomic.api/with function.

 command-history is a vector of maps. Each map has values to the keys
  :tx-result (like the return value of the transact function),
  :as-if-transction (transaction using entity-ids form previous as-if-db) and
  :tempid-transaction (transaction where all as-if-entity-ids are replaced by the initial temp-id if available). "
  [conn transaction-recorder-atom tx]
  (swap! transaction-recorder-atom
         (fn [current-value
              t]
           (let [db (calc-current-db conn current-value)
                 new-transact-result (api/with db t)]
             (cons {:tx-result new-transact-result
                    :as-if-transaction t
                    :tempid-transaction []}
                   current-value)))
         tx))

#_(defn intermediate-db-id-to-tempid-map [recording]
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

#_(defn save-recorded-transactions [session]
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


(defn current-db 
  "Returns the current value of db to execute queries"
  [session]
  (calc-current-db (:conn session) (deref (:transaction-recorder-atom session))))

(defn record-tx
  "Records the transaction tx to the recorder"
  [session tx]
  (as-> (apply-transaction (:conn session)
                           (:transaction-recorder-atom session)
                           tx) x
    (first x)))


