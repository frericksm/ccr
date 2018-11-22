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
    (is (= [:abs-path [[:path {:root "/", :path [[:self "."]]}]]] 
           (spec/conform ::jcr-spec/jcr-path [["/" "." ]])) "root path")
    (is (= [:abs-path
            [[:path
              {:root "/",
               :path
               [[:name-seg
                 {:name
                  {:ns [:uri "http://namespace.de"], :local-name "a"},
                  :index 1}]
                [:name-seg
                 {:name
                  {:ns [:uri "http://namespace.de"], :local-name "b"},
                  :index 2}]]}]]] 
           (spec/conform ::jcr-spec/jcr-path [["/" ["http://namespace.de" "a" 1] ["http://namespace.de" "b" 2]]])) "root path")
    (is (= [:abs-path
            [[:identifier "2846e798-28e5-4d31-ae5b-12b4c7a1d2c4"]]] 
           (spec/conform ::jcr-spec/jcr-path ["2846e798-28e5-4d31-ae5b-12b4c7a1d2c4" ])) "identifier path")
    
    (is (= [:rel-path [[:self "."]]] (spec/conform ::jcr-spec/jcr-path ["." ]) )"self path")
    (is (= [:rel-path [[:parent ".."]]] (spec/conform ::jcr-spec/jcr-path [".."])))))
