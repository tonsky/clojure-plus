(ns clojure+.hashp-test
  (:refer-clojure :exclude [eval])
  (:require
   [clojure.string :as str]
   [clojure.test :as test :refer [are deftest is testing use-fixtures]]
   [clojure+.hashp :as hashp])
  (:import
   [java.io StringWriter]))

(defn eval [s]
  (let [sw  (StringWriter.)
        res (binding [*ns*  (find-ns 'clojure+.hashp-test)
                      *out* sw]
              (clojure.core/eval (read-string s)))]
    {:out (-> (str sw)
            (str/replace #"(?<!\033)\[[^\]]+:-?\d+\]" "[<pos>]"))
     :res res}))

(defmacro with-hashp [opts & body]
  `(try
     (hashp/install! ~opts)
     ~@body
     (finally
       (hashp/uninstall!))))

(deftest color-test
  (with-hashp {:color? true}
    (testing "Result is passed through"
      (is (= 1 (:res (eval "#p 1")))))

    (testing "Colored output"
      (is (= "\033[34m#p 1 \033[37m[<pos>]\033[0m\n1\n"
            (:out (eval "#p 1")))))))

(deftest basic-test
  (with-hashp {:color? false}
    (testing "Black & white output"
      (is (= "#p 1 [<pos>]\n1\n"
            (:out (eval "#p 1")))))

    (testing "Nesting"
      (is (= "#p 1 [<pos>]
1
#p 2 [<pos>]
2
#p (+ #p 1 #p 2) [<pos>]
3
"
            (:out (eval "#p (+ #p 1 #p 2)")))))

    (testing "Thread first"
      (is (= "#p (+ 2) [<pos>]\n3\n"
            (:out (eval "(-> 1 #p (+ 2) (* 3))")))))

    (testing "Thread last"
      (is (= "#p (+ 2) [<pos>]\n3\n"
            (:out (eval "(->> 1 #p (+ 2) (* 3))")))))

    (testing "Thread naked"
      (is (= "#p str [<pos>]\n\"1\"\n"
            (:out (eval "(-> 1 #p str count)"))))
      (is (= "#p str [<pos>]\n\"1\"\n"
            (:out (eval "(->> 1 #p str count)")))))))

(deftest symbol-test
  (with-hashp {:symbol 'pp
               :color? false}
    (is (= "#pp 1 [<pos>]
1
#pp 2 [<pos>]
2
#pp (+ #pp 1 #pp 2) [<pos>]
3
"
          (:out (eval "#pp (+ #pp 1 #pp 2)"))))))

(defmacro macro1 [x]
  x)

(defmacro macro2 [x y]
  (list 'list x y))

(deftest special-forms-test
  (with-hashp {:color? false}
    (testing "special forms"
      (is (= {:res 1      :out "#p (if 1) [<pos>]\n1\n"}           (eval "(-> true #p (if 1))")))
      (is (= {:res nil    :out "#p (if 1) [<pos>]\nnil\n"}         (eval "(-> false #p (if 1))")))
      (is (= {:res 1      :out "#p (if true) [<pos>]\n1\n"}        (eval "(->> 1 #p (if true))")))
      (is (= {:res nil    :out "#p (if false) [<pos>]\nnil\n"}     (eval "(->> 1 #p (if false))")))
      (is (= {:res 1      :out "#p (if true 1 2) [<pos>]\n1\n"}    (eval "#p (if true 1 2)")))
      (is (= {:res 2      :out "#p (if false 1 2) [<pos>]\n2\n"}   (eval "#p (if false 1 2)")))
      (is (= {:res 1      :out "#p (if 1 2) [<pos>]\n1\n"}         (eval "(-> true #p (if 1 2))")))
      (is (= {:res 2      :out "#p (if 1 2) [<pos>]\n2\n"}         (eval "(-> false #p (if 1 2))")))
      (is (= {:res 1      :out "#p (if true 1) [<pos>]\n1\n"}      (eval "(->> 2 #p (if true 1))")))
      (is (= {:res 2      :out "#p (if false 1) [<pos>]\n2\n"}     (eval "(->> 2 #p (if false 1))")))
      (is (= {:res 1      :out "#p (let [x 1] x) [<pos>]\n1\n"}    (eval "#p (let [x 1] x)")))
      )
    (testing "fns with :inline"
      (is (= {:res false  :out "#p (nil? 123) [<pos>]\nfalse\n"}   (eval "#p (nil? 123)")))
      (is (= {:res false  :out "#p nil? [<pos>]\nfalse\n"}         (eval "(-> 123 #p nil?)")))
      (is (= {:res false  :out "#p nil? [<pos>]\nfalse\n"}         (eval "(->> 123 #p nil?)")))
      (is (= {:res false  :out "#p (nil?) [<pos>]\nfalse\n"}       (eval "(-> 123 #p (nil?))")))
      (is (= {:res false  :out "#p (nil?) [<pos>]\nfalse\n"}       (eval "(->> 123 #p (nil?))"))))
    (testing "macros"
      (is (= {:res 123    :out "#p (macro1 123) [<pos>]\n123\n"}   (eval "#p (macro1 123)")))
      (is (= {:res 123    :out "#p macro1 [<pos>]\n123\n"}         (eval "(-> 123 #p macro1)")))
      (is (= {:res 123    :out "#p macro1 [<pos>]\n123\n"}         (eval "(->> 123 #p macro1)")))
      (is (= {:res 123    :out "#p (macro1) [<pos>]\n123\n"}       (eval "(-> 123 #p (macro1))")))
      (is (= {:res 123    :out "#p (macro1) [<pos>]\n123\n"}       (eval "(->> 123 #p (macro1))")))
      (is (= {:res '(1 2) :out "#p (macro2 1 2) [<pos>]\n(1 2)\n"} (eval "#p (macro2 1 2)")))
      (is (= {:res '(1 2) :out "#p (macro2 2) [<pos>]\n(1 2)\n"}   (eval "(-> 1 #p (macro2 2))")))
      (is (= {:res '(1 2) :out "#p (macro2 1) [<pos>]\n(1 2)\n"}   (eval "(->> 2 #p (macro2 1))"))))))
