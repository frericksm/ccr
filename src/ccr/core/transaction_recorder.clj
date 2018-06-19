(ns ccr.core.transaction-recorder
  (:require [datomic.api :as datomic]
            [clojure.spec.alpha :as spec]))

(defn debug [m x] #_(println m x) x)


(spec/def ::recorded-tx (spec/keys :req-un [::tx  ::db-after ::db-before]))
(spec/def ::session (spec/coll-of ::recorded-tx))


(def entitity-attributes #{:db/id :jcr.value/reference :jcr.node/children :jcr.node/properties :jcr.property/values})

(defn replace-ids-in-map [idmap tx-segment-map]
  (as-> tx-segment-map x
    (map (fn [[k v]]
           (cond
             (and (contains? entitity-attributes k) (instance? datomic.db.DbId v))  [k (get idmap v v)]
             (and (contains? entitity-attributes k) (coll? v))        [k (map (fn [single-value] (get idmap single-value single-value)) v)]
             (and (contains? entitity-attributes k) (not (coll? v)))  [k (get idmap v v)]
             true                                                     [k v])) x)
    (into {} x)))

(defn replace-ids-in-coll [idmap tx-segment-vector]
  (as-> tx-segment-vector x
    (map (fn [v]
           (cond
             (instance? datomic.db.DbId v) (get idmap v v)
             ;;(coll? v) (replace-ids-in-coll idmap v)
             true      (get idmap v v))) x)
    (vec x))) 

(defn replace-ids [idmap tx-segment]
  (cond
    (instance? datomic.db.DbId tx-segment) (get idmap tx-segment tx-segment)
    (map? tx-segment) (replace-ids-in-map idmap tx-segment)
    (coll? tx-segment) (replace-ids-in-coll idmap tx-segment)
    true (get idmap tx-segment tx-segment))
  #_(as-> tx x
    (map (fn [tx-segment]
           #_(debug "tx-segment" tx-segment)
           #_(debug "tx-segment-type" (type tx-segment))
           #_(debug "tx-segment-coll?" (coll? tx-segment))
           (cond
             (instance? datomic.db.DbId tx-segment) (get idmap tx-segment tx-segment)
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

(defn detempidify
  "Replace all tempids in the datomic transaction (vector) 'tx' by entity-ids from the bijective map 'id2temp'"
  [id2temp tx]
  (let [temp2id (clojure.set/map-invert id2temp)
        result (as-> tx x
                 (map (partial replace-ids temp2id) x)
                 (vec x))]
    result
    ))
 
(defn tempidify
  "Replace all entity-ids in the datomic transaction (vector) 'tx' by tempids from the bijective map 'id2temp'"
  [id2temp tx]
  #_(debug "tempidify" tx)
  (as-> tx x
    (map  (partial replace-ids id2temp) x)
    (vec x)))


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
         (fn [current-value t]
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
  (debug "record-tx " "entry")
  (debug "record-tx-session" session)
  (spec/assert ::session session)
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

(defn replace-entity-ids-in 
  "Erstzt entity-ids durch temp-ids in db-function trsnsactions"
  [id2temp tx]
  (cond  
    (= :add-node(first tx))      (as-> tx x
                                   (assoc-in x [1] (get id2temp (nth tx 1) (nth tx 1)))
                                   (assoc-in x [2] (get id2temp (nth tx 2) (nth tx 2))))
    (= :set-property (first tx)) (as-> tx x
                                   (assoc-in x [1] (get id2temp (nth tx 1) (nth tx 1)))
                                   (assoc-in x [2] (get id2temp (nth tx 2) (nth tx 2))))
    true (throw (IllegalArgumentException. (format "replace-entity-ids-in: %s" tx)))
    
    ))
 
(defn ^:private entity-ids-from [tx]
  #_(debug "entity-ids-from/1" tx)
  (cond 
    (= :add-node                 (first tx)) (vector (nth tx 1) (nth tx 2))
    (= :set-property             (first tx)) (vector (nth tx 1) (nth tx 2))
    (= :append-position-in-scope (first tx)) (vector (nth tx 1) (nth tx 3))
    (map? tx)                    (as-> (filter (fn [[k v]] (contains? entitity-attributes k)) tx) x
                                   #_(reduce (fn [a [k v]] (if coll? v) (concat a v) (cons v a)) nil x)
                                   (reduce (fn [a [k v]]
                                             (cond
                                               (instance? datomic.db.DbId v) (cons v a)
                                               (coll? v) (concat v a)
                                               true (cons v a))) nil x))  
    true (throw (IllegalArgumentException. (format "entity-ids-from: %s" tx)))
    ))

(defn intermediate-db-id-to-tempid-map 
  ([recording]
   (intermediate-db-id-to-tempid-map  recording {}))
  ([recording to-extend]
   #_(debug "recording" recording)
   (as-> recording x
     (map (fn [r] 
            (let [tempids (->> r :tx-result :tempids)
                  db      (->> r :tx-result :db-after)
                  ids     (as-> (get r :tx) x
                            (map entity-ids-from x)
                            (apply concat x))] 
              #_(debug "ids" ids)
              (reduce (fn [a v] (let [intermediate-db-id (datomic/resolve-tempid db tempids v)]
                                  (if (not (nil? intermediate-db-id))
                                    (assoc a intermediate-db-id v)
                                    a)))
                      to-extend ids))) x)
     (apply merge x)
     )))

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
(defn ^:private  replace-entity-ids
  "Ersetzt in der Transaktion entity-ids durch temp-ids, falls die entity-id nicht  "
  [id2temp txs]
  (as-> txs x
    (map (fn [tx] (replace-entity-ids-in id2temp tx)) x)
    (vec x)))

(defn save
  "Commits the collected transactions against the connection"
  [session]
        (debug "ccr.core.transaction-recorder/save" "entry")

  (if-let [state (debug "state" (reset-transaction-recorder-atom (:transaction-recorder-atom session)))]

    (let [id2temp (debug "intermediate-db-id-to-tempid-map"  (intermediate-db-id-to-tempid-map state))]
      (as-> state x
        (map :tx x) ;; enthält die letzte Transition als erstes Element
        (reverse x) ;; daher die Reihenfolge umkehren
        (map (partial replace-entity-ids id2temp) x) ;; Die entity-ids wieder durch die ursprünglichen Temp-Ids ersetzen
        (apply concat x) ;; zu einer Liste von Transaktionen zusammenhängen
        (vec x)  ;; in einen Vektor umwandeln
        (vector :save x)   ;; die tx-function :save aufrufen
        (vector x) ;; in einen Vector packen  
        (debug "ccr.core.transaction-recorder/save-tx" x)
        (datomic/transact (:conn session) x)))))
