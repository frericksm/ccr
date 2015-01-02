(ns ccr.cnd
  (:require [instaparse.core :as insta]
            [clojure.java.io :as io]
            [clojure.zip :as zip]
            [clojure.data.zip :as z]
            [clojure.data.zip.xml :as zx]
            [net.cgrand.enlive-html :as html]))


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
           (cnd-parser x))))

(defn exists? [node selector]
  (not (empty? (html/select node  selector))))

(defn node-definition  [node]
  (let [node_name      (first (html/select node [:node_name html/text-node])) 
        required_types (html/select node [:required_types :string html/text-node]) 
        default_type   (->> (html/select node [:default_type html/text-node])
                            (filter (comp not nil?))) 
        autocreated    (exists? node [:node_attribute :autocreated]) 
        mandatory      (exists? node [:node_attribute :mandatory])
        protected      (exists? node [:node_attribute :protected])
        opv            (->> (html/select node [:node_attribute :opv
                                               :string html/text-node])
                            (filter (comp not nil?)))
        sns            (exists? node [:node_attribute :sns])]
    [{:jcr.property/name "jcr:name"
      :jcr.property/type "Name"
      :jcr.property/values #{node_name}}
     {:jcr.property/name "jcr:autoCreated"
      :jcr.property/type "Boolean"
      :jcr.property/values #{autocreated}}
     {:jcr.property/name "jcr:mandatory"
      :jcr.property/type "Boolean"
      :jcr.property/values #{mandatory}}
     {:jcr.property/name "jcr:onParentVersion"
      :jcr.property/type "String"
      :jcr.property/values (set opv)}
     {:jcr.property/name "jcr:protected"
      :jcr.property/type "Boolean"
      :jcr.property/values #{protected}}
     {:jcr.property/name "jcr:requiredPrimaryTypes"
      :jcr.property/type "Name"
      :jcr.property/values (set required_types)}
     {:jcr.property/name "jcr:defaultPrimaryType"
      :jcr.property/type "Name"
      :jcr.property/values (set default_type)}
     {:jcr.property/name "jcr:sameNameSiblings"
      :jcr.property/type "Name"
      :jcr.property/values #{sns}}]))

(defn property-definition  [node]
  (let [property_name (first (html/select node [:property_name html/text-node]))
        prop_type     (->> (html/select node [:property_type html/first-child])
                           (map :tag)
                           first)
        value_constraints (html/select node [:property_type
                                             :value_constraints
                                             :string
                                             html/text-node])
        default_values    (html/select node [:property_type
                                             :default_values
                                             :string
                                             html/text-node])
        autocreated (exists? node [:property_attribute :autocreated])
        mandatory   (exists? node [:property_attribute :mandatory])
        protected   (exists? node [:property_attribute :protected])
        opv         (->> (html/select node [:property_attribute :opv
                                            :string html/text-node])
                         (filter (comp not nil?)))
        multiple    (exists? node [:property_attribute :multiple])
        query_ops   (html/select node [:property_attribute :query_ops
                                       :operator html/text-node])
        full_text   (not (exists? node [:property_attribute :no_full_text]))
        query_orderable (not (exists? node [:property_attribute :no_query_order]))]
    [{:jcr.property/name "jcr:name"
      :jcr.property/type "Name"
      :jcr.property/values #{property_name}}
     {:jcr.property/name "jcr:autoCreated"
      :jcr.property/type "Boolean"
      :jcr.property/values #{autocreated}}
     {:jcr.property/name "jcr:mandatory"
      :jcr.property/type "Boolean"
      :jcr.property/values #{mandatory}}
     {:jcr.property/name "jcr:onParentVersion"
      :jcr.property/type "String"
      :jcr.property/values (set opv)}
     {:jcr.property/name "jcr:protected"
      :jcr.property/type "Boolean"
      :jcr.property/values #{protected}}
     {:jcr.property/name "jcr:requiredType"
      :jcr.property/type "String"
      :jcr.property/values #{prop_type}}
     {:jcr.property/name "jcr:valueConstraints"
      :jcr.property/type "String"
      :jcr.property/values (set value_constraints)}
     {:jcr.property/name "jcr:defaultValues"
      :jcr.property/type "Undefined"
      :jcr.property/values (set default_values)}
     {:jcr.property/name "jcr:multiple"
      :jcr.property/type "Boolean"
      :jcr.property/values #{multiple}}
     {:jcr.property/name "jcr:availableQueryOperators"
      :jcr.property/type "Name"
      :jcr.property/values (set query_ops)}
     {:jcr.property/name "jcr:isFullTextSearchable"
      :jcr.property/type "Boolean"
      :jcr.property/values #{full_text}}
     {:jcr.property/name "jcr:isQueryOrderable"
      :jcr.property/type "Boolean"
      :jcr.property/values #{query_orderable}}]))

(defn nodetype [node]
  (let [nt_name         (first (html/select node #{[:node_type_name
                                                    html/text-node]}))
        abstract        (exists? node [:node_type_attribute :abstract])
        mixin           (exists? node [:node_type_attribute :mixin])
        queryable       (exists? node [:node_type_attribute :query])
        primaryitem     (->> (html/select node #{[:node_type_attribute
                                                  :primary_item
                                                  :string html/text-node]})
                             (filter #(not (nil? %))))
        orderable       (exists? node [:node_type_attribute :orderable])
        supertypes      (html/select node  #{[:supertypes :string html/text-node]})
        property_defs   (as-> node x
                              (html/select x [:property_def])
                              (map property-definition x))
        child_node_defs (as-> node x
                              (html/select x [:child_node_def])
                              (map node-definition x))
        ]
    {:jcr.node/name nt_name
     :jcr.node/children [property_defs child_node_defs]
     :jcr.node/properties
     [{:jcr.property/name "jcr:nodeTypeName"
       :jcr.property/type "Name"
       :jcr.property/values #{nt_name}
       }
      {:jcr.property/name "jcr:supertypes"
       :jcr.property/type "Name"
       :jcr.property/values (set supertypes)
       }
      {:jcr.property/name "jcr:isAbstract"
       :jcr.property/type "Boolean"
       :jcr.property/values (set [abstract])
       }
      {:jcr.property/name "jcr:isQueryable"
       :jcr.property/type "Boolean"
       :jcr.property/values (set [queryable])
       }
      {:jcr.property/name "jcr:isMixin"
       :jcr.property/type "Boolean"
       :jcr.property/values (set [mixin])
       }
      {:jcr.property/name "jcr:hasOrderableChildNodes"
       :jcr.property/type "Boolean"
       :jcr.property/values (set [orderable])
       }
      {:jcr.property/name "jcr:primaryItemName"
       :jcr.property/type "Name"
       :jcr.property/values (set primaryitem)
       }]}))

(defn nodetypes
  "Liefert eine Map die UUIDs auf Datomic Tempids abbildet.
   Dazu werden alle Properties mit dem Namen \"jcr:uuid\" ermittlet. "
  [node]
  (as-> node x
        (html/select x [:node_type_def])
        (map nodetype x)))
