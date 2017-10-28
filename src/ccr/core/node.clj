(ns ccr.core.node
  (:require [ccr.api.node]
            [ccr.core.transaction-recorder :as tr]
            [ccr.core.model :as m]
            [ccr.core.nodetype :as n]
            [ccr.core.path :as p]
            [ccr.api.nodetype]
            [datomic.api :as d  :only [q db pull pull-many transact]]
            ))

(defn childnode-by-name
  "Returns the id of the child node with 'name' of node with parent-node-id"
  [db parent-node-id name]
  (let [g (re-find  #"(\w+)(\[(\d+)\])?" name)
        basename (second g)
        index (if (= 4 (count g)) (Integer/parseInt (nth g 3)) 0)]
    (as-> (m/child-query parent-node-id basename index) x
      (d/q x)
      (map first x))))

(deftype NodeImpl [session id]
  ccr.api.node/Node

  (add-node [this relPath]
    (let [db (tr/current-db session)   
          pnt (m/first-property-value db id "jcr:primaryType")
          cnds (ccr.api.nodetype/child-node-definitions (n/nodetype db pnt))]
      
      pnt)
    )

  (add-node [this replPath primaryNodeTypeName]
    )
  
  (definition [this]
    )
  
  (identifier [this]
    )
  
  (index [this]
    )
  
  (mixinNodeTypes [this]
    )

  (node [this relPath]
    
    )


  (nodes [this]
       )

  (nodes [this namePattern]
    )
  (primary-item [this]
    )

  (primary-nodetype [this]
    )
  

  
  ccr.api.node/Item

  (ancestor [this depth]
    )

  (depth [this]
    )

  (item-name [this]
    )
 

  (parent [this]
    )

  (path [this]
    )

  (session [this]
    )

  (modified? [this]
    )

  (new? [this]
    )

  (node? [this]
    )

  (same? [this otherItem]
    )

  (refresh [this keepChanges]
    )
  (remove-item [this]
    )

  )

(defn node [session id]
   (->NodeImpl session id))
