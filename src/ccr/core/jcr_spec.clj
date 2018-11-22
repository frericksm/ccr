(ns ccr.core.jcr-spec
"This namespace contains clojure specs for jcr concepts 
such as 
jcr name see https://docs.adobe.com/docs/en/spec/jcr/2.0/3_Repository_Model.html#3.2%20Names
jcr path  see https://docs.adobe.com/content/docs/en/spec/jcr/2.0/3_Repository_Model.html#3.4%20Paths

Hence a jcr name is a seq which comforms to ::jcr-name and 
 a jcr path is a seq whichs conforms to ::path 

"
  (:require
            [clojure.spec.alpha :as s]))

(s/def ::index (s/and int? #(> % 0)))
(s/def ::jcr-name (s/cat :ns ::namespace :local-name ::local-name) )
(s/def ::namespace (s/or :empty ::empty-string :uri ::uri))
(s/def ::empty-string (s/and string? #(= % "")))
(s/def ::uri (s/and string? #(not (nil? (java.net.URI. %)) )))
(s/def ::local-name string?) 


(s/def ::jcr-path (s/alt  :abs-path ::abs-path 
                      :rel-path ::rel-path))
(s/def ::abs-path (s/+ ::abs-path-segment))
(s/def ::rel-path (s/+ ::rel-path-segment))
(s/def ::abs-path-segment (s/or :path (s/cat :root ::root-segment :path (s/* ::rel-path-segment)) :identifier  ::identifier-segment))
(s/def ::rel-path-segment (s/or :parent ::parent-segment :self ::self-segment :name-seg ::name-segment))
(s/def ::name-segment (s/cat :name ::jcr-name :index ::index) )

(s/def ::root-segment (s/and string? #(= "/" %)))
(s/def ::parent-segment (s/and string? #(= ".." %)))
(s/def ::self-segment (s/and string? #(= "." %)))

(defn identifier-path-segment? [value]
  (try  (not (nil? (java.util.UUID/fromString value)))
        (catch IllegalArgumentException e false)))
(s/def ::identifier-segment (s/and string? identifier-path-segment?))
