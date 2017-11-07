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
  
  (testing "Add nodes via root node"
    (let [r (repository/repository {"ccr.datomic.uri" "datomic:mem://jcr"})
          s (rapi/login r)
          rn (sapi/root-node s)]
      
      (is (not (nil? (sapi/workspace s))) "aquiring workspace")

      (is (= "A" 
             (let [a1 (napi/add-node rn "A")
                   a2 (napi/item-name a1)]
               a2)) "add node 'A'")
      (is (= "B" 
             (let [a1 (napi/add-node (napi/node rn "A") "B")
                   a2 (napi/item-name a1)]
               a2)) "add node 'B' to node '/A'")
      (is (= "C" 
             (let [a1 (napi/add-node (sapi/root-node s) "A/C")
                   a2 (napi/item-name a1)]
               a2)) "add node 'A/C' to root node")
      ))

  (testing "Set properties"
    (let [r (repository/repository {"ccr.datomic.uri" "datomic:mem://jcr"})
          s (rapi/login r)
          rn (sapi/root-node s)
          node-a (napi/add-node rn "A") ]
      
      (is (= "value1" 
             (let [p1 (napi/set-property-value node-a "prop1" "value1" "String")
                   v1 (as-> (napi/property node-a "prop1") x
                        (napi/value x))]
               v1)) "setting a property on a node of type nt:unstructed")
      
      (is (= "value2" 
             (let [p1 (napi/set-property-value node-a "prop1" "value2" "String")
                   v1 (as-> (napi/property node-a "prop1") x
                        (napi/value x))]
               v1)) "updating a property on a node of type nt:unstructed")

      (is (= ["value1"] 
             (let [p1 (napi/set-property-values node-a "prop2" ["value1"] "String")
                   v1 (as-> (napi/property node-a "prop2") x
                        (napi/values x))]
               v1)) "setting a property on a node of type nt:unstructed")
      
      (is (= ["value2"] 
             (let [p1 (napi/set-property-values node-a "prop2" ["value2"] "String")
                   v1 (as-> (napi/property node-a "prop2") x
                        (napi/values x))]
               v1)) "updating a property on a node of type nt:unstructed")

      (is (= ["value1" "value2"] 
             (let [p1 (napi/set-property-values node-a "prop3" ["value1" "value2"] "String")
                   v1 (as-> (napi/property node-a "prop3") x
                        (napi/values x))]
               v1)) "updating a property on a node of type nt:unstructed")

      ))


(testing "Save session changes"
    (let [r (repository/repository {"ccr.datomic.uri" "datomic:mem://jcr"})
          s (rapi/login r)
          rn (sapi/root-node s)
          node-a (napi/add-node rn "A") ]
      
      (is (= "value1" 
             (do (napi/set-property-value node-a "prop1" "value1" "String")
                 (sapi/save s)
                 (let [s (rapi/login r)
                       rn (sapi/root-node s)
                       node-a (napi/add-node rn "A")
                       v1 (as-> (napi/property node-a "prop1") x
                            (napi/value x))]
                   v1))) "new property persisted")
      ))
  )
