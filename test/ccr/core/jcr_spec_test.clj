(ns ccr.core.jcr-spec-test
  (:require

   [ccr.core.jcr-spec :as jcr-spec]
   [clojure.spec.alpha :as spec]
   [clojure.test :refer :all]
   ))


(deftest test-names
  (testing "name spec"
    (is (= {:ns [:uri "http://namespace.de"], :local-name "a"} 
           (spec/conform  ::jcr-spec/jcr-name ["http://namespace.de" "a"])) "jcr-name")
    #_(is (= {:ns [:uri "http://namespace.de"], :local-name 1} 
           (spec/conform  ::jcr-spec/jcr-name ["http://namespace.de" 1])) "jcr-name")))



(comment
some paths

1. 
)

(deftest test-paths
  (testing "path spec"
    (is (= ["/" ["" "a" 1]["" "b" 2]]
 [:abs-path {:root "/", :ccr.core.jcr-spec/abs-path-segments [[:name-seg {:name {:ns [:empty ""], :local-name "a"}, :index 1}] [:name-seg {:name {:ns [:empty ""], :local-name "b"}, :index 2}]]}]) "path /a/b")
))
