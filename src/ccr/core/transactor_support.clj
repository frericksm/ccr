(ns ccr.core.transactor-support
  (:require [ccr.core.model :as m]
            [datomic.api :as d  :only [q db pull pull-many transact]]))

(defn debug [m x] (println m x) x)

(defn child-item-name [db id]
  (m/first-property-value db id "jcr:name"))

(defn mixin? [db node-type-name]
  (as-> node-type-name x
    (m/nodetype-property-query x "jcr:isMixin")
    (d/q x db) 
    (map first x)
    (first x)
    (true? x)))

(defn declared-supertype-names [db node-type-name]
  (as-> node-type-name x
    (m/nodetype-property-query x "jcr:supertypes")
    (d/q x db) 
    (map first x)
    (set x)))

(defn ^:private calc-supertype-tree
  "Liefert zum node-type-name eine Map, die die Supertypestruktur abbildet. E.g
  {node-type-name [supertype1 supertype1]
   supertype1 [supertype3]
   supertype2 []
   supertype3 []}"
  [db node-type-name]
  (loop [h {::root [node-type-name]}]
    (let [not-visited (clojure.set/difference (set (flatten (vals h)))
                                              (set (keys h)))]
      (if (empty? not-visited)
        (dissoc h ::root)
        (let [new_h (reduce (fn [a v]
                              (let [dsn (declared-supertype-names db v)]
                                (assoc a v dsn)))
                            h
                            not-visited
                            )]
          (recur new_h))))))

(defn read-child-node-definition-ids [db node-type-name]
      (as-> node-type-name x
        (m/nodetype-child-query x "jcr:childNodeDefinition")
        (d/q x db) 
        (map first x)
        ))

(defn child-node-definition-ids [db node-type-name]
  (let [st-tree (calc-supertype-tree db node-type-name)
        cndef-ids (as-> st-tree x
                    (keys x)
                    (map (fn [nt-name]
                           (vector nt-name
                                   (read-child-node-definition-ids db nt-name))) x)
                    (into {} x)) ;;child-node-defintion-ids je nodetype
        ]
    (loop [result []
           next-to-merge [node-type-name]]
      (if (empty? next-to-merge)
        result
        (let [merge-candidates (as-> next-to-merge x
                                 (map (fn [n] (get cndef-ids n)) x)
                                 (apply concat x))
              new_next-to-merge (as-> next-to-merge x
                                  (map (fn [n] (get st-tree n)) x)
                                  (apply concat x)) 
              all-names (as-> result x
                          (map (fn [id] (child-item-name db id)) x)
                          (set x))
              new_result (as-> merge-candidates x
                           (filter 
                            (fn [mc] 
                              (let [mc-name (child-item-name db mc)]
                                (or (= mc-name "*") ;;residual 
                                    (not (contains? all-names mc-name))))) x) 
                           (concat result x))]
          (recur new_result new_next-to-merge)))))
  )

(defn required-primary-type-names [db id]
  (as-> "jcr:requiredPrimaryTypes" x
    (m/all-property-values db id x)
    (set x)))

(defn calc-supertype-names [db node-type-name]
  (loop [dst1 (declared-supertype-names db node-type-name)]
    (let [dst2 (as-> dst1 x
                 (map (fn [nt] (declared-supertype-names db nt)) x)
                 (apply concat x)
                 (set x))]
      (if (clojure.set/subset? dst2 dst1)
        dst1
        (recur (clojure.set/union dst1 dst2))))))

(defn supertype-names [db node-type-name]
  (let [mix (mixin? db node-type-name)]
      (as-> (calc-supertype-names db node-type-name) x
        (if mix x (cons "nt:base" x))
        (set x))))

(defn ^:private childnode-id-by-name
  "Returns the id of the child node with 'name' of node with parent-node-id"
  [db parent-node-id name]
  (let [g (re-matches  #"([^/:\[\]\|\*]+)(\[(\d+)\])?" name) ;; split basename and optional index
        basename (second (debug "groups" g))
        index (if (nil? (nth g 3)) 0 (Integer/parseInt (nth g 3)))]
    (as-> (m/child-query parent-node-id basename index) x
      (d/q x db)
      (map first x)
      (first x))))

(defn node-by-path
  "Returns the child node of 'parent-node' denoted by 'rel-path-segments' (the result of ccr.core.path/to-path)"
  [db parent-node-id rel-path-segments]

  (loop [node-id  parent-node-id
         path-segments rel-path-segments]
    (if (nil? node-id)
      (throw (IllegalArgumentException. "Path not found"))
      (if (empty? path-segments)
        node-id
        (let [segment (first path-segments)
              new_path-segments (rest path-segments)
              new_node-id (childnode-id-by-name db node-id segment)]
          (recur new_node-id
                 new_path-segments))))))

(defn autocreated-childnodes
  [db childnode-definition]
  []
  )

(defn autocreated-properties
  [db childnode-definition]
  []
  )

(defn matching-cnd
  [db cnd-ids basename primary-node-type]
  (as-> cnd-ids x
    (filter (fn [cnd-id]
              (let [cin (child-item-name db cnd-id)
                    rpt (required-primary-type-names db cnd-id)
                    cin-residual? (= "*" cin)
                    cin-matches? (= basename cin)
                    super-types (supertype-names db primary-node-type)
                    covers-nt? (clojure.set/subset? (set rpt) super-types)]
                (or (and  cin-residual? covers-nt?)
                    (and  cin-matches? covers-nt?)))
              ))
    ))

(defn can-add-child-node? 
  ([db node-type-name childNodeName]
   (let [nd-ids (child-node-definition-ids db node-type-name) 
         name-match-cndefs (filter (fn [id] 
                                     (= childNodeName
                                        (child-item-name db id)))
                                   nd-ids)
         residuals (filter (fn [id] 
                             (= "*"
                                (child-item-name db id)))
                           nd-ids)]
     (or (= 1 (count name-match-cndefs))
         (and (= 0 (count name-match-cndefs))
              (= 1 (count residuals))))))
  
  ([db node-type-name childNodeName nt-to-add]
   (let [type-hierarchy (set (cons nt-to-add (supertype-names db nt-to-add)))
         nd-ids (child-node-definition-ids db node-type-name)
         name-match-cndefs (filter (fn [cd] 
                                     (and (= childNodeName
                                             (child-item-name db cd))
                                          (not (empty? 
                                                (clojure.set/intersection (required-primary-type-names db cd) 
                                                                          type-hierarchy))))) nd-ids)
         residuals (filter (fn [cd] 
                             (and (= "*" (child-item-name db cd))
                                  (not (empty? 
                                        (clojure.set/intersection (required-primary-type-names db cd) 
                                                                  type-hierarchy))))) nd-ids)]
     (or (= 1 (count name-match-cndefs))         ;; exactly 1 node definition to the childNodeName 
         (and (= 0 (count name-match-cndefs))    ;; no node definition to the childNodeName
              (= 1 (count residuals))            ;; exactly 1 residual node definition
              )))))
