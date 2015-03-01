(ns ccr.core.system)

(defn system
  "Returns a new instance of the whole application."
  []
  {})


(defn start
  "Performs side effects to initialize the system, acquire resources,
  and start it running. Returns an updated instance of the system."
  [system]
  (->> system
       ;;(r/start)
       ))

(defn stop
  "Performs side effects to shut down the system and release its
  resources. Returns an updated instance of the system."
  [system]
  (->> system
       ;;(r/stop)
       ))
