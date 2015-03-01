(ns ccr.core.nodetype-test
  (:require [clojure.test :refer :all]
            [ccr.api.repository :as repository]
            [ccr.api.session :as session]
            [ccr.core.nodetype :as nt]
            [datomic.api :as d  :only [q db]]
            ))

(def db-uri "datomic:mem://jcr")

(defn setup-my-fixture [f]
  (d/create-database db-uri)
  (f)  
  (d/delete-database db-uri))

(use-fixtures :each setup-my-fixture)

(deftest test-load-builin-nodetypes
  (testing "without conn"
    (is (thrown? java.lang.NullPointerException (nt/load-builtin-node-types nil))))
  (testing "with conn"
    (let [conn (d/connect db-uri)
          tx-data (nt/load-builtin-node-types conn)]
      (is (not (nil? tx-data))))))

(deftest test-read-nodetype
  (testing "with empty mem db"
    (let [conn (d/connect db-uri)
          tx-data (nt/load-builtin-node-types conn)]
      (is (not (nil? tx-data))))))


