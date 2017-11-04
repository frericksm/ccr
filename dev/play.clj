
(in-ns 'user)

(use 'user :reload-all)

(start)

(def r (:repository system))

(def s (r/login r "michael" "default"))

(def rn (s/root-node s))

(import/import-xml s rn "/home/michael/projects/ccr/samples/c2.xml")

(as-> (n/properties (n/node rn "V1.0.3/isp:activityBody")) x
        (map n/item-name x))


(as-> (n/properties (n/node rn "V1.0.3/isp:activityBody")) x
        (map n/value x))



(import/import-xml s rn "/home/michael/projects/ccr/samples/content.xml")

(as-> (n/nodes (n/node rn "V1.0.3/R/Auslaufende Preisbefreiung/Auslaufende Preisbefreiung/isp:activities/KONTO_LESEN_2/isp:activityBody/isp:dataMappings/isp:dataMapping[2]")) x
        (map n/item-name x))
