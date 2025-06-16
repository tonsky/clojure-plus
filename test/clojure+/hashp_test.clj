(ns clojure+.hashp-test
  (:refer-clojure :exclude [eval])
  (:require
   [clojure.string :as str]
   [clojure.test :as test :refer [are deftest is testing use-fixtures]]
   [clojure+.core :as core]
   [clojure+.hashp :as hashp])
  (:import
   [java.io StringWriter]
   [java.util Collections]))

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

(deftest interop-test
  (with-hashp {:color? false}
    (testing "classes"
      (is (= {:res String :out "#p String [<pos>]\njava.lang.String\n"}                 (eval "#p String")))
      (is (= {:res String :out "#p java.lang.String [<pos>]\njava.lang.String\n"}       (eval "#p java.lang.String")))
      (is (= {:res StringWriter :out "#p StringWriter [<pos>]\njava.io.StringWriter\n"} (eval "#p StringWriter")))
      (core/if-clojure-version-gte "1.12.0"
        (do
          (is (= "#p String/1 [<pos>]\njava.lang.String/1\n"                              (:out (eval "#p String/1"))))
          (is (= "#p StringWriter/1 [<pos>]\njava.io.StringWriter/1\n"                    (:out (eval "#p StringWriter/1")))))))

    (testing "static methods"
      (is (= {:res [] :out "#p (Collections/emptyList) [<pos>]\n[]\n"}          (eval "#p (Collections/emptyList)")))
      (is (= {:res [] :out "#p (. Collections emptyList) [<pos>]\n[]\n"}        (eval "#p (. Collections emptyList)")))
      (is (= {:res [] :out "#p (. Collections (emptyList)) [<pos>]\n[]\n"}      (eval "#p (. Collections (emptyList))")))

      (is (= {:res "1" :out "#p (String/valueOf 1) [<pos>]\n\"1\"\n"}           (eval "#p (String/valueOf 1)")))
      (is (= {:res "1" :out "#p (. String valueOf 1) [<pos>]\n\"1\"\n"}         (eval "#p (. String valueOf 1)")))
      (is (= {:res "1" :out "#p (. String (valueOf 1)) [<pos>]\n\"1\"\n"}       (eval "#p (. String (valueOf 1))")))

      (is (= {:res "1.0" :out "#p (String/valueOf 1.0) [<pos>]\n\"1.0\"\n"}     (eval "#p (String/valueOf 1.0)")))
      (is (= {:res "1.0" :out "#p (. String valueOf 1.0) [<pos>]\n\"1.0\"\n"}   (eval "#p (. String valueOf 1.0)")))
      (is (= {:res "1.0" :out "#p (. String (valueOf 1.0)) [<pos>]\n\"1.0\"\n"} (eval "#p (. String (valueOf 1.0))"))))

    (testing "static fields"
      (is (= {:res 5 :out "#p Thread/NORM_PRIORITY [<pos>]\n5\n"}     (eval "#p Thread/NORM_PRIORITY")))
      (is (= {:res 5 :out "#p (. Thread NORM_PRIORITY) [<pos>]\n5\n"} (eval "#p (. Thread NORM_PRIORITY)"))))

    (testing "instance members"
      (is (= {:res "bc" :out "#p (.substring \"abc\" 1) [<pos>]\n\"bc\"\n"}        (eval "#p (.substring \"abc\" 1)")))
      (is (= {:res "bc" :out "#p (String/.substring \"abc\" 1) [<pos>]\n\"bc\"\n"} (eval "#p (String/.substring \"abc\" 1)")))
      (is (= {:res "bc" :out "#p (. \"abc\" substring 1) [<pos>]\n\"bc\"\n"}       (eval "#p (. \"abc\" substring 1)")))
      (is (= {:res "bc" :out "#p (. \"abc\" (substring 1)) [<pos>]\n\"bc\"\n"}     (eval "#p (. \"abc\" (substring 1))")))

      (is (= {:res "b" :out "#p (.substring \"abc\" 1 2) [<pos>]\n\"b\"\n"}        (eval "#p (.substring \"abc\" 1 2)")))
      (is (= {:res "b" :out "#p (String/.substring \"abc\" 1 2) [<pos>]\n\"b\"\n"} (eval "#p (String/.substring \"abc\" 1 2)")))
      (is (= {:res "b" :out "#p (. \"abc\" substring 1 2) [<pos>]\n\"b\"\n"}       (eval "#p (. \"abc\" substring 1 2)")))
      (is (= {:res "b" :out "#p (. \"abc\" (substring 1 2)) [<pos>]\n\"b\"\n"}     (eval "#p (. \"abc\" (substring 1 2))"))))

    (testing "instance fields"
      (is (= {:res 1 :out "#p (.-x (java.awt.Point. 1 2)) [<pos>]\n1\n"}  (eval "#p (.-x (java.awt.Point. 1 2))")))
      (is (= {:res 1 :out "#p (.x (java.awt.Point. 1 2)) [<pos>]\n1\n"}   (eval "#p (.x (java.awt.Point. 1 2))")))
      (is (= {:res 1 :out "#p (. (java.awt.Point. 1 2) -x) [<pos>]\n1\n"} (eval "#p (. (java.awt.Point. 1 2) -x)")))
      (is (= {:res 1 :out "#p (. (java.awt.Point. 1 2) x) [<pos>]\n1\n"}  (eval "#p (. (java.awt.Point. 1 2) x)"))))

    (testing "constructors"
      (is (= {:res 1 :out "#p (Long. 1) [<pos>]\n1\n"}    (eval "#p (Long. 1)")))
      (is (= {:res 1 :out "#p (new Long 1) [<pos>]\n1\n"} (eval "#p (new Long 1)")))
      (core/if-clojure-version-gte "1.12.0"
        (is (= {:res 1 :out "#p (Long/new 1) [<pos>]\n1\n"} (eval "#p (Long/new 1)"))))

      (is (= {:res 1 :out "#p (Long. \"1\") [<pos>]\n1\n"}    (eval "#p (Long. \"1\")")))
      (is (= {:res 1 :out "#p (new Long \"1\") [<pos>]\n1\n"} (eval "#p (new Long \"1\")")))
      (core/if-clojure-version-gte "1.12.0"
        (is (= {:res 1 :out "#p (Long/new \"1\") [<pos>]\n1\n"} (eval "#p (Long/new \"1\")")))))

    (testing "non-serializable"
      (is (= {:out "#p st [<pos>]\n[cls mthd \"file\" 0]\n"
              :res (StackTraceElement. "cls" "mthd" "file" 0)}
        (eval "(let [st (StackTraceElement. \"cls\" \"mthd\" \"file\" 0)]
                 #p st)"))))
    ))
