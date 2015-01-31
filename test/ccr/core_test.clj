(ns ccr.core-test
  (:require [clojure.test :refer :all]
            [ccr.repository :as repository]
            [ccr.api :as api]
            ))




(deftest a-test
  (testing "FIXME, I fail."
    (let [r (repository/repository {"ccr.datomic.uri" "datomic:mem://jcr"})
          s (api/login r)]
      (is (not (nil? s))))))
