(ns ccr.core.path
"This namespace provides functions to handle a jcr paths in   abstract and lexical form.  See https://docs.adobe.com/content/docs/en/spec/jcr/2.0/3_Repository_Model.html#3.4%20Paths" 
  (:require    [clojure.spec.alpha :as spec]
               [ccr.core.jcr-spec :as jcr-spec]
               [clojure.java.io :as io]
               [instaparse.core :as insta]
               [net.cgrand.enlive-html :as html]
               ))


(def lexical-path-parser
  "A function with one parameter of type String. Assumes that the string is a jcr-path in lexical form. See https://docs.adobe.com/content/docs/en/spec/jcr/2.0/3_Repository_Model.html#3.4.3.3%20Lexical%20Path%20Grammar. Parses the string and returns the syntax tree in :hiccup format"
    (insta/parser (slurp (io/resource "path.abnf")) :input-format :abnf))

(declare lexical-form-from-conformed-path) 

(defn debug [m x] (println m x) x)
(defn lexical-form-rel-path 
"Builds a lexical path from  a conform result to spec :jcr-spec/rel-path" 
[conformed-path]
  (let [lx-segs (map lexical-form-from-conformed-path (first conformed-path))]
(clojure.string/join "/"  lx-segs )))
(defn lexical-form-abs-path 
"Builds a lexical path from  a conform result to spec :jcr-spec/abs-path" 
[conformed-path]
  (let [abspath (first conformed-path)
        ;;root (:root abspath  )
        segs ( :ccr.core.jcr-spec/abs-path-segments abspath)
        lx-segs (map lexical-form-from-conformed-path segs)]
(str "/" (clojure.string/join  "/" lx-segs ))))

(defn lexical-form-name-seg
  "Builds a lexical path from a conform result to spec :jcr-spec/abs-path" 
[conformed-path]
  (let [name-seg (first conformed-path)
        name (:name name-seg)
        ns (second (get-in name-seg [:name :ns]))
        local-name (get-in name-seg [:name :local-name])
        index (get-in name-seg [ :index])]
(str "{" ns "}" local-name (if (> index 1  ) (str "[" index "]") ))))


(defmulti lexical-form-from-conformed-path 
  "..." first)
(defmethod  lexical-form-from-conformed-path  :abs-path [conformed-path] (lexical-form-abs-path (rest conformed-path)))
(defmethod  lexical-form-from-conformed-path :rel-path [conformed-path] (lexical-form-rel-path (rest conformed-path)))
(defmethod  lexical-form-from-conformed-path :name-seg [conformed-path] (lexical-form-name-seg (rest conformed-path)))
(defmethod  lexical-form-from-conformed-path :self [conformed-path] ".")
(defmethod  lexical-form-from-conformed-path :parent [conformed-path] "..")
(defmethod  lexical-form-from-conformed-path :default  [conformed-path]  (str "default " conformed-path))


(defn conformed-path
  "Takes a jcr path and conforms it to the spec :jcr-spec/jcr-path " [path]
  (let [conformed-path (spec/conform ::jcr-spec/jcr-path  path)]
    (if (= conformed-path :clojure.spec.alpha/invalid)
      (throw (IllegalArgumentException. (format "Illegal jcr-path %s " path ))))
    conformed-path))
(defn lexical-form
"Takes a jcr path   'path' and returns the  lexical form
see
https://docs.adobe.com/content/docs/en/spec/jcr/2.0/3_Repository_Model.html#3.4%20Paths 
https://docs.adobe.com/content/docs/en/spec/jcr/2.0/3_Repository_Model.html#3.4.3%20Lexical Forms" 
  [path]
  (as-> path x
( conformed-path x)
( lexical-form-from-conformed-path x)))




(defn transform-fn-factory [id t-fn ]
(fn [& children]
(cons id (t-fn children))))

(defn flatten-filter-keywords-str
  ""
  [& children]
  (as-> children x
(flatten x)
(filter (comp not keyword?) x)
(apply str x)
(vector x)))

#_(defn flatten-filter-keywords-str [& children]
  (as-> children x
(flatten x)
(filter (comp not keyword?) x)
(apply str x)))
#_(defn flatten-filter-keywords-vector [& children]
  (as-> children x
(flatten x)
(filter (comp not keyword?) x)
(vector x)))

(def tag-map {
              :Identifier flatten-filter-keywords-str
              :LocalName flatten-filter-keywords-str
              :Prefix    flatten-filter-keywords-str
              :Index     flatten-filter-keywords-str
              :URI       flatten-filter-keywords-str

            })
(def tag-map2 
  (as-> tag-map x
(map ( fn [ [ id t-fn]] (vector id (transform-fn-factory id t-fn)))x)
  (into {} x)))

(defn  jcr-path 
  "Takes a jcr-path in lexical form and returns the corresponding abtract jcr path 
  Throws a parse error when lexical-path does not match grammar https://docs.adobe.com/content/docs/en/spec/jcr/2.0/3_Repository_Model.html#3.4.3.3%20Lexical%20Path%20Grammar. "

  
  [lexical-path transform?]
  (as-> lexical-path  x (ccr.core.path/lexical-path-parser x)
(if transform? (insta/transform tag-map2 x) x)))

(defn absolute-path? [lexical-form]
  (= (first (jcr-path lexical-form)) "/" ))
(defn absolute-path [jcr-path]
  nil)
