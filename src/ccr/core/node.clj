(ns ccr.core.node
  (:require [ccr.api.node]))


(defrecord NodeImpl [session id]
  ccr.api.node/Node

  (add-node [this relPath]
   "a"
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
