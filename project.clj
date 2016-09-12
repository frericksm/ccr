(defproject ccr "0.1.0-SNAPSHOT"
  :description "A clojure content repository system"
  :dependencies [[org.clojure/clojure "1.9.0-alpha12"]
                 ;;[org.clojure/clojure "1.6.0"]
                 [org.clojure/data.zip "0.1.1"]
                 [org.clojure/data.xml "0.0.8"]
                 [instaparse "1.4.3"]
                 [enlive "1.1.5"]
                 [javax.jcr/jcr "2.0"]
                 [org.clojure/data.codec "0.1.0"]
                 [org.clojure/tools.logging "0.3.1"]]

  :repositories {"my.datomic.com" {:url "https://my.datomic.com/repo"
                                   :creds :gpg}}
  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.4"]
                                  [com.datomic/datomic-free "0.9.5394"
                                   :exclusions [joda-time]]]
                   :source-paths ["dev"]}
             :db2 {:dependencies
                   [[com.ibm.db2/db2jcc "1.0"]
                    [com.ibm.db2/db2jcc_license_cu "1.0"]
                    [com.ibm.db2/db2jcc_license_cisuz "1.0"]
                    [com.datomic/datomic-pro "0.9.5130"
                     :exclusions [joda-time]]]}
             }
  :jvm-opts ["-Xss5m" "-Xmx1g"]
  )
