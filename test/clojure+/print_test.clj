(ns clojure+.print-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :as test :refer [is deftest testing use-fixtures]]
   [clojure+.print :as print])
  (:import
   [clojure.lang Atom Agent Delay ExceptionInfo IDeref IPending PersistentQueue Ref]
   [java.io File]
   [java.nio.file Path]
   [java.util.concurrent Future]))

(use-fixtures :once
  (fn [f]
    (set! *data-readers* (.getRawRoot #'*data-readers*))
    (f)))

(deftest file-test
  (is (= "#file \"/abc/x\\\"y\"" (pr-str (io/file "/abc/x\"y"))))
  (let [file (read-string "#file \"x\\\"y\"")]
    (is (instance? File file))
    (is (= "x\"y" (File/.getPath file)))))

(deftest path-test
  (is (= "#path \"/abc/x\\\"y\"" (pr-str (.toPath (io/file "/abc/x\"y")))))
  (let [path (read-string "#path \"x\\\"y\"")]
    (is (instance? Path path))
    (is (= "x\"y" (str path)))))

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

(deftest atom-test
  (is (= "#atom 123" (pr-str (atom 123))))
  (let [atom (read-string "#atom 123")]
    (is (instance? Atom atom))
    (is (= 123 @atom))))

(deftest agent-test
  (is (= "#agent 123" (pr-str (agent 123))))
  (let [agent (read-string "#agent 123")]
    (is (instance? Agent agent))
    (is (= 123 @agent))))

(deftest ref-test
  (is (= "#ref 123" (pr-str (ref 123))))
  (let [ref (read-string "#ref 123")]
    (is (instance? Ref ref))
    (is (= 123 @ref))))

(deftest promise-test
  (is (= "#promise :<pending...>" (pr-str (promise))))
  (let [promise (read-string "#promise :<pending...>")]
    (is (instance? IPending promise))
    (is (not (realized? promise))))

  (is (= "#promise 123" (pr-str (doto (promise) (deliver 123)))))
  (let [promise (read-string "#promise 123")]
    (is (instance? IPending promise))
    (is (realized? promise))
    (is (= 123 @promise))))

(deftest delay-test
  (is (= "#delay :<pending...>" (pr-str (delay))))
  (is (thrown-with-cause-msg? ExceptionInfo #"Can’t read back :<pending\.\.\.> delay"
        (read-string "#delay :<pending...>")))

  (is (= "#delay 123" (pr-str (doto (delay 123) (deref)))))
  (let [delay (read-string "#delay 123")]
    (is (instance? Delay delay))
    (is (realized? delay))
    (is (= 123 @delay))))

(deftest future-test
  (is (= "#future :<pending...>" (pr-str (future (Thread/sleep 100) 123))))
  (is (thrown-with-cause-msg? ExceptionInfo #"Can’t read back :<pending\.\.\.> future"
        (read-string "#future :<pending...>")))

  (is (= "#future 123" (pr-str (doto (future 123) (deref)))))
  (let [future (read-string "#future 123")]
    (is (instance? Future future))
    (is (realized? future))
    (is (= 123 @future))))

(deftest queue-test
  (let [q  (into PersistentQueue/EMPTY [1 2 3])
        _  (is (= "#queue [1 2 3]" (pr-str q)))
        q' (read-string "#queue [1 2 3]")
        _  (is (instance? PersistentQueue q))
        _  (is (= q q'))
        _  (is (= [1 2 3] (vec q')))]))
