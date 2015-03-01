(ns ccr.api.session-test
  (:require [clojure.test :refer :all]
            [ccr.core.repository :as repository]
            [ccr.api.repository :as rapi]
            [ccr.api.session :as sapi]
            [ccr.api.node :as napi]
            ))

(deftest test-session
  (testing "Session"
    (let [r (repository/repository {"ccr.datomic.uri" "datomic:mem://jcr"})
          s (rapi/login r)]
      (testing "aquiring root node"
        (is (not (nil? (sapi/root-node s)))))
      (testing "aquiring workspacee"
        (is (not (nil? (sapi/workspace s)))))))
  
  (testing "Root node"
    (let [r (repository/repository {"ccr.datomic.uri" "datomic:mem://jcr"})
          s (rapi/login r)
          rn (sapi/root-node s)]
      (testing "add node node"
        (is (not (nil? (napi/add-node rn "A")))))
      (testing "aquiring workspacee"
        (is (not (nil? (sapi/workspace s))))))))
