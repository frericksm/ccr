

(d/transact
  [[:db/add todolist-id :todolist/todoitems todoitem-tempid]
   [:db/add todoitem-tempid :todoitem/text "Remember the milk"]
   [:append-position-in-scope
    todolist-id
    :todolist/todoitems
    todoitem-tempid
    :todoitem/position]])


(d/transact
  [[:db.fn/retractEntity todoitem-id]
   [:reset-position-in-scope
    todolist-id
    :todolist/todoitems
    todoitem-id
    :todoitem/position]])


(d/transact
  [[:db.fn/retractEntity todoitem-id]
   [:set-position-in-scope
    todolist-id
    :todolist/todoitems
    [56 21 92 10]
    :todoitem/position]])
