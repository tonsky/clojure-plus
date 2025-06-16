(ns clojure+.test-test
  (:require
   [clojure+.test :as test+]
   [clojure.test :as test :refer [is are deftest testing]])
  (:import
   [clojure.lang ExceptionInfo]))

(require 'clojure+.test :reload)

(defn f [x]
  x)

(deftest out-test-pass
  (Thread/sleep 100)
  (println "before")
  (Thread/sleep 100)
  (is (= 1 1))
  (Thread/sleep 100)
  (println "middle")
  (Thread/sleep 100)
  (is (= 2 2))
  (Thread/sleep 100)
  (println "after"))

(deftest out-test-fail
  (Thread/sleep 100)
  (println "before")
  (Thread/sleep 100)
  (is (= 1 2))
  (Thread/sleep 100)
  (println "middle")
  (Thread/sleep 100)
  (is (= 2 3))
  (Thread/sleep 100)
  (println "after"))

(deftest equal-test
  (is (= (f 1) (f 2)))
  (is (= (f 1) (f 2) (f 3)))
  (is (not= (f 1) (f 1)))
  (is (not= (f 1) (f 1) (f 1)))
  (is (=
        {:a 1 :b 2 :d {:a 1 :b 2}}
        {:b 2 :c 3 :d {:b 2 :c 3}}))
  (is (= {:a 1} {:b 2}))
  (is (= {:a 1} {:a 1 :b 2}))
  (is (= {:a 1 :b 2} {:a 1}))
  (is (= [1 2 3 4 5] [1 5 3 1]))
  (is (= #{1 2 3 4 5} #{5 3 1})))

(deftest not-test
  (is (not :true)))

(deftest nesting-test
  (testing "a"
    (is (= 1 2)))
  (testing "a"
    (testing "b"
      (testing "c"
        (is (= 3 4))))))

(deftest exceptions-test
  (is (thrown? ExceptionInfo (/ 1 0)))
  (is (throw (ex-info "inside (is)" {:a 1})))
  (throw (ex-info "outside" {:b 2})))

(deftest nested-exception-test
  (testing "a"
    (testing "b"
      (testing "c"
        (/ 1 0)))))

(defn h []
  (throw (ex-info "Oops" {})))

(defn g []
  (h))

(deftest line-number-throw-test
  ((fn [x]
     (let [y 2]
       (g))) 1))

(deftest line-number-throw-test-2
  ((fn [x]
     (let [y 2]
       (is (g)))) 1))

(deftest line-number-is-test
  ((fn [x]
     (let [y 2]
       (is (= x y)))) 1))

(deftest long-test []
  (let [arr (repeatedly 100000 rand)]
    (is (= (sort arr) (sort arr)))))

(deftest interrupt-test []
  (test+/install!)
  (let [t (Thread.
            (fn []
              (test+/run #'long-test)
              (recur)))]
    (.setUncaughtExceptionHandler t
      (reify Thread$UncaughtExceptionHandler
        (uncaughtException [_ _ ex]
          (println ex))))
    (.start t)
    (Thread/sleep 1000)
    (.interrupt t)
    (.join t)))

(comment
  (test+/install!)
  (test+/run #'out-test-pass #'out-test-fail)
  (test+/run {:capture-output? false} #'out-test-pass)
  (test+/run {:capture-output? false} #'out-test-fail)
  (test+/run #'equal-test)
  (test+/run #'not-test)
  (test+/run #'nesting-test)
  (test+/run #'exceptions-test)
  (test+/run #'nested-exception-test)
  (test+/run #'line-number-throw-test)   ;; line 82
  (test+/run #'line-number-throw-test-2) ;; line 87
  (test+/run #'line-number-is-test)      ;; line 92
  (clojure.test/test-var #'interrupt-test)
  (.printStackTrace (InterruptedException.))
  (test+/run {:capture-output? false} #'out-test-pass #'out-test-fail)
  (test+/run {:randomize? false})
  (clojure+.hashp/install!))