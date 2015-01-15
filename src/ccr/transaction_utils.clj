(ns ccr.transaction-utils
  (:require [clojure.data.xml :as xml]
            [datomic.api :as d  :only [q db]]
            [clojure.java.io :as io]
            [clojure.instant :as instant]
            [net.cgrand.enlive-html :as html]
            [clojure.data.codec.base64 :as b64]))

(defn add-tx [node-id child-id]
  [{:db/id             node-id
    :jcr.node/children child-id}
   [:append-position-in-scope node-id :jcr.node/children child-id :jcr.node/position]
   ])

(defn translate-value [v]
  ;; Returns a vector of two elements:
  ;; 1. The replacement for V (new :db/id value if V is a map,
  ;;    a vector with maps replaced by :db/id's if V is a vector, etc.)
  ;; 2. The sequence of maps which were replaced by their new :db/id's,
  ;;    each map already contains the :db/id.
  (letfn [(translate-values [values]
            (let [mapped (map translate-value values)]
              [(reduce conj [] (map first mapped))
               (reduce concat '() (map second mapped))]))]
    (cond (map? v) (let [id (d/tempid :db.part/user)
                         translated-vals (translate-values (vals v))
                         translated-map (zipmap (keys v)
                                                (first translated-vals))]
                     [id (cons (assoc translated-map :db/id id)
                               (second translated-vals))])
          (vector? v) (translate-values v)
          :else [v nil])))
 
(defn to-transaction [data-map]
  (vec (second (translate-value data-map))))


(defn encode64 [ba]
  (let [is (java.io.ByteArrayInputStream. ba)
        os (java.io.ByteArrayOutputStream.)]
    (with-open [in is
                out os]
      (b64/encoding-transfer in out))
    (->> os
         (.toByteArray)
         (String. ))))

(defn decode64 [b64-string]
  (let [is (java.io.ByteArrayInputStream. (.getBytes b64-string))
        os (java.io.ByteArrayOutputStream.)]
    (with-open [in is
                out os]
      (b64/decoding-transfer in out))
    (.toByteArray os)))


(defn- print-byte-array
  "Print a byte array."
  [^bytes ba, ^java.io.Writer w]
  (.write w "#base64 \"")
  (.write w (encode64 ba))
  (.write w "\""))

(defmethod print-method (class (byte-array 1)) 
  [ba, ^java.io.Writer w]
  (print-byte-array ba w))

(defmethod print-dup (class (byte-array 1))
  [ba, ^java.io.Writer w]
  (print-byte-array ba w))











