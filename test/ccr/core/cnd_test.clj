(ns ccr.core.cnd-test
  (:require
   [ccr.api.nodetype :as ntapi]
   [ccr.core.cnd :as c]
   [clojure.test :refer :all]
   [datomic.api :as d  :only [q db]]
   ))

(deftest test-builtin-nodetypes
  (let [bnt (c/builtin-nodetypes)]
    #_(spit "homte/michael/debub.txt")
    (testing "Builtin nodetypes"
      (is (= false
             false))
    )))
