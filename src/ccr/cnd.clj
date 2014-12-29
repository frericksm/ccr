(ns ccr.cnd
  (:require [instaparse.core :as insta]))



(def cnd-parser
  (insta/parser
   "cnd ::= {ns_mapping | node_type_def};

  ns_mapping ::= <\"<\"> <#\"\\s*\">  prefix <#\"\\s*\"> <\"=\"> <#\"\\s*\"> uri <#\"\\s*\"> <\">\">;

  prefix ::= string;

  uri ::= string;

  node_type_def ::= node_type_name [<#\"\\s*\"> supertypes] [<#\"\\s*\"> options] <#\"\\s*\"> {property_def | child_node_def};


  child_node_def ::= <\"+\"> <#\"\\s*\"> node_name [<#\"\\s*\"> required_types] [<#\"\\s*\"> default_type] <#\"\\s*\"> attributes;

  node_type_name ::= <\"[\"> string <\"]\">;

  supertypes ::= <\">\"> <#\"\\s*\"> string_list;


  options ::= orderable_opt | mixin_opt | orderable_opt | mixin_opt | mixin_opt orderable_opt;

  orderable_opt ::= \"orderable\" | \"ord\" | \"o\";

  mixin_opt ::= \"MIXIN\" | \"mixin\" | \"mix\" | \"m\";

  property_def ::= <#\"\\s*\"> <\"-\"> <#\"\\s*\"> property_name [<#\"\\s*\"> property_type_decl] [<#\"\\s*\"> default_values] [<#\"\\s*\"> attributes] [<#\"\\s*\"> value_constraints];

  property_name ::= string;

  property_type_decl ::= <\"(\"> property_type <\")\">;

  property_type ::= \"STRING\" | \"String\" | \"string\" | \"BINARY\" | \"Binary\" | \"binary\" |
  \"LONG\" | \"Long\" | \"long\" | \"DOUBLE\" | \"Double\" | \"double\" |
  \"BOOLEAN\" | \"Boolean\" | \"boolean\" | \"DATE\" | \"Date\" | \"date\" |
  \"NAME\" | \"Name\" | \"name\" | \"PATH\" | \"Path\" | \"path\" | \"REFERENCE\" | \"Reference\" |
  \"reference\" | \"UNDEFINED\" | \"Undefined\" | \"undefined\" | \"*\";

  default_values ::= <\"=\"> <#\"\\s*\"> string_list;

  value_constraints ::= <\"<\"> <#\"\\s*\"> string_list;

  node_def ::= <\"+\"> <#\"\\s*\"> node_name [<#\"\\s*\"> required_types] [<#\"\\s*\"> default_type] [<#\"\\s*\"> attributes];

  node_name ::= string;

  required_types ::= <\"(\"> string_list <\")\">;

  default_type ::= <\"=\"> string;

  attributes ::= (<#\"\\s*\"> attribute)+

  attribute ::= \"PRIMARY\" | \"primary\" | \"pri\" | \"!\" |
               \"AUTOCREATED\" | \"autocreated\" | \"aut\" | \"a\" |
               \"MANDATORY\" | \"mandatory\" | \"man\" | \"m\" |
               \"PROTECTED\" | \"protected\" | \"pro\" | \"p\" |
               \"MULTIPLE\" | \"multiple\" | \"mul\" | \"*\" |
               \"COPY\" | \"Copy\" | \"copy\" |
               \"VERSION\" | \"Version\" | \"version\" |
               \"INITIALIZE\" | \"Initialize\" |
                  \"initialize\" |
               \"COMPUTE\" | \"Compute\" | \"compute\" |
               \"IGNORE\" | \"Ignore\" | \"ignore\" |
               \"ABORT\" | \"Abort\" | \"abort\";

  string_list ::= string {<#\"\\s*\"> <\",\"> <#\"\\s*\"> string};

  string ::= quoted_string | unquoted_string;

  quoted_string ::= <\"'\"> unquoted_string <\"'\">;

  unquoted_string ::= #\"[A-Za-z0-9:_]*\";"
   ;;:output-format :enlive
   ))
