(defproject ccr "0.1.0-SNAPSHOT"
  :description "A clojure content repository system"
  :dependencies [[org.clojure/clojure "1.7.0-alpha4"]
                 ;;[org.clojure/clojure "1.6.0"]
                 [org.clojure/data.zip "0.1.1"]
                 [org.clojure/data.xml "0.0.8"]
                 [com.datomic/datomic-free "0.9.5078" :exclusions [joda-time]]
                 [instaparse "1.3.5"]
                 [enlive "1.1.5"]
                 [org.clojure/data.codec "0.1.0"]]
  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.4"]]
                   :source-paths ["dev"]}}

  :jvm-opts ["-Xss10m" "-Xmx1400m"]
  )
