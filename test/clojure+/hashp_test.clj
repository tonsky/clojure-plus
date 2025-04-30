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
        res (binding [*out* sw]
              (clojure.core/eval (read-string s)))]
    {:out (-> (str sw)
            (str/replace #"(?<!\033)\[[^\]]+\]" "[<pos>]"))
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
