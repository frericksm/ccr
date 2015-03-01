(ns ccr.api.repository-test
  (:require [clojure.test :refer :all]
            [ccr.core.repository :as repository]
            [ccr.api.repository :as api]
            ))

(deftest test-create-repository
  (testing "with empty mem db"
    (let [r (repository/repository {"ccr.datomic.uri" "datomic:mem://jcr"})]
      (is (not (nil? r)))))
  
  (testing "with invalid db uri"
    (is (thrown? IllegalArgumentException
                 (repository/repository {"ccr.datomic.uri" "datomic:mem1://jcr"}))))
  
  (testing "with wrong parameter"
    (is (nil? (repository/repository {"ccr.datomic.uri.wrong" "datomic:mem://jcr"}))))
  
  )

(deftest test-login 
  (testing "with empty repository"
    (let [r (repository/repository {"ccr.datomic.uri" "datomic:mem://jcr"})
          s (api/login r)]
      (is (not (nil? s)))))
  
  (testing "without repository"
    (is (thrown? IllegalArgumentException (api/login nil)))))
