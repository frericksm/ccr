(ns ccr.api.session)

(defprotocol Session
  "The Session object provides read and (in level 2) write access to the content of a particular workspace in the repository."
  
  (root-node [this] "Returns the root node of the workspace , \"/\" ")
  
  (workspace [this] "Returns the Workspace attached to this Session.")
  )
