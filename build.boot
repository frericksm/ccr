(set-env!
 :source-paths #{"src"}
 :resource-paths #{"resources"}
 :dependencies '[[org.clojure/clojure "1.7.0-alpha4"]
                 [org.clojure/data.zip "0.1.1"]
                 [org.clojure/data.xml "0.0.8"]
                 [instaparse "1.3.5"]
                 [enlive "1.1.5"]
                 [javax.jcr/jcr "2.0"]
                 [org.clojure/data.codec "0.1.0"]])

(task-options!
 repl {:server true
       :port 44444
       ;;:init-ns 'user
       })

(deftask add-datomic-pro-dependencies
  "Add datomic pro and sql storage dependencies. In particular: DB2)"
  []
  (set-env! :dependencies
            #(vec
              (concat %
                      '[[com.ibm.db2/db2jcc "1.0" ]
                        [com.ibm.db2/db2jcc_license_cu "1.0" ]
                        [com.ibm.db2/db2jcc_license_cisuz "1.0" ]
                        [com.datomic/datomic-pro "0.9.5130"
                         :exclusions [joda-time]]])))
  (set-env! :repositories
            #(vec (concat % {"my.datomic.com" {:url "https://my.datomic.com/repo"
                                               :creds :gpg}})))
  identity)

(deftask add-development-dependencies
  "Setup for development"
  []
  (set-env! :dependencies
            #(vec
              (concat %
                      '[[com.datomic/datomic-free "0.9.5078" :exclusions [joda-time]]])))
  identity)

(deftask setup-develop
  "Setup for development"
  []
  (set-env! :source-paths #(conj % "dev"))
  (set-env! :dependencies
            #(concat % '[[org.clojure/tools.namespace "0.2.4"]]))
  identity)

(deftask dev-repl
  "Start repl with extended develop classpath"
  []
  (comp ;(setup-develop)
        (add-development-dependencies)
        (repl)
        (wait)))


