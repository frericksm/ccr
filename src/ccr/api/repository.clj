(ns ccr.api.repository)

(defprotocol Repository
  "The entry point into the content repository."
  
  (login
    [this credentials workspace-name]
    [this] "Authenticates the user using the supplied credentials"))












