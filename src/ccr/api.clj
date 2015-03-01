(ns ccr.api
  (:require [ccr.core.repository]))

(defn repository
  "Attempts to establish a connection to a repository using the given parameters."
  [parameters]
  (ccr.core.repository/repository parameters))

