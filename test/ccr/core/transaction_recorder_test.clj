(ns ccr.core.transaction-recorder-test
  (:require
   [ccr.api.nodetype :as ntapi]
   [ ccr.core.transaction-recorder :as transaction-recorder]
   [clojure.test :refer :all]
   [datomic.api :as datomic]
   ))

(defn debug [m x] (println m x) x)

(def db-uri "datomic:mem://jcr")



(deftest test-replace-ids-in-coll
  (testing "test-replace-ids-in-coll"
    (let [tx  [17592186047978 (datomic/tempid :db.part/user) "A" nil]]
      (is (= (debug "in" tx) (debug "out" (transaction-recorder/replace-ids-in-coll nil tx))))
      )))

(deftest test-replace-ids
  (testing "test-replace-ids"
    (let [tx  [[17592186047978 (datomic/tempid :db.part/user) "A" nil]]]
      (is (= (debug "in" tx) (debug "out" (transaction-recorder/replace-ids nil tx))))
      )))

(deftest test-tempidify
  (testing "Test tempidify"
    (let [;;_ (datomic/create-database "datomic:mem://jcr")
          ;;db (datomic/db (datomic/connect "datomic:mem://jcr"))
          tx  [[17592186047978 (datomic/tempid :db.part/user) "A" nil]]]
      (is (= (debug "in" tx) (debug "out" (transaction-recorder/tempidify nil tx))))
      )))

(deftest test-detempidify
  (testing "Test detempidify"
    (let [tx  [[17592186047978 (datomic/tempid :db.part/user) "A" nil]]]
      (is (= (debug "in" tx) (debug "out" (transaction-recorder/detempidify nil tx))))
      ))
  (testing "Test detempidify2"
    (let [temp-tx [{:db/id 17592186047978, :jcr.node/children #db/id[:db.part/user -1002599]}
                   [:append-position-in-scope 17592186047978 :jcr.node/children #db/id[:db.part/user -1002599] :jcr.node/position]
                   {:jcr.node/name "A" :jcr.node/children [],
                    :jcr.node/properties [#db/id[:db.part/user -1002604]],
                    :db/id #db/id[:db.part/user -1002599]}
                   {:jcr.property/name "jcr:primaryType"
                    :jcr.property/value-attr :jcr.value/name,
                    :jcr.property/values [#db/id[:db.part/user -1002605]],
                    :db/id #db/id[:db.part/user -1002604]}
                   {:jcr.value/name "nt:unstructured"
                    :jcr.value/position 0,
                    :db/id #db/id[:db.part/user -1002605]}]
          #_detemp-tx #_[([:db/id 17592186047978] [:jcr.node/children #db/id[:db.part/user -1002599]])
                     (:append-position-in-scope 17592186047978 :jcr.node/children #db/id[:db.part/user -1002599] :jcr.node/position)
                     ([:jcr.node/name "A"] [:jcr.node/children []] [:jcr.node/properties [#db/id[:db.part/user -1002604]]] [:db/id #db/id[:db.part/user -1002599]])
                     ([:jcr.property/name "jcr:primaryType"] [:jcr.property/value-attr :jcr.value/name] [:jcr.property/values [#db/id[:db.part/user -1002605]]]
                      [:db/id #db/id[:db.part/user -1002604]]) ([:jcr.value/name "nt:unstructured"] [:jcr.value/position 0] [:db/id #db/id[:db.part/user -1002605]])]]
      (is (= {:db/id 17592186047978, :jcr.node/children #db/id[:db.part/user -1002599]} (transaction-recorder/replace-ids nil {:db/id 17592186047978, :jcr.node/children #db/id[:db.part/user -1002599]})))
      (is (= temp-tx (transaction-recorder/detempidify nil temp-tx))))))





