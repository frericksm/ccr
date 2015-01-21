(ns ccr.node
  (:require [ccr]))


(defrecord NodeImpl []
  ccr/Node

  (add-node [this ^String relPath]
   
    )

  (add-node [this ^String replPath ^String primaryNodeTypeName]
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
    [this namePattern]
    )

  (primary-item [this]
    )

  (primary-nodetype [this]
    )
  

  
  ccr/Item

  (ancestor [this depth]
    )

  (depth [this]
    )

  (item-name [this]
    )
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


