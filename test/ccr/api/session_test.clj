(ns ccr.api.session-test
  (:require [clojure.test :refer :all]
            [ccr.core.repository :as repository]
            [ccr.api.repository :as rapi]
            [ccr.api.session :as session]
            [ccr.api.node :as node]
            ))

(deftest test-session
  (testing "Session"
    (let [r (repository/repository {"ccr.datomic.uri" "datomic:mem://jcr"})
          s (rapi/login r)]
      (testing "aquiring root node"
        (is (not (nil? (session/root-node s)))))
      (testing "aquiring workspacee"
        (is (not (nil? (session/workspace s)))))))
  
  (testing "Add nodes via root node"
    (let [r (repository/repository {"ccr.datomic.uri" "datomic:mem://jcr"})
          s (rapi/login r)
          rn (session/root-node s)]
      
      (is (not (nil? (session/workspace s))) "aquiring workspace")

      (is (= "A" 
             (let [a1 (node/add-node rn "A")
                   a2 (node/item-name a1)]
               a2)) "add node 'A'")
      (is (= "B" 
             (let [a1 (node/add-node (node/node rn "A") "B")
                   a2 (node/item-name a1)]
               a2)) "add node 'B' to node '/A'")
      (is (= "C" 
             (let [a1 (node/add-node (session/root-node s) "A/C")
                   a2 (node/item-name a1)]
               a2)) "add node 'A/C' to root node")
      ))

  (testing "Set properties"
    (let [r (repository/repository {"ccr.datomic.uri" "datomic:mem://jcr"})
          s (rapi/login r)
          rn (session/root-node s)
          node-a (node/add-node rn "A") ]
      
      (is (= "value1" 
             (let [p1 (node/set-property-value node-a "prop1" "value1" "String")
                   v1 (as-> (node/property node-a "prop1") x
                        (node/value x))]
               v1)) "setting a property on a node of type nt:unstructed")
      
      (is (= "value2" 
             (let [p1 (node/set-property-value node-a "prop1" "value2" "String")
                   v1 (as-> (node/property node-a "prop1") x
                        (node/value x))]
               v1)) "updating a property on a node of type nt:unstructed")

      (is (= ["value1"] 
             (let [p1 (node/set-property-values node-a "prop2" ["value1"] "String")
                   v1 (as-> (node/property node-a "prop2") x
                        (node/values x))]
               v1)) "setting a property on a node of type nt:unstructed")
      
      (is (= ["value2"] 
             (let [p1 (node/set-property-values node-a "prop2" ["value2"] "String")
                   v1 (as-> (node/property node-a "prop2") x
                        (node/values x))]
               v1)) "updating a property on a node of type nt:unstructed")

      (is (= ["value1" "value2"] 
             (let [p1 (node/set-property-values node-a "prop3" ["value1" "value2"] "String")
                   v1 (as-> (node/property node-a "prop3") x
                        (node/values x))]
               v1)) "updating a property on a node of type nt:unstructed")

      ))


(testing "Save session changes"
    (let [r (repository/repository {"ccr.datomic.uri" "datomic:mem://jcr"})
          s (rapi/login r)
          rn (session/root-node s)
          node-a (node/add-node rn "A")]
      
      (is (= "value1" 
             (do (node/set-property-value node-a "prop1" "value1" "String")
                 (println (session/save s))
                 (let [s2 (rapi/login r)
                       rn2 (session/root-node s2)
                       node-a2 (node/node rn2 "A")
                       v1 (as-> (node/property node-a2 "prop1") x
                            (node/value x))]
                   v1))) "new property persisted")
      ))
  )
