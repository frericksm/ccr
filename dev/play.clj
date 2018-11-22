
(in-ns 'user)

(use 'user :reload-all)

(start)

(def r (:repository system))

(def s (r/login r "michael" "default"))

(def rn (s/root-node s))

#_(import/import-xml s rn "/home/michael/projects/ccr/samples/c2.xml")

(as-> (n/properties (n/node rn "V1.0.3/isp:activityBody")) x
        (map n/item-name x))


(as-> (n/properties (n/node rn "V1.0.3/isp:activityBody")) x
        (map n/value x))



(import/import-xml s rn "/home/michael/projects/ccr/samples/content.xml")

(as-> (n/nodes rn ) x
        (map n/item-name x))
