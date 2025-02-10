(ns clojure+.core-test
  (:require
   [clojure+.core :as core]
   [clojure.test :as test :refer [is deftest testing]]))

(defmethod clojure.test/assert-expr 'thrown-with-cause-msg? [msg form]
  ;; (is (thrown-with-cause-msg? c re expr))
  ;; Asserts that evaluating expr throws an exception of class c.
  ;; Also asserts that the message string of the *cause* exception matches
  ;; (with re-find) the regular expression re.
  (let [klass (nth form 1)
        re    (nth form 2)
        body  (nthnext form 3)]
    `(try ~@body
       (test/do-report {:type :fail, :message ~msg, :expected '~form, :actual nil})
       (catch ~klass e#
         (let [m# (if (.getCause e#) (.. e# getCause getMessage) (.getMessage e#))]
           (if (re-find ~re m#)
             (test/do-report {:type :pass, :message ~msg,
                              :expected '~form, :actual e#})
             (test/do-report {:type :fail, :message ~msg,
                              :expected '~form, :actual e#})))
         e#))))

(deftest if+-test
  (is (= [1 2]
        (core/if+ (and
                    (= 1 1)
                    :let [x 1
                          y (+ x 1)] ;; vars can depend on each other
                    (> y x))         ;; vars can be used in later conditions
          [x y]
          :false)))
  
  (testing "“else” branch can be omitted"
    (is (= [1 2]
          (core/if+ (and
                      (= 1 1)
                      :let [x 1
                            y (+ x 1)]
                      (> y x))
            [x y]))))
  
  (testing "if can fail"
    (is (= :false
          (core/if+ (and
                      (= 1 1)
                      :let [x 1
                            y (+ x 1)]
                      (< y x))
            [x y]
            :false)))
  
    (is (= :false
          (core/if+ (and
                      (= 1 2)
                      :let [x 1
                            y (+ x 1)]
                      (> y x))
            [x y]
            :false))))
  
  (testing "and without conditions"
    (is (= true
          (core/if+ (and)
            true
            false)))
    
    (is (= false
          (core/if+ (and :let [x false])
            x
            true))))

  (testing "doesn’t work inside or"
    (is (thrown-with-cause-msg? Exception #"Unable to resolve symbol: x"
          (eval
            '(clojure+.core/if+ (or
                                  (= 1 2)
                                  :let [x 3])
               x
               false)))))
  
  (testing "vars are not accessible in else branch"
    (is (thrown-with-cause-msg? Exception #"Unable to resolve symbol: x"
          (eval
            '(clojure+.core/if+ (and
                                  :let [x 3])
               true
               x))))))

(deftest when+-test
  (is (= [1 2]
        (core/when+ (and
                      (= 1 1)
                      :let [x 1
                            y (+ x 1)]
                      (> y x))
          [x y])))
  
  (is (= nil
        (core/when+ (and
                      (= 1 1)
                      :let [x 1
                            y (+ x 1)]
                      (< y x))
          [x y])))
  
  (is (= nil
        (core/when+ (and
                      (= 1 2)
                      :let [x 1
                            y (+ x 1)]
                      (> y x))
          [x y])))
  
  (is (= false
        (core/when+ (and :let [x false])
          x))))

(deftest cond+-test
  (testing "Define variables"
    (is (= "1" 
          (core/cond+
            (= 1 2) :false
            :let    [x 1]
            (= 1 x) (str x)))))
   
  (testing "Insert imperative code"
    (let [*atom (atom 0)]
      (is (= 1
            (core/cond+
              (= 1 2) :false
              :do     (swap! *atom inc)
              :else   @*atom)))))
   
  (testing "variables inside conditions"
    (is (= [2 3]
          (core/cond+
            (and
              (= 1 1)
              :let [x 2, y (+ x 1)]
              (> y x))
            [x y]))))
  
  (testing "no variables inside or"
    (is (thrown-with-cause-msg? Exception #"Unable to resolve symbol: x"
          (eval
            '(clojure+.core/cond+
               (or (= 1 2)
                 :let [x 2])
               x)))))
    
  (testing "no variables leaking outside branch"
    (is (thrown-with-cause-msg? Exception #"Unable to resolve symbol: x"
          (eval
            '(clojure+.core/cond+
               (and (= 1 2)
                 :let [x 2])
               x
               :else x)))))
  
  (testing "catch-all condition"
    (is (= :false
          (core/cond+
            (= 1 2) :true
            :else   :false))))
  
  (testing "no catch-all condition"
    (is (= nil
          (core/cond+
            (= 1 2) :true))))

  (testing "trailing condition"
    (is (= :false
          (core/cond+
            (= 1 2) :true
            :false)))))

