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
    (testing "Read declared supertypes from nodetype with one supertype"
      (is (= ["nt:hierarchyNode"]
             (ntapi/declared-supertype-names (nt/nodetype db "nt:linkedFile")))))

    (testing "Read declared supertypes from nodetype with many supertypes"
      (is (= ["mix:lastModified" "mix:mimeType"]
             (ntapi/declared-supertype-names (nt/nodetype db "nt:resource")))))

    (testing "Read jcr:isAbstract"
      (is (= false
             (ntapi/abstract? (nt/nodetype db "nt:resource")))))

    (testing "Read jcr:isMixin"
      (is (= true
             (ntapi/mixin? (nt/nodetype db "mix:lifecycle")))))

    (testing "Read jcr:hasOderableChildNodes"
      (is (= false
             (ntapi/orderable-child-nodes? (nt/nodetype db "mix:referenceable")))))

    (testing "Read jcr:primaryItemName"
      (is (= "JCR:CONTENT"
             (ntapi/primary-item-name (nt/nodetype db "nt:file")))))
    
    (testing "Can add child node?"
      (is (= true
             (ntapi/can-add-child-node? (nt/nodetype db "nt:file") 
                                        "jcr:content")))
      (is (= true
             (ntapi/can-add-child-node? (nt/nodetype db "nt:file") 
                                        "jcr:content" "nt:base")))
      (is (thrown? java.lang.IllegalArgumentException
             (ntapi/can-add-child-node? (nt/nodetype db "nt:file") 
                                        "jcr:content" "nt:base1"))))

    (testing "Testing supertypes"
      (is (= true (ntapi/node-type? (->> (nt/nodetype db "nt:file"))
                                   "nt:file")))
      (is (= true (ntapi/node-type? (->> (nt/nodetype db "nt:file"))
                                   "nt:hierarchyNode")))
      (is (= true (ntapi/node-type? (->> (nt/nodetype db "nt:file"))
                                    "nt:base")))
      (is (= false (ntapi/node-type? (->> (nt/nodetype db "nt:file"))
                                    "nt:folder"))))
    
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
    ))

(deftest test-effective-nodetype
  (testing "without conn"
    (is (thrown? java.lang.NullPointerException (nt/load-builtin-node-types nil))))
  (testing "with conn"
    (let [conn (d/connect db-uri)
          tx-data (nt/load-builtin-node-types conn)]
      (is (not (nil? tx-data))))))

