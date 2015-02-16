(ns ccr.core-test
  (:require [clojure.test :refer :all]
            [ccr.repository :as repository]
            [ccr.api :as api]
            ))


(deftest test-create-repository
  (testing "with empty mem db"
    (let [r (repository/repository {"ccr.datomic.uri" "datomic:mem://jcr"})]
      (is (not (nil? r)))))
  
  (testing "with illegal db uri"
    (is (nil? (repository/repository {"ccr.datomic.uri" "datomic:mem1://jcr"}))))
  
  (testing "with wrong parameter"
    (is (nil? (repository/repository {"ccr.datomic.uri.wrong" "datomic:mem://jcr"}))))
  
  )

(deftest test-login 
  (testing "with empty reposiory"
    (let [r (repository/repository {"ccr.datomic.uri" "datomic:mem://jcr"})
          s (api/login r)]
      (is (not (nil? s)))))
  
  (testing "without reposiory"
    (is (thrown? IllegalArgumentException (api/login nil)))))
