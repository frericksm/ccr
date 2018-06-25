(ns ccr.core.path)

(defn debug [m x] (println m x) x)

(defn absolute-path 
"Takes a jcr path   'path' (see https://docs.adobe.com/content/docs/en/spec/jcr/2.0/3_Repository_Model.html#3.4 Paths)  and builds an absolute path in lexical form (see https://docs.adobe.com/content/docs/en/spec/jcr/2.0/3_Repository_Model.html#3.4.3 Lexical Forms"
  [path]
  (as-> path x
    (interpose "/" x)
    (apply str x))
  )

(defn to-path [lexical-form]
  #_(debug "lexical-form" lexical-form)
  (cond (= "/" lexical-form) (list "/")
        (= "" lexical-form) (list "")
        (.startsWith lexical-form "/") (conj (seq (.split (.substring  lexical-form 1) "/")) "/")
        true (seq (.split lexical-form "/"))
        ))

(defn absolute-path? [lexical-form]
  (= (first (to-path lexical-form)) "/" ))
 
