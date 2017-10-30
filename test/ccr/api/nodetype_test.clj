(ns ccr.api.nodetype-test
  (:require [clojure.test :refer :all]
            [ccr.core.repository :as repository]
            [ccr.api.repository :as api]
            [ccr.api.session :as s]
            [ccr.api.workspace :as w]
            [ccr.api.nodetype :as nt]
            ))

(deftest test-childnode-definitions 
  (testing "with empty repository"
    (let [r (repository/repository {"ccr.datomic.uri" "datomic:mem://jcr"})
          s (api/login r)
          w (s/workspace  s)
          ntm (w/node-type-manager w)
          nt (nt/node-type ntm "nt:folder")
          cnd (nt/child-node-definitions nt)]
      (is (not (nil? nt))))))
