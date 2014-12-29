(ns ccr.system
  (:require [ccr.repository :as r]))

(defn system
  "Returns a new instance of the whole application."
  []
  {:uri "datomic:mem://dev"
   :import-file "content.xml"
   :tx-out-file "c:/1111/tx-data.txt"
   :builtin-nodetypes-file "???/builtin.xml_"
   :custom-nodetypes-file "???custom_nodetypes.xml"
   })


(defn start
  "Performs side effects to initialize the system, acquire resources,
  and start it running. Returns an updated instance of the system."
  [system]
  (->> system
       (r/start)))

(defn stop
  "Performs side effects to shut down the system and release its
  resources. Returns an updated instance of the system."
  [system]
  (->> system
       (r/stop)))
