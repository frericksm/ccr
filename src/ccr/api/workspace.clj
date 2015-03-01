(ns ccr.api.workspace)

(defprotocol Workspace
  "A Workspace object represents a view onto a persitent workspace within a repository. This view is defined by the authorization settings of the Session object associated with the Workspace object. Each Workspace object is associated one-to-one with a Session object."

  (node-type-manager [this]
    "Returns the NodeTypeManager through which node type information can be queried. There is one node type registry per repository, therefore the NodeTypeManager is not workspace-specific; it provides introspection methods for the global, repository-wide set of available node types. In repositories that support it, the NodeTypeManager can also be used to register new node types.")
  )
