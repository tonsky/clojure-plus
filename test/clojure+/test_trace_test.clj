(ns clojure+.test-trace-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [clojure+.error :as error]
   [clojure+.test]))

(use-fixtures :each
  (fn [f]
    (try
      (f)
      (finally
        (error/uninstall!)))))

(def test-trace
  [(StackTraceElement. "example$test_body" "invokeStatic" "example.clj" 10)
   (StackTraceElement. "clojure.test$test_var$fn__1234" "invoke" "test.clj" 717)
   (StackTraceElement. "clojure.test$test_var" "invokeStatic" "test.clj" 717)
   (StackTraceElement. "clojure.test$test_var" "invoke" "test.clj" 708)
   (StackTraceElement. "example$eval12" "invokeStatic" "NO_SOURCE_FILE" 1)
   (StackTraceElement. "example$eval12" "invoke" "NO_SOURCE_FILE" 1)
   (StackTraceElement. "clojure.lang.Compiler" "eval" "Compiler.java" 7700)
   (StackTraceElement. "clojure.main$repl" "invokeStatic" "main.clj" 437)
   (StackTraceElement. "java.lang.Thread" "run" "Thread.java" 1575)])

(deftest test-frame-omissions-test
  (doseq [runner-class ["clojure.test$test_var$fn__1234"
                        "clojure_PLUS_.test$test_var$fn__1234$fn__5678"]
          clean? [false true]
          collapse-repl? [false true]
          reverse? [false true]]
    (error/install! {:color? false
                     :clean? clean?
                     :collapse-repl? collapse-repl?
                     :reverse? reverse?})
    (binding [error/*trace-transform* (var-get (ns-resolve 'clojure+.test 'trace-transform))]
      (let [e (doto (Exception. "Oops" nil)
                (.setStackTrace (into-array StackTraceElement
                                  (assoc test-trace 1 (StackTraceElement. runner-class "invoke" "test.clj" 717)))))
            n (if clean? 6 8)
            trace (:trace (error/datafy-throwable e))]
        (is (= 2 (count trace)))
        (is (= (first test-trace) (:element (first trace))))
        (is (= ['...test-frames n] (last trace)))
        (is (str/includes? (with-out-str (print e)) (str "... " n " test frames omitted")))
        (is (str/includes? (with-out-str (pr e)) (str "[...test-frames " n "]"))))))
  (testing "no marker when the test boundary is absent"
    (error/install! {:collapse-repl? false})
    (binding [error/*trace-transform* (var-get (ns-resolve 'clojure+.test 'trace-transform))]
      (let [e (doto (Exception. "Oops" nil)
                (.setStackTrace (into-array StackTraceElement [(first test-trace)])))]
        (is (= [(first test-trace)] (map :element (:trace (error/datafy-throwable e)))))))))

(deftest collapse-tests-option-test
  (doseq [clean? [false true]
          collapse-repl? [false true]]
    (error/install! {:color? false
                     :clean? clean?
                     :collapse-tests? false
                     :collapse-repl? collapse-repl?})
    (let [e (doto (Exception. "Oops" nil)
              (.setStackTrace (into-array StackTraceElement test-trace)))
          unfiltered (:trace (error/datafy-throwable e))]
      (binding [error/*trace-transform* (var-get (ns-resolve 'clojure+.test 'trace-transform))]
        (is (= unfiltered (:trace (error/datafy-throwable e))))
        (is (str/includes? (with-out-str (print e))
              (if clean? "clojure.test/test-var/fn" "clojure.test$test_var$fn__1234.invoke")))
        (is (not (str/includes? (with-out-str (pr e)) "...test-frames")))
        (is (= collapse-repl? (boolean (some vector? (:trace (error/datafy-throwable e)))))))))
  (testing "install! resets the option to its default"
    (error/install! {:collapse-tests? false})
    (error/install!)
    (is (true? (:collapse-tests? error/config)))))
