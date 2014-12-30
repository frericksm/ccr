(ns ccr.cnd
  (:require [instaparse.core :as insta]))


;; http://www.day.com/maven/jcr/2.0/25_Appendix.html

(def cnd-parser
  (insta/parser
   "cnd ::= {ns_mapping | node_type_def};

  ns_mapping ::= <#\"\\s*\"> <\"<\"> <#\"\\s*\">  prefix <#\"\\s*\"> <\"=\"> <#\"\\s*\"> uri <#\"\\s*\"> <\">\">;

  prefix ::= string;

  uri ::= quoted_uri | unquoted_uri;

  node_type_def ::= <#\"\\s*\"> node_type_name [<#\"\\s*\"> supertypes] [<#\"\\s*\"> NodeTypeAttribute] <#\"\\s*\"> {property_def | child_node_def};


  child_node_def ::= <#\"\\s*\"> <\"+\"> <#\"\\s*\"> node_name [<#\"\\s*\"> required_types] [<#\"\\s*\"> default_type] <#\"\\s*\"> NodeAttributes;

  node_type_name ::= <\"[\"> string <\"]\">;

  supertypes ::= <\">\"> <#\"\\s*\"> string_list;


  NodeTypeAttribute ::= Orderable | Mixin | Abstract | Query | PrimaryItem;

  Orderable ::= \"ORDERABLE\" | \"orderable\" | \"ord\" | \"o\";

  Mixin ::= \"MIXIN\" | \"mixin\" | \"mix\" | \"m\";

  Abstract ::= \"abstract\" | \"abs\" | \"a\";

  Query ::= \"noquery\" | \"nq\" | \"query\" | \"q\";
 
  PrimaryItem ::= ('PRIMARYITEM'| 'primaryitem'| '!') <#\"\\s*\"> string;

  property_def ::= <#\"\\s*\"> <\"-\"> <#\"\\s*\"> property_name [<#\"\\s*\"> property_type_decl] [<#\"\\s*\"> default_values] [<#\"\\s*\"> PropertyAttributes] [<#\"\\s*\"> value_constraints];

  property_name ::= string | '*';

  property_type_decl ::= <\"(\"> property_type <\")\">;

  property_type ::= \"STRING\" | \"String\" | \"string\" | \"BINARY\" | \"Binary\" | \"binary\" |
  \"LONG\" | \"Long\" | \"long\" | \"DOUBLE\" | \"Double\" | \"double\" |
  \"BOOLEAN\" | \"Boolean\" | \"boolean\" | \"DATE\" | \"Date\" | \"date\" |
  \"NAME\" | \"Name\" | \"name\" | \"PATH\" | \"Path\" | \"path\" | \"WEAKREFERENCE\" |
  \"Weakreference\" | \"weakreference\" | \"REFERENCE\" | \"Reference\" |
  \"reference\" | \"UNDEFINED\" | \"Undefined\" | \"undefined\" | \"*\";

  default_values ::= <\"=\"> <#\"\\s*\"> string_list;

  value_constraints ::= <\"<\"> <#\"\\s*\"> string_list;

  node_name ::= string | '*';

  required_types ::= <\"(\"> string_list <\")\">;

  default_type ::= <\"=\"> <#\"\\s*\"> string;

  attributes ::= (<#\"\\s*\"> attribute)+

  PropertyAttributes ::= (<#\"\\s*\"> PropertyAttribute)+;

  PropertyAttribute ::= Autocreated | Mandatory | Protected | Opv | Multiple ;

  NodeAttributes ::= (<#\"\\s*\"> NodeAttribute)+;

  NodeAttribute ::= Autocreated | Mandatory | Protected | Opv | Sns;

  Autocreated ::= ('AUTOCREATED' | 'autocreated' | 'aut' | 'a' );

  Mandatory ::= ('MANDATORY' | 'mandatory' | 'man' | 'm');

  Protected ::= ('PROTECTED' | 'protected' | 'pro' | 'p');

  Opv ::= 'COPY' | 'VERSION' | 'INITIALIZE' | 'COMPUTE' | 'IGNORE' | 'ABORT' | ('OPV' '?');

  Sns ::= ('SNS' | 'sns' );

  Multiple ::= \"MULTIPLE\" | \"multiple\" | \"mul\";

  attribute ::= \"PRIMARY\" | \"primary\" | \"pri\" | \"!\" |
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

  unquoted_string ::= #\"[A-Za-z0-9:_]*\";

  quoted_uri ::= <\"'\"> unquoted_uri <\"'\">;

  unquoted_uri ::= #\"[A-Za-z0-9:_/\\.]*\";

"


   
   ;;:output-format :enlive
   ))
