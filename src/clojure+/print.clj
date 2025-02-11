(ns clojure+.print
  (:require
   [clojure.string :as str])
  (:import
   [java.io File Writer]
   [java.nio.file Path]))

(defmacro defprint [type [value writer] & body]
  `(do
     (defmethod print-method ~type [~(vary-meta value assoc :tag type)
                                    ~(vary-meta writer assoc :tag 'java.io.Writer)]
       ~@body)
     (defmethod print-dup ~type [~value ~writer]
       (print-method ~value ~writer))))


;; File

(defprint File [file w]
  (.write w "#file \"")
  (.write w (str/replace (.getPath file) "\"" "\\\""))
  (.write w "\""))

(defn read-file [^String s]
  (File. s))

(alter-var-root #'*data-readers* assoc 'file #'read-file)


;; Path

(defprint Path [path w]
  (.write w "#path \"")
  (.write w (str/replace (str path) "\"" "\\\""))
  (.write w "\""))

(defn read-path [^String s]
  (Path/of s (make-array String 0)))

(alter-var-root #'*data-readers* assoc 'path #'read-path)


;; arrays

(defprint boolean/1 [arr w]
  (.write w "#booleans ")
  (print-method (vec arr) w))

(alter-var-root #'*data-readers* assoc 'booleans #'boolean-array)

(defprint byte/1 [arr w]
  (.write w "#bytes ")
  (print-method (vec arr) w))

(alter-var-root #'*data-readers* assoc 'bytes #'byte-array)

(defprint char/1 [arr w]
  (.write w "#chars ")
  (print-method (vec arr) w))

(alter-var-root #'*data-readers* assoc 'chars #'char-array)

(defprint short/1 [arr w]
  (.write w "#shorts ")
  (print-method (vec arr) w))

(alter-var-root #'*data-readers* assoc 'shorts #'short-array)

(defprint int/1 [arr w]
  (.write w "#ints ")
  (print-method (vec arr) w))

(alter-var-root #'*data-readers* assoc 'ints #'int-array)

(defprint long/1 [arr w]
  (.write w "#longs ")
  (print-method (vec arr) w))

(alter-var-root #'*data-readers* assoc 'longs #'long-array)

(defprint float/1 [arr w]
  (.write w "#floats ")
  (print-method (vec arr) w))

(alter-var-root #'*data-readers* assoc 'floats #'float-array)

(defprint double/1 [arr w]
  (.write w "#doubles ")
  (print-method (vec arr) w))

(alter-var-root #'*data-readers* assoc 'doubles #'double-array)


;; #strings

(defprint String/1 [arr w]
  (.write w "#strings ")
  (print-method (vec arr) w))

(defn read-strings [xs]
  (into-array String xs))

(alter-var-root #'*data-readers* assoc 'strings #'read-strings)


;; #objects & #array

(defn- print-array [arr w]
  (let [cls (class arr)]
    (if (and cls (.isArray cls))
      (@#'clojure.core/print-sequential "[" print-array " " "]" arr w)
      (print-method arr w))))

(defprint Object/1 [arr w]
  (let [cls  (class arr)
        base (.componentType cls)]
    (cond
      (= Object base)
      (do
        (.write w "#objects ")
        (print-method (vec arr) w))
      
      
      :else
      (do
        (.write w "#array ^")
        (if (= "java.lang" (.getPackageName cls))
          (.write w (subs (pr-str cls) (count "java.lang.")))
          (print-method cls w))
        (.write w " ")
        (print-array arr w)))))

(alter-var-root #'*data-readers* assoc 'objects #'object-array)

(defn- read-array [vals]
  (let [class (:tag (meta vals))
        class (cond-> class
                (symbol? class) resolve)
        base  (Class/.componentType class)
        arr   ^Object/1 (make-array base (count vals))]
    (doseq [i (range (count vals))
            :let [x (nth vals i)]]
      (aset arr i
        (if (.isArray base)
          (read-array (vary-meta x assoc :tag base))
          x)))
    arr))

(alter-var-root #'*data-readers* assoc 'array #'read-array)

(comment
  (set! *data-readers* (.getRawRoot #'*data-readers*)))
