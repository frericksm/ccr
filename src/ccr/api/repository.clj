(ns ccr.api.repository)

(defprotocol Repository
  "The entry point into the content repository."
  
  (login
    [this credentials workspace-name]
    [this] "Authenticates the user using the supplied credentials"))


(defprotocol NodeTypeManager
  "Allows for the retrieval and (in implementations that support it) the registration of node types. Accessed via Workspace.getNodeTypeManager()."
  

  (node-type [this node-type-name]
    "Returns the named node type."))









