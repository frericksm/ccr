(ns ccr.core.transaction-recorder
  (:require [datomic.api :as datomic]
            [clojure.spec.alpha :as spec]))

(defn debug [m x] (println m x) x)

(spec/def ::tx-result (spec/keys :req [::db-after ::db-before]))
(spec/def ::recording (spec/coll-of ::tx-result))

(defn ^:private calc-current-db
  "Returns a datomic db. If recording is not empty then the :db-after value of the last entry in recording is returned. Otherwise a fresh (datomic.api/db connection) is returned"
  [connection recording]
  (if-let [last-db (->> recording first :tx-result :db-after)]
    last-db
    (datomic/db connection))
  )

(defn ^:private datomic-transaction [session-transaction]
  (apply (:trans-fn session-transaction)
         (:entity-ids session-transaction)))

(defn apply-transaction
  "Applies a session-transaction to a session. A session-transaction is a normal datomic transaction except that entity-ids of the sessions's current-db are used to identify entities. Transactions applied to a session are not directly persisted in datomic. They are applied using the datomic.api/with function.

 command-history is a vector of maps. Each map 'm' has values to the keys
  :tx-result (return value of datomic/with),
  :tx (transaction using entity-ids from (get-in m [:tx-result :db-before])"
  [conn transaction-recorder-atom tx]
  (swap! transaction-recorder-atom
         (fn [current-value
              t]
           (let [db (calc-current-db conn current-value)
                 new-transact-result (datomic/with db t)]
             (cons {:tx-result new-transact-result
                    :tx t}
                   current-value)))
         tx))

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


(defn ^:private reset-transaction-recorder-atom
  "Reset the transaction-recorder-atom to nil and returns the replaced value"
  [transaction-recorder-atom]
  (loop [state (deref transaction-recorder-atom)]
    (if (compare-and-set! transaction-recorder-atom state nil)
      state
      (recur (deref transaction-recorder-atom)))))

(defn ^:private tempids-from [tx]
  (let [[tx-fn eid] tx]
    (vector eid)))

(defn intermediate-db-id-to-tempid-map [recording]
  (as-> recording x
    (map (fn [r]
           (let [tempids (->> r :tx-result :tempids )
                 db      (->> r :tx-result :db-after )
                 ids     (tempids-from (get r :tx))]
             (reduce (fn [a v] (assoc a
                                      (datomic/resolve-tempid db tempids v) v))
                     {} ids))) x)
    (apply merge x)
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
    (datomic/transact (:connection session) t )))


;; eid-n  
(defn ^:private replace-temp-ids [{:keys [tx-result tx]}]
  (let [{:keys [db-before tempids]} tx-result
        [tx-fn eid] tx
        new-eid (datomic/resolve-tempid )]
    )
  
  )

(defn save
  "Commits the collected transactions against the connection"
  [session]
  (if-let [state (reset-transaction-recorder-atom (:transaction-recorder-atom session))]
    (let [id2temp (intermediate-db-id-to-tempid-map state)])
    (as-> state x
      (reverse x)
      (map :tx x)
      (map replace-temp-ids x)
      (debug "save" x))))
