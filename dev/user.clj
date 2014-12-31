(ns user
  "Tools for interactive development with the REPL. This file should
  not be included in a production build of the application."
  (:require
   [clojure.java.io :as io]
   [clojure.java.javadoc :refer (javadoc)]
   [clojure.pprint :refer (pprint)]
   [clojure.reflect :refer (reflect)]
   [clojure.repl :refer (apropos dir doc find-doc pst source)]
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.test :as test]
   [clojure.tools.namespace.repl :refer (refresh refresh-all)]
   [ccr.system :as system]
   [ccr.import :as import]
   [ccr.repository :as repository]
   [ccr.session :as session]
   [ccr.tree :as tree]
   [ccr.cnd :as cnd]
   [datomic.api :as d  :only [q db]]
   [clojure.zip :as zip]
   [clojure.data.zip :as z]
   [clojure.data.zip.xml :as zx]
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


(comment
  (import/start system)

  (def tx (d/transact-async (->> system :db :conn ) (read-string (slurp "c:/1111/out.txt"))))
  (def db (d/db (->> system :db :conn)))

  (->> (d/q '[:find ?e 
                  :in $ ?a ?pn ?pv
                  :where 
                  [?v ?a ?pv]
                  [?p :jcr.property/values ?v]
                  [?p :jcr.property/name ?pn]
                  [?e :jcr.node/properties ?p]
                  ]                   
                db 
                :jcr.value/string
                "jcr:uuid" 
                "02714eb2-1a0d-4107-b85f-2cbad748731f")
           (map #(d/entity db (first %)))
           (map d/touch)
           )

  (->> (d/q '[:find ?e 
              :in $ ?a ?pn ?pv
              :where 
              [?v ?a ?pv]
              [?p :jcr.property/values ?v]
              [?p :jcr.property/name ?pn]
              [?e :jcr.node/properties ?p]
              ]                   
            db 
            :jcr.value/boolean
            "isp:hidden" 
            false)
       count
       ))
