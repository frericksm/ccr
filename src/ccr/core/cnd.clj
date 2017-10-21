(ns ccr.core.cnd
  (:require [clojure.java.io :as io]
            [ccr.core.transaction-utils :as tu]
            [datomic.api :as d  :only [q db]]
            [instaparse.core :as insta]
            [net.cgrand.enlive-html :as html]))

(def cnd-parser
  "A function with one parameter of type String. Assumes that the string is in cnd format. Parses the string and returns the syntax tree in :enlive format"
    (insta/parser (slurp (io/resource "cnd.ebnf")) :output-format :enlive))

(defn ^:private exists?
  "Returns true, if the selector applied to the node returns an empty list"
  [node selector]
  (not (empty? (html/select node  selector))))

(defn ^:private remove-nil-vals
  "Returns a property entity map where the seq to key :jcr.property/values 
  a) is cleaned from empty values and
  b) to each element in that seq a value for the key :jcr.value/position is added"
  [m]
  (update-in m [:jcr.property/values]
             (fn [val-seq]
               (as-> val-seq x
                     (filter #(not (nil? (get % (first (keys %))))) x)
                     (map-indexed (fn [i v]
                                    (assoc v :jcr.value/position i)) x)
                     (into [] x)))))

(defn ^:private childnode-definition-properties
  "Returns a datomic transaction for a childnode definition. The parameter 'node' "
  [node]
  (let [node_name      (first (html/select node [:node_name html/text-node])) 
        required_types (html/select node [:required_types :string html/text-node]) 
        default_type   (->> (html/select node [:default_type html/text-node])
                            (filter (comp not nil?))
                            (first)) 
        autocreated    (exists? node [:node_attribute :autocreated]) 
        mandatory      (exists? node [:node_attribute :mandatory])
        protected      (exists? node [:node_attribute :protected])
        opv            (->> (html/select node [:node_attribute :opv html/text-node])
                            (filter (comp not nil?))
                            (first))
        sns            (exists? node [:node_attribute :sns])]
    (as-> [{:jcr.property/name "jcr:primaryType"
           :jcr.property/value-attr :jcr.value/name
           :jcr.property/values [{:jcr.value/name "nt:childNodeDefinition"}]}
          {:jcr.property/name "jcr:name"
           :jcr.property/value-attr :jcr.value/name
           :jcr.property/values [{:jcr.value/name node_name}]}
          {:jcr.property/name "jcr:autoCreated"
           :jcr.property/value-attr :jcr.value/boolean
           :jcr.property/values [{:jcr.value/boolean autocreated}]}
          {:jcr.property/name "jcr:mandatory"
           :jcr.property/value-attr :jcr.value/boolean
           :jcr.property/values [{:jcr.value/boolean mandatory}]}
          {:jcr.property/name "jcr:onParentVersion"
           :jcr.property/value-attr :jcr.value/string
           :jcr.property/values [{:jcr.value/string opv}]}
          {:jcr.property/name "jcr:protected"
           :jcr.property/value-attr :jcr.value/boolean
           :jcr.property/values [{:jcr.value/boolean protected}]}
          {:jcr.property/name "jcr:requiredPrimaryTypes"
           :jcr.property/value-attr :jcr.value/name
           :jcr.property/values (->> required_types
                                     (map (fn [v]
                                            {:jcr.value/name v}))
                                     (into []))}
          {:jcr.property/name "jcr:defaultPrimaryType"
           :jcr.property/value-attr :jcr.value/name
           :jcr.property/values [{:jcr.value/name default_type}]}
          {:jcr.property/name "jcr:sameNameSiblings"
           :jcr.property/value-attr :jcr.value/boolean
           :jcr.property/values [{:jcr.value/boolean sns}]}] x
           (map remove-nil-vals x)
           (into [] x))))

(defn ^:private childnode-definition
  ""
  [node]
  {:jcr.node/name "jcr:childNodeDefinition"
   :jcr.node/properties (childnode-definition-properties node)})

 
(def property-type-map
  "Map of parser tag to JCR property type"
  {:type_string         "String"
   :type_binary         "Binary"
   :type_long           "Long"
   :type_double         "Double"
   :type_boolean        "Boolean"
   :type_date           "Date"
   :type_name           "Name"
   :type_path           "Path"
   :type_weak_reference "Weakreference"
   :type_reference      "Reference"
   :type_undefined      "Undefined"})

(defn ^:private property-definition-properties  [node]
  (let [property_name (first (html/select node [:property_name html/text-node]))
        prop_type     (->> (html/select node [:property_type html/first-child])
                           (map :tag)
                           (map property-type-map)
                           first)
        value_constraints (html/select node [:value_constraints
                                             :string
                                             html/text-node])
        default_values    (html/select node [:default_values
                                             :string
                                             html/text-node])
        autocreated (exists? node [:property_attribute :autocreated])
        mandatory   (exists? node [:property_attribute :mandatory])
        protected   (exists? node [:property_attribute :protected])
        opv         (->> (html/select node [:property_attribute :opv
                                            html/text-node])
                         (filter (comp not nil?))
                         (first))
        multiple    (exists? node [:property_attribute :multiple])
        query_ops   (html/select node [:property_attribute :query_ops
                                       :operator html/text-node])
        full_text   (not (exists? node [:property_attribute :no_full_text]))
        query_orderable (not (exists? node [:property_attribute :no_query_order]))]
    (as-> [{:jcr.property/name "jcr:primaryType"
            :jcr.property/value-attr :jcr.value/name
            :jcr.property/values [{:jcr.value/name "nt:propertyDefinition"}]}
           {:jcr.property/name "jcr:name"
            :jcr.property/value-attr :jcr.value/name
            :jcr.property/values [{:jcr.value/name  property_name}]}
           {:jcr.property/name "jcr:autoCreated"
            :jcr.property/value-attr :jcr.value/boolean
            :jcr.property/values [{:jcr.value/boolean autocreated}]}
           {:jcr.property/name "jcr:mandatory"
            :jcr.property/value-attr :jcr.value/boolean
            :jcr.property/values [{:jcr.value/boolean mandatory}]}
           {:jcr.property/name "jcr:onParentVersion"
            :jcr.property/value-attr :jcr.value/string
            :jcr.property/values [{:jcr.value/string opv}]}
           {:jcr.property/name "jcr:protected"
            :jcr.property/value-attr :jcr.value/boolean
            :jcr.property/values [{:jcr.value/boolean protected}]}
           {:jcr.property/name "jcr:requiredType"
            :jcr.property/value-attr :jcr.value/string
            :jcr.property/values [{:jcr.value/string prop_type}]}
           {:jcr.property/name "jcr:valueConstraints"
            :jcr.property/value-attr :jcr.value/string
            :jcr.property/values (->> value_constraints
                                      (map (fn [v]
                                             {:jcr.value/string v}))
                                      (into []))}
           {:jcr.property/name "jcr:defaultValues"
            :jcr.property/value-attr :jcr.value/string
            :jcr.property/values (->> default_values
                                      (map (fn [v]
                                             {:jcr.value/string v}))
                                      (into []))}
           {:jcr.property/name "jcr:multiple"
            :jcr.property/value-attr :jcr.value/boolean
            :jcr.property/values [{:jcr.value/boolean multiple}]}
           {:jcr.property/name "jcr:availableQueryOperators"
            :jcr.property/value-attr :jcr.value/name
            :jcr.property/values (->> query_ops
                                      (map (fn [v]
                                             {:jcr.value/name v}))
                                      (into []))}
           {:jcr.property/name "jcr:isFullTextSearchable"
            :jcr.property/value-attr :jcr.value/boolean
            :jcr.property/values [{:jcr.value/boolean full_text}]}
           {:jcr.property/name "jcr:isQueryOrderable"
            :jcr.property/value-attr :jcr.value/boolean
            :jcr.property/values [{:jcr.value/boolean query_orderable}]}] x
            (map remove-nil-vals x)
            (into [] x))))

(defn property-definition  [node]
  [{:jcr.node/name "jcr:propertyDefinition"
    :jcr.node/properties (property-definition-properties node)}])

(defn nodetype-properties [node]
  (let [nt_name         (first (html/select node #{[:node_type_name
                                                    html/text-node]}))
        abstract        (exists? node [:node_type_attribute :abstract])
        mixin           (exists? node [:node_type_attribute :mixin])
        queryable       (exists? node [:node_type_attribute :query])
        primaryitem     (->> (html/select node #{[:node_type_attribute
                                                  :primary_item
                                                  :string html/text-node]})
                             (filter #(not (nil? %)))
                             (first))
        orderable       (exists? node [:node_type_attribute :orderable])
        supertypes      (html/select node [:supertypes
                                           :string
                                           html/text-node])]
    (as-> [{:jcr.property/name "jcr:primaryType"
           :jcr.property/value-attr :jcr.value/name
           :jcr.property/values [{:jcr.value/name "nt:nodeType"}]}
          {:jcr.property/name "jcr:nodeTypeName"
           :jcr.property/value-attr :jcr.value/name
           :jcr.property/values [{:jcr.value/name nt_name}]}
          {:jcr.property/name "jcr:supertypes"
           :jcr.property/value-attr :jcr.value/name
           :jcr.property/values (->> supertypes
                                     (map (fn [v]
                                            {:jcr.value/name v}))
                                     (into []))}
          {:jcr.property/name "jcr:isAbstract"
           :jcr.property/value-attr :jcr.value/boolean
           :jcr.property/values [{:jcr.value/boolean abstract}]}
          {:jcr.property/name "jcr:isQueryable"
           :jcr.property/value-attr :jcr.value/boolean
           :jcr.property/values [{:jcr.value/boolean queryable}]}
          {:jcr.property/name "jcr:isMixin"
           :jcr.property/value-attr :jcr.value/boolean
           :jcr.property/values [{:jcr.value/boolean mixin}]}
          {:jcr.property/name "jcr:hasOrderableChildNodes"
           :jcr.property/value-attr :jcr.value/boolean
           :jcr.property/values [{:jcr.value/boolean orderable}]}
          {:jcr.property/name "jcr:primaryItemName"
           :jcr.property/value-attr :jcr.value/name
           :jcr.property/values [{:jcr.value/name primaryitem}]}] x
           (map remove-nil-vals x)
           (into [] x))))

(defn nodetype
  "Returns a nested map representing a nodetype "
  [node]
  (let [nt_name         (first (html/select node #{[:node_type_name
                                                    html/text-node]}))
        property_defs   (as-> node x
                              (html/select x [:property_def])
                              (map property-definition x)
                              (apply concat x))
        childnode_defs (as-> node x
                              (html/select x [:child_node_def])
                              (map childnode-definition x))
        nodetype_properties (nodetype-properties node)]
    {:jcr.node/name nt_name
     :jcr.node/children (vec (concat property_defs  childnode_defs)) 
     :jcr.node/properties nodetype_properties}))

(defn node-to-tx
  "Returns a datomic transaction generated from the 'node'. A 'node' is the result of a call of the function parse-cnd-resource.
  This transaction adds all nodetypes defined in 'node' as jcr content."
  [node]
  (as-> node x
        (html/select x [:node_type_def])
        (map nodetype x)
        (vec x)
        (tu/translate-value x)
        (second x)
        ))

(defn ^:private parse-cnd-resource 
  "Returns the syntax tree of the parsed cnd file"
  [resource_on_classpath]
  (as-> resource_on_classpath x
        (io/resource x)
        (slurp x)
        (cnd-parser x)))

(defn builtin-nodetypes
  "Returns the syntax tree of the parsed classpath resource 'jcr2-nodetypes.cnd'"
  []
  (parse-cnd-resource "jcr2-nodetypes.cnd"))
