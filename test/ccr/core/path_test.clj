(ns ccr.core.path-test
  (:require

   [ccr.core.path :as path]
   [clojure.test :refer :all]
   ))


(deftest test-lexical-form
  (testing "rel path"
    (is (= "/a/b" 
           (path/lexical-form [["http://zu.de" "a"1 ] ["http://zu.de" "b"2 ] ".."])))
    #_(is (= "{http://zu.de}/a[1]/{http://zu.de}/b[2]" 
           (path/lexical-form (path/conformed-path [["http://zu.de" "a"1 ] ["http://zu.de" "b"2 ] ".."]))))))
