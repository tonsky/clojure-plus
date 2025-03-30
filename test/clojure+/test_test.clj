(ns clojure+.test-test
  (:require
   [clojure+.test :as test+]
   [clojure.test :as test :refer [is are deftest testing]])
  (:import
   [clojure.lang ExceptionInfo]))

(require 'clojure+.test :reload)

(test+/install!)

(defn f [x]
  x)

(deftest out-test
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

(defn h []
  (throw (ex-info "Oops" {})))

(defn g []
  (h))

(deftest line-number-test
  (is (g)))

#_(test+/run #'out-test)
#_(test+/run #'equal-test)
#_(test+/run #'not-test)
#_(test+/run #'nesting-test)
#_(test+/run #'exceptions-test)
#_(test+/run #'line-number-test)
(test+/run *ns*)
