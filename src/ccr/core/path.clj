(ns ccr.core.path)


(defn to-path [lexical-form]
  (cond (= "/" lexical-form) (list "/")
        (= "" lexical-form) (list "")
        (.startsWith lexical-form "/") (conj (seq (.split (.substring  lexical-form 1) "/")) "/")
        true (seq (.split lexical-form "/"))
        ))

(defn absolute-path? [lexical-form]
  (= (first (to-path lexical-form)) "/" ))
