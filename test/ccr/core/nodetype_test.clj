(ns ccr.core.nodetype-test
  (:require [clojure.test :refer :all]
            [ccr.api.repository :as repository]
            [ccr.api.session :as session]
            [ccr.core.nodetype :as nt]
            [ccr.core.repository :as crepository]
            [datomic.api :as d  :only [q db]]
            ))

(def db-uri "datomic:mem://jcr")

(defn setup-my-fixture [f]
  (if (d/create-database db-uri)
    (->> (d/connect db-uri) (crepository/create-schema)))
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
  (let [conn (d/connect db-uri)
        tx-data (nt/load-builtin-node-types conn)
        db (d/db conn)]
    (testing "with empty mem db"
      (is (= "nt:base"  (nt/nodetype db "nt:base")))))
  )


