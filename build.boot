(require '[boot.core]
         '[boot.repl])


(swap! boot.repl/*default-dependencies*
       concat '[[cider/cider-nrepl "0.9.1"]])

(swap! boot.repl/*default-middleware*
       conj 'cider.nrepl/cider-middleware)


(set-env!
 :source-paths #{"src" "test"}
 :resource-paths #{"resources"}
 :dependencies '[[org.clojure/clojure "1.9.0-alpha14"]
                 [org.clojure/data.zip "0.1.1"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/tools.logging "0.3.1"]
                 [instaparse "1.4.5"]
                 [enlive "1.1.5"]
                 [javax.jcr/jcr "2.0"]
                 [org.clojure/data.codec "0.1.0"]
                 [com.datomic/datomic-free "0.9.5407"]
            
;;[com.datomic/datomic-free "0.9.5078" :exclusions [joda-time]]

                 [adzerk/boot-test "1.2.0" :scope "test"]
                 ])

(require '[adzerk.boot-test :refer :all])

(task-options! repl
               {:server true
                :port 44444
                :eval "(in-ns user)"})

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

(deftask load-data-readers
  "Setup for development"
  []
  (boot.core/load-data-readers!)
  identity)

(deftask dev-repl
  "Start repl with extended develop classpath"
  []
  (comp ;(setup-develop)
   (add-development-dependencies)
   ;(load-data-readers)
   (repl)
   (wait)))


