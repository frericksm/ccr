(ns ccr.nodetypes
  (:require [clojure.data.xml :as xml]
            [clojure.data.zip.xml :as zx]
            [clojure.data.zip :as z]
            [clojure.zip :as zip]
            [datomic.api :as d  :only [q db]]
            [clojure.java.io :as io]
            [clojure.instant :as instant]
            [clojure.pprint :as pp]))

(def builins-file "C:/Projekte/isp-head/ContentEditor/Prod/META-INF/repository/builtin.xml_")
(def custom-file "C:/jcr111/repository/nodetypes/custom_nodetypes.xml")


(defn load-node-types []
  (let [builtins (as->  builins-file x
                        (io/input-stream x)
                        (xml/parse x))
        customs (as->  custom-file x
                        (io/input-stream x)
                        (xml/parse x))
        ]))
