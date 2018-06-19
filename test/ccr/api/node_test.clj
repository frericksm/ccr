(ns ccr.api.node-test
  (:require [clojure.test :refer :all]
            [ccr.core.repository :as repository]
            [ccr.api.repository :as rapi]
            [ccr.api.session :as session]
            [ccr.api.node :as node]
            ))

(deftest test-node
  (testing "Add nodes via root node"
    (let [r (repository/repository {"ccr.datomic.uri" "datomic:mem://jcr"})
          s (rapi/login r)
          rn (session/root-node s)
          a1 (node/add-node rn "A")
          b  (node/add-node a1 "B")
          a1-name (node/item-name a1)
          p (node/parent b)
          b-parent-name  (node/item-name p)
b-path (node/path b)
          ]
      (session/save s)
      (is (= "/A/B" b-path) "path of b is : /A/B")
      (is (= "A" a1-name) "add node 'A'")
      (is (= "A" b-parent-name) "parents name")
      (is (not (nil? p))"parent nicht gefunden"))))
