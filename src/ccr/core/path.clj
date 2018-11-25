(ns ccr.core.path
  (:require    [clojure.spec.alpha :as spec]
               [ccr.core.jcr-spec :as jcr-spec]))

(defn debug [m x] (println m x) x)






(defn lexical-form-rel-path 
"Builds a lexical path from  a conform result to spec :jcr-spec/rel-path" 
[conformed-path]
  (let [lx-segs (map lexical-form (first conformed-path))]
(clojure.string/join "/"  lx-segs )))
(defn lexical-form-abs-path 
"Builds a lexical path from  a conform result to spec :jcr-spec/abs-path" 
[conformed-path]
  (let [abspath (first conformed-path)
        root (:root abspath  )
        segs ( :ccr.core.jcr-spec/abs-path-segments abspath)
        lx-segs (map lexical-form segs)]
(clojure.string/join  "/" (cons root lx-segs ))))

(defn lexical-form-name-seg
"Builds a lexical path fr a conform result to spec :jcr-spec/abs-path" 
[conformed-path]
  (let [name-seg (first conformed-path)
        name (:name name-seg)
        ns (second (get-in name-seg [:name :ns]))
        local-name (get-in name-seg [:name :local-name])
        index (get-in name-seg [:name :uri])]
(str "{" ns "}" local-name index)))


(defmulti lexical-form 
  "Takes a jcr path   'path' (see https://docs.adobe.com/content/docs/en/spec/jcr/2.0/3_Repository_Model.html#3.4%20Paths)  
  and builds an absolute path in lexical form (see https://docs.adobe.com/content/docs/en/spec/jcr/2.0/3_Repository_Model.html#3.4.3%20Lexical Forms"
  first)
#_(defmethod  lexical-form :rel-path [conformed-path] (as-> (rest conformed-path) x
                                                      (map lexical-form x )))
(defmethod  lexical-form :abs-path [conformed-path] (lexical-form-abs-path (rest conformed-path)))
(defmethod  lexical-form :rel-path [conformed-path] (lexical-form-rel-path (rest conformed-path)))
(defmethod  lexical-form :name-seg [conformed-path] (lexical-form-name-seg (rest conformed-path)))
(defmethod  lexical-form :self [conformed-path] ".")
(defmethod  lexical-form :parent [conformed-path] "..")
(defmethod  lexical-form :default  [conformed-path]  (str "default " conformed-path))


(defn analyze [conformed-path]
  (cond (map? conformed-path ) (map lexical-form conformed-path )
        ( vector? conformed-path)  (map lexical-form conformed-path )
))

(defn conformed-path
  "Takes a jcr path and conforms it to the spec :jcr-spec/jcr-path " [path]
  (let [conformed-path (spec/conform ::jcr-spec/jcr-path  path)]
    (if (= conformed-path :clojure.spec.alpha/invalid)
      (throw (IllegalArgumentException. (format "Illegal jcr-path %s" path))))
    #_(println conformed-path )
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


