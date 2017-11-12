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
    (let [;;_ (datomic/create-database "datomic:mem://jcr")
          ;;db (datomic/db (datomic/connect "datomic:mem://jcr"))
          tx  [[17592186047978 (datomic/tempid :db.part/user) "A" nil]]]
      (is (= (debug "in" tx) (debug "out" (transaction-recorder/detempidify nil tx))))
      )))
