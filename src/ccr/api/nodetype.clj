(ns ccr.api.nodetype)

(defprotocol NodeType

  (can-add-child-node?
    [this childNodeName]
    [this childNodeName nodeTypeName]
    "Returns true if this node type allows the addition of a child node called childNodeName without specific node type information (that is, given the definition of this parent node type, the child node name is sufficient to determine the intended child node type). Returns false otherwise.")

  (can-remove-node? [this nodeName]
    "Returns true if removing the child node called nodeName is allowed by this node type. Returns false otherwise.")

  (can-remove-property? [this propertyName]
    "Returns true if removing the property called propertyName is allowed by this node type. Returns false otherwise.")

  (can-set-property? [this propertyName & values]
    "Returns true if setting propertyName to values is allowed by this node type. Otherwise returns false.")
  
  (child-node-definitions [this]
    "Returns an array containing the child node definitions of this node type. This includes both those child node definitions actually declared in this node type and those inherited from the supertypes of this node type.")

  (declared-subtypes [this]
    "Returns the direct subtypes of this node type in the node type inheritance hierarchy, that is, those which actually declared this node type in their list of supertypes.")

  (declared-supertypes [this]
    "Returns the direct supertypes of this node type in the node type inheritance hierarchy, that is, those actually declared in this node type. In single-inheritance systems, this will always be an array of size 0 or 1. In systems that support multiple inheritance of node types this array may be of size greater than 1.")

  (property-definitions [this]
    "Returns an array containing the property definitions of this node type. This includes both those property definitions actually declared in this node type and those inherited from the supertypes of this type.")

  (subtypes [this]
    "Returns all subtypes of this node type in the node type inheritance hierarchy.")

  (supertypes [this]
    "Returns all supertypes of this node type in the node type inheritance hierarchy. For primary types apart from nt:base, this list will always include at least nt:base. For mixin types, there is no required supertype.")

  (node-type? [this nodeTypeName]
    "Returns true if the name of this node type or any of its direct or indirect supertypes is equal to nodeTypeName, otherwise returns false.")
  )

(defprotocol NodeTypeDefinition
  (node-type-name [this]
    "Returns the name of the node type.")

  (declared-supertype-names [this]
    "Returns the names of the supertypes actually declared in this node type. In implementations that support node type registration, if this NodeTypeDefinition object is actually a newly-created empty NodeTypeTemplate, then this method will return an empty array.")

  (abstract? [this]
    "Returns true if this is an abstract node type; returns false otherwise. An abstract node type is one that cannot be assigned as the primary or mixin type of a node but can be used in the definitions of other node types as a superclass. In implementations that support node type registration, if this NodeTypeDefinition object is actually a newly-created empty NodeTypeTemplate, then this method will return false.")
  
  (mixin? [this]
    "Returns true if this is a mixin type; returns false if it is primary. In implementations that support node type registration, if this NodeTypeDefinition object is actually a newly-created empty NodeTypeTemplate, then this method will return false.")

  (orderable-child-nodes? [this]
    "Returns true if nodes of this type must support orderable child nodes; returns false otherwise. If a node type returns true on a call to this method, then all nodes of that node type must support the method Node.orderBefore. If a node type returns false on a call to this method, then nodes of that node type may support Node.orderBefore. Only the primary node type of a node controls that node's status in this regard. This setting on a mixin node type will not have any effect on the node. In implementations that support node type registration, if this NodeTypeDefinition object is actually a newly-created empty NodeTypeTemplate, then this method will return false.")

  (primary-item-name [this]
    "Returns the name of the primary item (one of the child items of the nodes of this node type). If this node has no primary item, then this method returns null. This indicator is used by the method Node.getPrimaryItem(). In implementations that support node type registration, if this NodeTypeDefinition object is actually a newly-created empty NodeTypeTemplate, then this method will return null.")


  (declared-property-definitions [this]
    "Returns an array containing the property definitions actually declared in this node type. In implementations that support node type registration, if this NodeTypeDefinition object is actually a newly-created empty NodeTypeTemplate, then this method will return null. ")

  (declared-child-node-definitions [this]
    "Returns an array containing the child node definitions actually declared in this node type. In implementations that support node type registration, if this NodeTypeDefinition object is actually a newly-created empty NodeTypeTemplate, then this method will return null.")
  
  )

(defprotocol ItemDefinition
  (declaring-node-type [this]
    "Gets the node type that contains the declaration of this ItemDefinition. In implementations that support node type registration an ItemDefinition object may be acquired (in the form of a NodeDefinitionTemplate or PropertyDefinitionTemplate) that is not attached to a live NodeType. In such cases this method returns null.")

  (child-item-name [this]
    "Gets the name of the child item. If "*", this ItemDefinition defines a residual set of child items. That is, it defines the characteristics of all those child items with names apart from the names explicitly used in other child item definitions. In implementations that support node type registration, if this ItemDefinition object is actually a newly-created empty PropertyDefinitionTemplate or NodeDefinitionTemplate, then this method will return null.")

  (on-parent-version [this]
    "Gets the OnParentVersion status of the child item. This governs what occurs (in implementations that support versioning) when the parent node of this item is checked-in. One of:

OnParentVersionAction.COPY
OnParentVersionAction.VERSION
OnParentVersionAction.IGNORE
OnParentVersionAction.INITIALIZE
OnParentVersionAction.COMPUTE
OnParentVersionAction.ABORT

In implementations that support node type registration, if this ItemDefinition object is actually a newly-created empty PropertyDefinitionTemplate or NodeDefinitionTemplate, then this method will return OnParentVersionAction.COPY.")

  (auto-created? [this]
    "Reports whether the item is to be automatically created when its parent node is created. If true, then this ItemDefinition will necessarily not be a residual set definition but will specify an actual item name (in other words getName() will not return "*").
An autocreated non-protected item must be created immediately when its parent node is created in the transient session space. Creation of autocreated non-protected items is never delayed until save.

An autocreated protected item should be created immediately when its parent node is created in the transient session space. Creation of autocreated protected items should not be delayed until save, though doing so does not violate JCR compliance.

In implementations that support node type registration, if this ItemDefinition object is actually a newly-created empty PropertyDefinitionTemplate or NodeDefinitionTemplate, then this method will return false.")

  (mandatory? [this]
    "Reports whether the item is mandatory. A mandatory item is one that, if its parent node exists, must also exist.
This means that a mandatory single-value property must have a value (since there is no such thing a null value). In the case of multi-value properties this means that the property must exist, though it can have zero or more values.

An attempt to save a node that has a mandatory child item without first creating that child item will throw a ConstraintViolationException on save.

In implementations that support node type registration, if this ItemDefinition object is actually a newly-created empty PropertyDefinitionTemplate or NodeDefinitionTemplate, then this method will return false.

An item definition cannot be both residual and mandatory.")

  (protected? [this]
    "Reports whether the child item is protected. In level 2 implementations, a protected item is one that cannot be removed (except by removing its parent) or modified through the the standard write methods of this API (that is, Item.remove, Node.addNode, Node.setProperty and Property.setValue).
A protected node may be removed or modified (in a level 2 implementation), however, through some mechanism not defined by this specification or as a side-effect of operations other than the standard write methods of the API.

In implementations that support node type registration, if this ItemDefinition object is actually a newly-created empty PropertyDefinitionTemplate or NodeDefinitionTemplate, then this method will return false.")
  )

(defprotocol PropertyDefinition
  (available-query-operators [this]
    "Returns the set of query comparison operators supported by this property.
This attribute only takes effect if the node type holding the property definition has a queryable setting of true.

JCR defines the following comparison operators:

QueryObjectModelConstants.JCR_OPERATOR_EQUAL_TO,
QueryObjectModelConstants.JCR_OPERATOR_NOT_EQUAL_TO,
QueryObjectModelConstants.JCR_OPERATOR_LESS_THAN,
QueryObjectModelConstants.JCR_OPERATOR_LESS_THAN_OR_EQUAL_TO,
QueryObjectModelConstants.JCR_OPERATOR_GREATER_THAN,
QueryObjectModelConstants.JCR_OPERATOR_GREATER_THAN_OR_EQUAL_TO, or
QueryObjectModelConstants.JCR_OPERATOR_LIKE
An implementation may define additional comparison operators.
Note that the set of operators that can appear in this attribute may be limited by implementation-specific constraints that differ across property types. For example, some implementations may permit property definitions to provide JCR_OPERATOR_EQUAL_TO and JCR_OPERATOR_NOT_EQUAL_TO as available operators for BINARY properties while others may not.

However, in all cases where a JCR-defined operator is potentially available for a given property type, its behavior must conform to the comparison semantics defined in the specification document (see 3.6.5 Comparison of Values).

In implementations that support node type registration, if this NodeTypeDefinition object is actually a newly-created empty NodeTypeTemplate, then this method will return an impementation- determined default set of operator constants.")

  (default-values [this]
    "Gets the default value(s) of the property. These are the values that the property defined by this PropertyDefinition will be assigned if it is automatically created (that is, if ItemDefinition.isAutoCreated() returns true).
This method returns an array of Value objects. If the property is multi-valued, then this array represents the full set of values that the property will be assigned upon being auto-created. Note that this could be the empty array. If the property is single-valued, then the array returned will be of size 1.

If null is returned, then the property has no fixed default value. This does not exclude the possibility that the property still assumes some value automatically, but that value may be parametrized (for example, "the current date") and hence not expressible as a single fixed value. In particular, this must be the case if isAutoCreated returns true and this method returns null.

Note that to indicate a null value for this attribute in a node type definition that is stored in content, the jcr:defaultValues property is simply removed (since null values for properties are not allowed.

In implementations that support node type registration, if this PropertyDefinition object is actually a newly-created empty PropertyDefinitionTemplate, then this method will return null.")

  (required-type [this]
    "Gets the required type of the property. One of:
PropertyType.STRING
PropertyType.DATE
PropertyType.BINARY
PropertyType.DOUBLE
PropertyType.DECIMAL
PropertyType.LONG
PropertyType.BOOLEAN
PropertyType.NAME
PropertyType.PATH
PropertyType.URI
PropertyType.REFERENCE
PropertyType.WEAKREFERENCE
PropertyType.UNDEFINED
PropertyType.UNDEFINED is returned if this property may be of any type.
In implementations that support node type registration, if this PropertyDefinition object is actually a newly-created empty PropertyDefinitionTemplate, then this method will return PropertyType.STRING.")

  (value-constraints [this]
    "Gets the array of constraint strings.")

  (full-text-searchable? [this]
    "Returns true if this property is full-text searchable, meaning that its value is accessible through the full-text search function within a query.")

  (multiple? [this]
    "Reports whether this property can have multiple values. Note that the isMultiple flag is special in that a given node type may have two property definitions that are identical in every respect except for the their isMultiple status. For example, a node type can specify two string properties both called X, one of which is multi-valued and the other not. An example of such a node type is nt:unstructured.")

  (query-orderable? [this]
    "Returns true if this property is query-orderable, meaning that query results may be ordered by this property using the order-by clause of a query.")
  )

(defprotocol NodeDefinition
  (allows-same-name-siblings? [this]
    "Reports whether this child node can have same-name siblings. In other words, whether the parent node can have more than one child node of this name. If this NodeDefinition is actually a NodeDefinitionTemplate that is not part of a registered node type, then this method will return the same name siblings status as set in that template. If that template is a newly-created empty one, then this method will return false.")

  (default-primary-type [this]
    "Gets the default primary node type that will be assigned to the child node if it is created without an explicitly specified primary node type. This node type must be a subtype of (or the same type as) the node types returned by getRequiredPrimaryTypes. If null is returned this indicates that no default primary type is specified and that therefore an attempt to create this node without specifying a node type will throw a ConstraintViolationException. In implementations that support node type registration an NodeDefinition object may be acquired (in the form of a NodeDefinitionTemplate) that is not attached to a live NodeType. In such cases this method returns null.")

  (default-primary-type-name [this]
    "Returns the name of the default primary node type. If this NodeDefinition is acquired from a live NodeType this list will reflect the NodeType returned by getDefaultPrimaryType, above. If this NodeDefinition is actually a NodeDefinitionTemplate that is not part of a registered node type, then this method will return the required primary types as set in that template. If that template is a newly-created empty one, then this method will return null.")

  (required-primary-types [this]
    "Gets the minimum set of primary node types that the child node must have. Returns an array to support those implementations with multiple inheritance. This method never returns an empty array. If this node definition places no requirements on the primary node type, then this method will return an array containing only the NodeType object representing nt:base, which is the base of all primary node types and therefore constitutes the least restrictive node type requirement. Note that any particular node instance still has only one assigned primary node type, but in multiple-inheritance-supporting implementations the RequiredPrimaryTypes attribute can be used to restrict that assigned node type to be a subtype of all of a specified set of node types. In implementations that support node type registration an NodeDefinition object may be acquired (in the form of a NodeDefinitionTemplate) that is not attached to a live NodeType. In such cases this method returns null.")
  
  (required-primary-type-names [this]
    "Returns the names of the required primary node types. If this NodeDefinition is acquired from a live NodeType this list will reflect the node types returned by getRequiredPrimaryTypes, above. If this NodeDefinition is actually a NodeDefinitionTemplate that is not part of a registered node type, then this method will return the required primary types as set in that template. If that template is a newly-created empty one, then this method will return null.")
  )
