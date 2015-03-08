(ns ccr.api.node)

(defprotocol Item
  (ancestor [this depth]
    "Returns the ancestor of this Item at the specified depth.")

  (depth [this]
    "Returns the depth of this Item in the workspace item graph.")

  (item-name [this]
    "Returns the name of this Item in qualified form.")

  (parent [this]
    "Returns the parent of this Item.")

  (path [this]
    "Returns the normalized absolute path to this item.")

  (session [this]
    "Returns the Session through which this Item was acquired.")

  (modified? [this]
    "Returns true if this Item has been saved but has subsequently been modified through the current session and therefore the state of this item as recorded in the session differs from the state of this item as saved.")

  (new? [this]
    "Returns true if this is a new item, meaning that it exists only in transient storage on the Session and has not yet been saved.")

  (node? [this]
    "Indicates whether this Item is a Node or a Property.")

  (same? [this otherItem]
    "Returns true if this Item object (the Java object instance) represents the same actual workspace item as the object otherItem.")
 
  (refresh [this keepChanges]
    "If keepChanges is false, this method discards all pending changes currently recorded in this Session that apply to this Item or any of its descendants (that is, the subgraph rooted at this Item)and returns all items to reflect the current saved state.")
  (remove-item [this]
    "Removes this item (and its subgraph).")
  )

(defprotocol Node

  (add-node [this ^String relPath]
            [this ^String replPath ^String primaryNodeTypeName]
    "1. Creates a new node at relPath
2. Creates a new node at relPath of the specified node type primaryNodeTypeName")

  (definition [this]
    "Returns the node definition that applies to this node.")

  (identifier [this]
    "Returns the identifier of this node.")

  (index [this]
    "This method returns the index of this node within the ordered set of its same-name sibling nodes.")

  (mixinNodeTypes [this]
    "Returns an array of NodeType objects representing the mixin node types in effect for this node.")

  (node [this relPath]
    "Returns the node at relPath relative to this node.")


  (nodes [this]
    [this namePattern]
    "1.) Returns all child nodes of this node accessible through the current
         Session
2.) Gets all child nodes of this node accessible through the current Session that match namePattern.")

  (primary-item [this]
    "Returns the primary child item of this node.")

  (primary-nodetype [this]
    "Returns the primary node type in effect for this node.")
  )

(defprotocol Property  )
