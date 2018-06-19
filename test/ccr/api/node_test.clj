(ns ccr.api.node-test
  (:require [clojure.test :refer :all]
            [ccr.core.repository :as repository]
            [ccr.api.repository :as rapi]
            [ccr.api.session :as session]
            [ccr.api.node :as node]
            ))

(deftest test-session
  (testing "Add nodes via root node"
    (let [r (repository/repository {"ccr.datomic.uri" "datomic:mem://jcr"})
          s (rapi/login r)
          rn (session/root-node s)
          a1 (node/add-node rn "A")
          a1-name (node/item-name a1)]
(session/save s)
      (is (= "A" a1-name) "add node 'A'"))))
