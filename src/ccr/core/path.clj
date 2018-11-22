(ns ccr.core.path
  (:require    [clojure.spec.alpha :as spec]
               [ccr.core.jcr-spec :as jcr-spec]))

(defn debug [m x] (println m x) x)


(defmulti lexical-form 
  "Takes a jcr path   'path' (see https://docs.adobe.com/content/docs/en/spec/jcr/2.0/3_Repository_Model.html#3.4%20Paths)  
  and builds an absolute path in lexical form (see https://docs.adobe.com/content/docs/en/spec/jcr/2.0/3_Repository_Model.html#3.4.3%20Lexical Forms"
  first)
(defmethod  lexical-form :rel-path [conformed-path] (as-> (rest conformed-path) x
                                                      (map lexical-form x )))
(defmethod  lexical-form :abs-path [conformed-path] "abspath")
(defmethod  lexical-form :name-seg [conformed-path] "name-seg!!")
(defmethod  lexical-form :default  [conformed-path]  (str "default " conformed-path))
(defn conformed-path
  "Conforms path to jcr-path spec" [path]
  (let [conformed-path (spec/conform ::jcr-spec/jcr-path  path)]
    (if (= conformed-path :clojure.spec.alpha/invalid)
      (throw (IllegalArgumentException. (format "Illegal jcr-path %s" path))))
    (println conformed-path )
    conformed-path
    ))

(defn jcr-path 
  "Anwortet mit dem  JCR-Path zum Path path-in-lexical-form"

  [path-in-lexical-form]
  #_(debug "path-in-lexical-form" path-in-lexical-form)
  (cond (= "/" path-in-lexical-form) (list "/")
        (= "" path-in-lexical-form) (list "")
        (.startsWith path-in-lexical-form "/") (conj (seq (.split (.substring  path-in-lexical-form 1) "/")) "/")
        true (seq (.split path-in-lexical-form "/"))
        ))

(defn absolute-path? [lexical-form]
  (= (first (jcr-path lexical-form)) "/" ))
(defn absolute-path [jcr-path]
  nil)

