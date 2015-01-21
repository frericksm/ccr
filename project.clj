(defproject ccr "0.1.0-SNAPSHOT"
  :description "A clojure content repository system"
  :dependencies [[org.clojure/clojure "1.7.0-alpha4"]
                 ;;[org.clojure/clojure "1.6.0"]
                 [org.clojure/data.zip "0.1.1"]
                 [org.clojure/data.xml "0.0.8"]
                 [instaparse "1.3.5"]
                 [enlive "1.1.5"]
                 [javax.jcr/jcr "2.0"]
                 [org.clojure/data.codec "0.1.0"]
                 [com.ibm.db2/db2jcc "1.0" ]
		 [com.ibm.db2/db2jcc_license_cu "1.0" ]
		 [com.ibm.db2/db2jcc_license_cisuz "1.0" ]
                 [com.datomic/datomic-pro "0.9.5130" :exclusions [joda-time]]
                 ;;[com.datomic/datomic-free "0.9.5078"
                 ;;:exclusions [joda-time]]
                 ]

  :repositories {"my.datomic.com" {:url "https://my.datomic.com/repo"
                                   :creds :gpg}}

  
  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.4"]]
                   :source-paths ["dev"]}}
  :java-cmd "C:/Program Files/Java/jdk1.7.0_67/bin/java.exe"
  :jvm-opts ["-Xss5m" "-Xmx1400m"]
  )
