(ns user
  "Tools for interactive development with the REPL. This file should
  not be included in a production build of the application."
  (:require
   [ccr.api.repository :as r]
   [ccr.api.session :as s]
   [ccr.core.cnd :as cnd]
   [ccr.core.import :as import]
   [ccr.core.nodetype :as nt]
   [ccr.core.repository :as repository]
   [ccr.core.session :as session]
   [ccr.core.system :as system]
   [ccr.core.tree :as tree]
   [clojure.data.zip :as z]
   [clojure.data.zip.xml :as zx]
   [clojure.java.io :as io]
   [clojure.java.javadoc :refer (javadoc)]
   [clojure.pprint :refer (pprint)]
   [clojure.reflect :refer (reflect)]
   [clojure.repl :refer (apropos dir doc find-doc pst source)]
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.test :as test]
   [clojure.tools.namespace.repl :refer (refresh refresh-all)]
   [clojure.zip :as zip]
   [datomic.api :as d  :only [q db]]
   [net.cgrand.enlive-html :as html]
   ))

(def system
  "A Var containing an object representing the application under
  development."
  nil)

(defn init
  "Constructs the current development system."
  []
  (alter-var-root #'system
    (constantly (system/system))))

(defn start
  "Starts the current development system."
  []
  (alter-var-root #'system system/start))

(defn stop
  "Shuts down and destroys the current development system."
  []
  (alter-var-root #'system
    (fn [s] (when s (system/stop s)))))

(defn go
  "Initializes the current development system and starts it running."
  []
  (init)
  (start))

(defn reset
  "Stops the system, reloads modified source files, and restarts it."
  []
  (stop)
  (refresh :after 'user/go))

(init)


