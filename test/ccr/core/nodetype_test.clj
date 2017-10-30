(ns ccr.core.nodetype-test
  (:require
   [ccr.api.session :as session]
   [ccr.api.nodetype :as ntapi]
   [ccr.core.nodetype :as nt]
   [ccr.core.repository :as crepository]
   [clojure.test :refer :all]
   [datomic.api :as d  :only [q db]]
   ))

(def db-uri "datomic:mem://jcr")

(defn setup-my-fixture [f]
  (d/delete-database db-uri)
  (if (d/create-database db-uri)
    (->> (d/connect db-uri) (crepository/create-schema)))
  (f)  
  (d/delete-database db-uri))

(use-fixtures :each setup-my-fixture)

(deftest test-load-builtin-nodetypes
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
      (is (= "nt:base"  (ntapi/node-type-name (nt/nodetype db "nt:base"))))))
  )

(deftest test-read-nodetype-properties
  (let [conn (d/connect db-uri)
        tx-data (nt/load-builtin-node-types conn)
        db (d/db conn)]
    (testing "Testing nodetype api" 
      (is (= #{"nt:hierarchyNode"}
             (ntapi/declared-supertype-names (nt/nodetype db "nt:linkedFile")))
          "Read declared supertypes from nodetype with one supertype")

      (is (= #{"mix:lastModified" "mix:mimeType"}
             (ntapi/declared-supertype-names (nt/nodetype db "nt:resource")))
          "Read declared supertypes from nodetype with many supertypes")

      (is (= false
             (ntapi/abstract? (nt/nodetype db "nt:resource")))
          "Read jcr:isAbstract")

      (is (= true
             (ntapi/mixin? (nt/nodetype db "mix:lifecycle")))
          "Read jcr:isMixin")

      (is (= false
             (ntapi/orderable-child-nodes? (nt/nodetype db "mix:referenceable")))
          "Read jcr:hasOderableChildNodes")

      (is (= "JCR:CONTENT"
             (ntapi/primary-item-name (nt/nodetype db "nt:file")))
          "Read jcr:primaryItemName")
      
      (is (= true
             (ntapi/can-add-child-node? 
              (nt/nodetype db "nt:file") 
              "jcr:content"))
          "Can add child node?")
      
      (is (= true
             (ntapi/can-add-child-node? 
              (nt/nodetype db "nt:file") 
              "jcr:content" "nt:base"))
          "Can add child node with node-type?")
      (is (= true
             (ntapi/can-add-child-node?
              (nt/nodetype db "nt:unstructured") 
              "jcr:content" "nt:file"))
          "Can add child node with nt:file "
          )
      (is (= false 
             (ntapi/can-add-child-node?
              (nt/nodetype db "nt:file") 
              "jcr:content" "nt:base1"))
          "Can add child node with unknown nodetype")

      (is (= true (ntapi/node-type?
                   (->> (nt/nodetype db "nt:file"))
                   "nt:file"))
          "Testing node-nodetype? with own type")

      (is (= true (ntapi/node-type?
                   (->> (nt/nodetype db "nt:file"))
                   "nt:hierarchyNode"))
          "Testing node-nodetype? with supertype")
      
      (is (= true (ntapi/node-type?
                   (->> (nt/nodetype db "nt:file"))
                   "nt:base"))
          "Testing node-nodetype? with nt:base")
      (is (= false (ntapi/node-type?
                    (->> (nt/nodetype db "nt:file"))
                    "nt:folder"))
          "Testing node-nodetype? with wrong supertype"))
    
    (testing "Read supertypes"
      (is (= #{"nt:hierarchyNode" "nt:base" "mix:created"}
             (->> (nt/nodetype db "nt:file")
                  (ntapi/supertypes)
                  (map ntapi/node-type-name)
                  (set))))
      (is (= #{"mix:referenceable"}
             (->> (nt/nodetype db "mix:shareable")
                  (ntapi/supertypes)
                  (map ntapi/node-type-name)
                  (set)))))
    (testing "Child node definitions"
      (is (= false
             (->> (nt/nodetype db "nt:file")
                  (ntapi/child-node-definitions)
                  (empty?))))
      (is (= 1
             (as-> (nt/nodetype db "nt:file") x
               (ntapi/child-node-definitions x)
               (count x))))
    )))

(deftest test-effective-nodetype
  (testing "without conn"
    (is (thrown? java.lang.NullPointerException (nt/load-builtin-node-types nil))))
  (testing "with conn"
    (let [conn (d/connect db-uri)
          tx-data (nt/load-builtin-node-types conn)]
      (is (not (nil? tx-data))))))

