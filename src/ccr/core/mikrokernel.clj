(ns ccr.core.mikrokernel
  (:require [datomic.api :as d  :only [q db]])
  ;;(:require clojure.pprint)
  )


(defn head-revision [repository]
   (d/db (:connection repository)))
