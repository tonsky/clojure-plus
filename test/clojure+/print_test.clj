(ns clojure+.print-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :as test :refer [is deftest testing use-fixtures]]
   [clojure+.print :as print])
  (:import
   [java.io File]
   [java.nio.file Path]))

(use-fixtures :once
  (fn [f]
    (set! *data-readers* (.getRawRoot #'*data-readers*))
    (f)))


;; #file

(deftest file-test
  (is (= "#file \"/abc/x\\\"y\"" (pr-str (io/file "/abc/x\"y"))))
  (let [file (read-string "#file \"x\\\"y\"")]
    (is (instance? File file))
    (is (= "x\"y" (File/.getPath file)))))


;; #path

(deftest path-test
  (is (= "#path \"/abc/x\\\"y\"" (pr-str (.toPath (io/file "/abc/x\"y")))))
  (let [path (read-string "#path \"x\\\"y\"")]
    (is (instance? Path path))
    (is (= "x\"y" (str path)))))


;; arrays

(deftest booleans-test
  (is (= "#booleans [true false true]"
        (pr-str (boolean-array [true false true]))))
  
  (is (= "#booleans []"
        (pr-str (boolean-array 0))))
  
  (let [arr (read-string "#booleans [true false true]")]
    (is (= boolean/1 (class arr)))
    (is (= [true false true] (vec arr)))))

(deftest bytes-test
  (is (= "#bytes [1 2 3]"
        (pr-str (byte-array [1 2 3]))))
  
  (is (= "#bytes []"
        (pr-str (byte-array 0))))
  
  (let [arr (read-string "#bytes [1 2 3]")]
    (is (= byte/1 (class arr)))
    (is (= [1 2 3] (vec arr)))))

(deftest chars-test
  (is (= "#chars [\\a \\b \\c]"
        (pr-str (char-array [\a \b \c]))))
  
  (is (= "#chars []"
        (pr-str (char-array 0))))
  
  (let [arr (read-string "#chars [\\a \\b \\c]")]
    (is (= char/1 (class arr)))
    (is (= [\a \b \c] (vec arr)))))

(deftest shorts-test
  (is (= "#shorts [1 2 3]"
        (pr-str (short-array [1 2 3]))))
  
  (is (= "#shorts []"
        (pr-str (short-array 0))))
  
  (let [arr (read-string "#shorts [1 2 3]")]
    (is (= short/1 (class arr)))
    (is (= [1 2 3] (vec arr)))))

(deftest ints-test
  (is (= "#ints [1 2 3]"
        (pr-str (int-array [1 2 3]))))
  
  (is (= "#ints []"
        (pr-str (int-array 0))))
  
  (let [arr (read-string "#ints [1 2 3]")]
    (is (= int/1 (class arr)))
    (is (= [1 2 3] (vec arr)))))

(deftest longs-test
  (is (= "#longs [1 2 3]"
        (pr-str (long-array [1 2 3]))))
  
  (is (= "#longs []"
        (pr-str (long-array 0))))
  
  (let [arr (read-string "#longs [1 2 3]")]
    (is (= long/1 (class arr)))
    (is (= [1 2 3] (vec arr)))))

(deftest floats-test
  (is (= "#floats [1.0 2.0 3.5]"
        (pr-str (float-array [1.0 2.0 3.5]))))
  
  (is (= "#floats []"
        (pr-str (float-array 0))))
  
  (let [arr (read-string "#floats [1.0 2.0 3.5]")]
    (is (= float/1 (class arr)))
    (is (= [1.0 2.0 3.5] (vec arr)))))

(deftest doubles-test
  (is (= "#doubles [1.0 2.0 3.5]"
        (pr-str (double-array [1.0 2.0 3.5]))))
  
  (is (= "#doubles []"
        (pr-str (double-array 0))))
  
  (let [arr (read-string "#doubles [1.0 2.0 3.5]")]
    (is (= double/1 (class arr)))
    (is (= [1.0 2.0 3.5] (vec arr)))))


;; #strings

(deftest strings-test
  (is (= "#strings [\"a\" \"b\" \"c\"]"
        (pr-str (into-array String ["a" "b" "c"]))))
  
  (is (= "#strings []"
        (pr-str (make-array String 0))))
  
  (is (= "#strings [nil nil]"
        (pr-str (make-array String 2))))
  
  (let [arr (read-string "#strings [\"a\" \"b\" \"c\"]")]
    (is (= String/1 (class arr)))
    (is (= ["a" "b" "c"] (vec arr)))))


;; #objects & #array

(deftest objects-test
  (is (= "#objects [\"a\" \"b\" \"c\"]"
        (pr-str (into-array Object ["a" "b" "c"]))))
  
  (is (= "#objects []"
        (pr-str (make-array Object 0))))
  
  (is (= "#objects [nil nil]"
        (pr-str (make-array Object 2))))
  
  (let [arr (read-string "#objects [\"a\" \"b\" \"c\"]")]
    (is (= Object/1 (class arr)))
    (is (= ["a" "b" "c"] (vec arr)))))
  
(deftest array-test
  (is (= "#array ^java.io.File/1 [#file \"a\" #file \"b\" #file \"c\"]"
        (pr-str (into-array File [(io/file "a") (io/file "b") (io/file "c")]))))
  
  (let [arr (read-string "#array ^java.io.File/1 [#file \"a\" #file \"b\" #file \"c\"]")]
    (is (= File/1 (class arr)))
    (is (= [(io/file "a") (io/file "b") (io/file "c")] (vec arr)))))

(deftest multi-array-test
  (is (= "#array ^String/2 [[\"a\"] [\"b\" \"c\"]]"
        (pr-str (into-array String/1 [(into-array String ["a"])
                                      (into-array String ["b" "c"])]))))
  
  (let [arr (read-string "#array ^String/2 [[\"a\"] [\"b\" \"c\"]]")]
    (is (= String/2 (class arr)))
    (is (= [["a"] ["b" "c"]] (mapv vec arr)))))
