(ns ccr.api.nodetype)

(defprotocol NodeType

  (node-type? [this nodeTypeName]
    "Returns true if the name of this node type or any of its direct or indirect supertypes is equal to nodeTypeName, otherwise returns false.")
  )

(defprotocol NodeTypeDefinition
  (node-type-name [this]
    "Returns the name of the node type.")
  
  )
