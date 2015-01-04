(ns ccr.cnd
  (:require [instaparse.core :as insta]
            [clojure.java.io :as io]
            [net.cgrand.enlive-html :as html]
            [datomic.api :as d  :only [q db]]))

(defn exists?
  "Returns true, if the selector applied to the node returns an empty list"
  [node selector]
  (not (empty? (html/select node  selector))))

(defn remove-nil-vals
  "Returns a map which is m but with all entries removed where the value for a key is nil"
  [m]
  (select-keys m (filter #(not (nil? (get m %))) (keys m))))

(defn node-definition-properties-tx
  ""
  [node]
  (let [node_name      (first (html/select node [:node_name html/text-node])) 
        required_types (html/select node [:required_types :string html/text-node]) 
        default_type   (->> (html/select node [:default_type html/text-node])
                            (filter (comp not nil?))
                            (first)) 
        autocreated    (exists? node [:node_attribute :autocreated]) 
        mandatory      (exists? node [:node_attribute :mandatory])
        protected      (exists? node [:node_attribute :protected])
        opv            (->> (html/select node [:node_attribute :opv
                                               :string html/text-node])
                            (filter (comp not nil?))
                            (first))
        sns            (exists? node [:node_attribute :sns])]
    (map remove-nil-vals
     [{:db/id (d/tempid ":db.part/user")
       :jcr.property/name "jcr:primaryType"
       :jcr.property/value-attr :jcr.value/name
       :jcr.value/name "nt:childNodeDefinition"}
      {:db/id (d/tempid ":db.part/user")
       :jcr.property/name "jcr:name"
       :jcr.property/value-attr :jcr.value/name
       :jcr.value/name  node_name}
      {:db/id (d/tempid ":db.part/user")
       :jcr.property/name "jcr:autoCreated"
       :jcr.property/value-attr :jcr.value/boolean
       :jcr.value/boolean autocreated}
      {:db/id (d/tempid ":db.part/user")
       :jcr.property/name "jcr:mandatory"
       :jcr.property/value-attr :jcr.value/boolean
       :jcr.value/boolean mandatory}
      {:db/id (d/tempid ":db.part/user")
       :jcr.property/name "jcr:onParentVersion"
       :jcr.property/value-attr :jcr.value/string
       :jcr.value/string opv}
      {:db/id (d/tempid ":db.part/user")
       :jcr.property/name "jcr:protected"
       :jcr.property/value-attr :jcr.value/boolean
       :jcr.value/boolean protected}
      {:db/id (d/tempid ":db.part/user")
       :jcr.property/name "jcr:requiredPrimaryTypes"
       :jcr.property/value-attr :jcr.value/names
       :jcr.value/names (set required_types)}
      {:db/id (d/tempid ":db.part/user")
       :jcr.property/name "jcr:defaultPrimaryType"
       :jcr.property/value-attr :jcr.value/name
       :jcr.value/name default_type}
      {:db/id (d/tempid ":db.part/user")
       :jcr.property/name "jcr:sameNameSiblings"
       :jcr.property/value-attr :jcr.value/boolean
       :jcr.value/boolean sns}])))

(defn node-definition-tx
  ""
  [node]
  (let [node_definition_properties (node-definition-properties-tx node)]
    (concat
     [{:db/id (d/tempid ":db.part/user")
       :jcr.node/name "jcr:childNodeDefinition"
       :jcr.node/properties (->> node_definition_properties (map :db/id))}]
     node_definition_properties)))

 
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

(defn property-definition-properties-tx  [node]
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
                                            :string html/text-node])
                         (filter (comp not nil?))
                         (first))
        multiple    (exists? node [:property_attribute :multiple])
        query_ops   (html/select node [:property_attribute :query_ops
                                       :operator html/text-node])
        full_text   (not (exists? node [:property_attribute :no_full_text]))
        query_orderable (not (exists? node [:property_attribute :no_query_order]))]
    (map remove-nil-vals
     [{:db/id (d/tempid ":db.part/user")
       :jcr.property/name "jcr:primaryType"
       :jcr.property/value-attr :jcr.value/name
       :jcr.value/name "nt:propertyDefinition"}
      {:db/id (d/tempid ":db.part/user")
       :jcr.property/name "jcr:name"
       :jcr.property/value-attr :jcr.value/name
       :jcr.value/name  property_name}
      {:db/id (d/tempid ":db.part/user")
       :jcr.property/name "jcr:autoCreated"
       :jcr.property/value-attr :jcr.value/boolean
       :jcr.value/boolean autocreated}
      {:db/id (d/tempid ":db.part/user")
       :jcr.property/name "jcr:mandatory"
       :jcr.property/value-attr :jcr.value/boolean
       :jcr.value/boolean mandatory}
      {:db/id (d/tempid ":db.part/user")
       :jcr.property/name "jcr:onParentVersion"
       :jcr.property/value-attr :jcr.value/string
       :jcr.value/string opv}
      {:db/id (d/tempid ":db.part/user")
       :jcr.property/name "jcr:protected"
       :jcr.property/value-attr :jcr.value/boolean
       :jcr.value/boolean protected}
      {:db/id (d/tempid ":db.part/user")
       :jcr.property/name "jcr:requiredType"
       :jcr.property/value-attr :jcr.value/string
       :jcr.value/string prop_type}
      {:db/id (d/tempid ":db.part/user")
       :jcr.property/name "jcr:valueConstraints"
       :jcr.property/value-attr :jcr.value/strings
       :jcr.value/strings (set value_constraints)}
      {:db/id (d/tempid ":db.part/user")
       :jcr.property/name "jcr:defaultValues"
       :jcr.property/value-attr :jcr.value/strings
       :jcr.value/strings (set default_values)}
      {:db/id (d/tempid ":db.part/user")
       :jcr.property/name "jcr:multiple"
       :jcr.property/value-attr :jcr.value/boolean
       :jcr.value/boolean multiple}
      {:db/id (d/tempid ":db.part/user")
       :jcr.property/name "jcr:availableQueryOperators"
       :jcr.property/value-attr :jcr.value/names
       :jcr.value/names (set query_ops)}
      {:db/id (d/tempid ":db.part/user")
       :jcr.property/name "jcr:isFullTextSearchable"
       :jcr.property/value-attr :jcr.value/boolean
       :jcr.value/boolean full_text}
      {:db/id (d/tempid ":db.part/user")
       :jcr.property/name "jcr:isQueryOrderable"
       :jcr.property/value-attr :jcr.value/boolean
       :jcr.value/boolean query_orderable}])))

(defn property-definition-tx  [node]
  (let [property_definition_properties (property-definition-properties-tx node)]
    (concat
     [{:db/id (d/tempid ":db.part/user")
       :jcr.node/name "jcr:propertyDefinition"
       :jcr.node/properties (map :db/id property_definition_properties)}]
     property_definition_properties)))

(defn nodetype-properties-tx [node]
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
        supertypes      (html/select node  #{[:supertypes :string html/text-node]})]
    (map remove-nil-vals
     [{:db/id (d/tempid ":db.part/user")
       :jcr.property/name "jcr:primaryType"
       :jcr.property/value-attr :jcr.value/name
       :jcr.value/name "nt:nodeType"}
      {:db/id (d/tempid ":db.part/user")
       :jcr.property/name "jcr:nodeTypeName"
       :jcr.property/value-attr :jcr.value/name
       :jcr.value/name nt_name}
      {:db/id (d/tempid ":db.part/user")
       :jcr.property/name "jcr:supertypes"
       :jcr.property/value-attr :jcr.value/names
       :jcr.value/names (set supertypes)}
      {:db/id (d/tempid ":db.part/user")
       :jcr.property/name "jcr:isAbstract"
       :jcr.property/value-attr :jcr.value/boolean
       :jcr.value/boolean abstract}
      {:db/id (d/tempid ":db.part/user")
       :jcr.property/name "jcr:isQueryable"
       :jcr.property/value-attr :jcr.value/boolean
       :jcr.value/boolean queryable}
      {:db/id (d/tempid ":db.part/user")
       :jcr.property/name "jcr:isMixin"
       :jcr.property/value-attr :jcr.value/boolean
       :jcr.value/boolean mixin}
      {:db/id (d/tempid ":db.part/user")
       :jcr.property/name "jcr:hasOrderableChildNodes"
       :jcr.property/value-attr :jcr.value/boolean
       :jcr.value/boolean orderable}
      {:db/id (d/tempid ":db.part/user")
       :jcr.property/name "jcr:primaryItemName"
       :jcr.property/value-attr :jcr.value/name
       :jcr.value/name primaryitem}])))

(defn nodetype-tx [node]
  (let [nt_name         (first (html/select node #{[:node_type_name
                                                    html/text-node]}))
        property_defs   (as-> node x
                              (html/select x [:property_def])
                              (map property-definition-tx x)
                              (apply concat x))
        child_node_defs (as-> node x
                              (html/select x [:child_node_def])
                              (map node-definition-tx x)
                              (apply concat x))
        nodetype_properties (nodetype-properties-tx node)]
    (concat
     [{:db/id (d/tempid ":db.part/user")
       :jcr.node/name nt_name
       :jcr.node/children (->> (concat property_defs child_node_defs) (map :db/id))
       :jcr.node/properties (map :db/id nodetype_properties)}]
     property_defs
     child_node_defs
     nodetype_properties)))

(defn node-to-tx
  "Returns a datomic transaction of the syntax tree 'node' generated by the function 'parse-cnd-resource'. This transaction loads all nodetypes defined in 'node' as jcr content. That is entities consisting of the node attributes defined in 'jcr.dtm'"
  [node]
  (as-> node x
        (html/select x [:node_type_def])
        (map nodetype-tx x)
        (apply concat x)))

(def cnd-parser
  "A function with one parameter of type String. Assumes that the string is in cnd format. Parses the string and returns the syntax tree in :enlive format"
    (insta/parser (slurp (io/resource "cnd.ebnf")) :output-format :enlive))

(defn parse-cnd-resource 
  "Returns the syntax tree of the parsed cnd file"
  [resource_on_claspath]
  (as-> resource_on_claspath x
        (io/resource x)
        (slurp x)
        (cnd-parser x)))

(defn builtin-nodetypes
  "Returns the syntax tree of the parsed classpath resource 'jcr2-nodetypes.cnd'"
  []
  (parse-cnd-resource "jcr2-nodetypes.cnd"))
