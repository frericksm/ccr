(ns ccr.cnd
  (:require [instaparse.core :as insta]
            [clojure.java.io :as io]
            [clojure.zip :as zip]
            [clojure.data.zip :as z]
            [clojure.data.zip.xml :as zx]))


;; http://www.day.com/maven/jcr/2.0/25_Appendix.html

(def cnd-parser
  (insta/parser (slurp (io/resource "cnd.ebnf"))
                :output-format :enlive))

(defn builtin-nodetypes-zipper
  "Liefert einen Zipper für den Inhalt einer Datei, die Nodetypes im Format Compact Nodetype Definition (CND) enthält. Wird keine Datei übergeben, dann werden die builtin-Nodetypes geliefert"
  
  ([] (builtin-nodetypes-zipper "jcr2-nodetypes.cnd"))

  ([resource_on_claspath]
     (as-> resource_on_claspath x
           (io/resource x)
           (slurp x)
           (cnd-parser x) 
           (zip/xml-zip x))))

(defn node-definition  [loc]
  (let [node_name (as-> loc x
                   (zx/xml-> x z/descendants :node_name zx/text )
                   (first x))
        required_types (as-> loc x
                             (zx/xml-> x
                                       z/descendants :required_types
                                       z/descendants :string zx/text))
        default_type (as-> loc x
                           (zx/xml-> x z/descendants :default_type zx/text )
                           (first x))
        autocreated (as-> loc x
                          (zx/xml-> x z/descendants :node_attribute
                                    z/descendants :autocreated)
                          (empty? x)
                          (not x))
        mandatory (as-> loc x
                        (zx/xml-> x z/descendants :node_attribute
                                  z/descendants :mandatory)
                        (empty? x)
                        (not x))
        protected (as-> loc x
                        (zx/xml-> x z/descendants :node_attribute
                                  z/descendants :protected)
                        (empty? x)
                        (not x))
        opv       (as-> loc x
                        (zx/xml-> x z/descendants :node_attribute
                                  z/descendants :opv
                                  z/descendants :string zx/text)
                        (first x))
        sns       (as-> loc x
                        (zx/xml-> x z/descendants :node_attribute
                                  z/descendants :sns)
                        (empty? x)
                        (not x))]
    (as-> {:jcr/name node_name} x
          (if (empty? required_types)
            x
            (assoc  x :jcr/required-primary-type required_types))
          (if default_type
            (assoc  x :jcr/default-primary-type default_type)
            x)
          (if autocreated
            (assoc  x :jcr/auto-created true)
            x)
          (if mandatory
            (assoc  x :jcr/mandatory true)
            x)
          (if protected
            (assoc  x :jcr/protected true)
            x)
          (if opv
            (assoc  x :jcr/onParentVersion opv)
            x)
          (if sns
            (assoc  x :jcr/allow-same-name-siblings true)
            x)
          )))

(defn property-definition  [loc]
  (let [property_name (as-> loc x
                   (zx/xml-> x z/descendants :property_name zx/text )
                   (first x))
        prop_type (as-> loc x
                        (zx/xml-> x
                                  z/descendants :property_type z/children)
                        (map zip/node x)
                        (map :tag x)
                        (first x))
        autocreated (as-> loc x
                          (zx/xml-> x z/descendants :property_attribute
                                    z/descendants :autocreated)
                          (empty? x)
                          (not x))
        mandatory (as-> loc x
                        (zx/xml-> x z/descendants :property_attribute
                                  z/descendants :mandatory)
                        (empty? x)
                        (not x))
        protected (as-> loc x
                        (zx/xml-> x z/descendants :property_attribute
                                  z/descendants :protected)
                        (empty? x)
                        (not x))
        opv       (as-> loc x
                        (zx/xml-> x z/descendants :property_attribute
                                  z/descendants :opv zx/text)
                        (first x))
        multiple  (as-> loc x
                        (zx/xml-> x z/descendants :property_attribute
                                  z/descendants :multiple)
                        (empty? x)
                        (not x))]
    (as-> {:jcr/name property_name
           :jcr/type prop_type} x
          (if autocreated
            (assoc  x :jcr/auto-created true)
            x)
          (if mandatory
            (assoc  x :jcr/mandatory true)
            x)
          (if protected
            (assoc  x :jcr/protected true)
            x)
          (if opv
            (assoc  x :jcr/onParentVersion opv)
            x)
          (if multiple
            (assoc  x :jcr/multiple true)
            x)
          )))

(defn nodetype [loc]
  (let [nt_name (as-> loc x
                      (zx/xml-> x z/descendants :node_type_name zx/text )
                      (first x))
        abstract (as-> loc x
                       (zx/xml-> x z/descendants :node_type_attribute
                                 z/descendants :abstract)
                       (empty? x)
                       (not x))
        mixin (as-> loc x
                    (zx/xml-> x z/descendants :node_type_attribute z/descendants :mixin)
                    (empty? x)
                    (not x))  
        queryable  (as-> loc x
                         (zx/xml-> x z/descendants :node_type_attribute z/descendants :query)
                         (empty? x)
                         (not x))
        primaryitem (as-> loc x
                          (zx/xml-> x z/descendants :node_type_attribute z/descendants :primary_item
                                    z/descendants :string zx/text)
                          (first x))
        orderable (as-> loc x
                        (zx/xml-> x z/descendants :node_type_attribute z/descendants :orderable)
                        (empty? x)
                        (not x))
        supertypes (as-> loc x
                         (zx/xml-> x z/descendants :supertypes z/descendants :string zx/text))
        property_defs (as-> loc x
                            (zx/xml-> x z/descendants :property_def )
                            (map property-definition x))
        child_node_defs (as-> loc x
                              (zx/xml-> x z/descendants :child_node_def)
                              (map node-definition x))
        ]
    (as-> {:jcr/nodeTypeName nt_name} x
          (if (empty? supertypes)
            x
            (assoc  x :jcr/supertypes supertypes))
          (if abstract
            (assoc  x :jcr/abstract true)
            x)
          (if orderable
            (assoc  x :jcr/orderableChildNodes true)
            x)
          (if mixin
            (assoc  x :jcr/mixin true)
            x)
          (if primaryitem
            (assoc  x :jcr/primaryItemName primaryitem)
            x)
          (if queryable
            (assoc  x :jcr/queryable true)
            x)
          (if (empty? property_defs)
            x
            (assoc x :jcr/propertyDefinitions property_defs))
          (if (empty? child_node_defs)
            x
            (assoc x :jcr/childnodeDefinitions child_node_defs))
          )))

(defn nodetypes
  "Liefert eine Map die UUIDs auf Datomic Tempids abbildet.
   Dazu werden alle Properties mit dem Namen \"jcr:uuid\" ermittlet. "
  [loc]
  (as-> loc x
        (zx/xml-> x z/descendants :node_type_def)
        (map (fn [l] (nodetype l)) x)))
