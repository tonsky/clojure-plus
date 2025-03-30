(ns clojure+.test
  (:require
   [clojure.data :as data]
   [clojure.string :as str]
   [clojure.test :as test]
   [clojure+.error :as error])
  (:import
   [clojure.lang MultiFn Namespace Var]
   [java.io ByteArrayOutputStream PrintStream OutputStreamWriter]
   [java.util.regex Pattern]))

(def ^:private system-out
  nil)

(def ^:private system-err
  nil)

(def ^:private clojure-test-out
  nil)

(def ^:private ^:dynamic *buffer*)

(def ^:private *time-ns
  (atom nil))

(def ^:private *time-total
  (atom nil))

(def ^:private *ns-failed?
  (atom false))

(defn- default-config []
  {:capture-output? true})

(def config
  (default-config))

(defn- capture-output []
  (Var/.doReset #'system-out System/out)
  (Var/.doReset #'system-err System/err)
  (Var/.doReset #'clojure-test-out test/*test-out*)
  
  (let [buffer (ByteArrayOutputStream.)
        ps     (PrintStream. buffer)
        out    (OutputStreamWriter. buffer)]
    (System/setOut ps)
    (System/setErr ps)
    (push-thread-bindings
      {#'*buffer*        buffer
       #'*out*           out
       #'*err*           out
       #'test/*test-out* out})))

(defn- flush-output []
  (.flush System/out)
  (flush)
  (binding [*out* clojure-test-out]
    (when-not @*ns-failed?
      (println)) ;; newline after "Testing <ns>..."
    (print (str *buffer*))
    (flush))
  (ByteArrayOutputStream/.reset *buffer*))

(defn- restore-output []
  (pop-thread-bindings)
  (System/setErr system-err)
  (System/setOut system-out)
  (Var/.doReset #'clojure-test-out nil)
  (Var/.doReset #'system-err nil)
  (Var/.doReset #'system-out nil))

(defn- report-begin-test-ns [m]
  (reset! *time-ns (System/currentTimeMillis))
  (compare-and-set! *time-total nil (System/currentTimeMillis))
  (reset! *ns-failed? false)
  (test/with-test-out
    (when (and (:idx m) (:count m))
      (print (str (inc (:idx m)) "/" (:count m) " ")))
    (print (str "Testing " (ns-name (:ns m))))
    (if (:capture-output? config)
      (do (print "...") (flush))
      (println))))

(defn- report-end-test-ns [m]
  (test/with-test-out
    (when (:capture-output? config)
      (if @*ns-failed?
        (println "Finished" (ns-name (:ns m)) "in" (- (System/currentTimeMillis) @*time-ns) "ms")
        (println "" (- (System/currentTimeMillis) @*time-ns) "ms")))))

(defn report-begin-test-var [m]
  (when (:capture-output? config)
    (capture-output)))

(defn- report-end-test-var [m]
  (when (:capture-output? config)
    (when @*ns-failed?
      (flush-output))
    (restore-output)))

(defn- testing-vars-str [m]
  (let [{:keys [file line]} m
        vars (reverse test/*testing-vars*)
        var  (first test/*testing-vars*)
        ns   (:ns (meta var))]
    (str ns "/" (str/join " " (map #(:name (meta %)) vars)) " (" file ":" line ")")))

(defn- print-testing-contexts []
  (loop [[tc & tcs] (reverse test/*testing-contexts*)
         indent     ""]
    (if tc
      (do
        (println (str indent "└╴" tc))
        (recur tcs (str indent "  ")))
      indent)))

(defn- report-pass [m]
  (test/with-test-out
    (test/inc-report-counter :pass)))

(defn- report-fail [m]
  (test/inc-report-counter :fail)
  (test/with-test-out
    (println "FAIL in" (testing-vars-str m))
    (let [indent (print-testing-contexts)
          {:keys [expected actual missing extra]} m]
      (when-some [message (:message m)]
        (println (str indent "├╴message: ") message))
      (when-some [form (:form m)]
        (println (str indent "├╴form:    ") (pr-str form)))
      (println (str indent "├╴expected:") (pr-str expected))
      (println (str indent (if (or missing extra) "├╴" "└╴") "actual:  ") (pr-str actual))
      (when missing
        (println (str indent (if extra "├╴" "└╴") "missing: ") (pr-str missing)))
      (when extra
        (println (str indent "└╴extra:   ") (pr-str extra)))))
  (flush-output)
  (reset! *ns-failed? true))

(defn- trace-transform [trace]
  (take-while
    (fn [{:keys [ns method file]}]
      (not= ["clojure.test" "test-var/fn" "test.clj"] [ns method file]))
    trace))

(defn- report-error [m]
  (test/inc-report-counter :error)
  (test/with-test-out
    (println "ERROR in" (testing-vars-str m))
    (let [indent (print-testing-contexts)]
      (if (and (= "Uncaught exception, not in assertion." (:message m)) (nil? (:expected m)))
        (binding [error/*trace-transform* trace-transform]
          (println (str indent "└╴uncaught:"))
          (println (:actual m)))
        (do
          (when-some [message (:message m)]
            (println (str indent "├╴message:") message))
          (println (str indent "├╴expected:") (pr-str (:expected m)))
          (binding [error/*trace-transform* trace-transform]
            (println (str indent "└╴actual:"))
            (println (:actual m)))))))
  (flush-output)
  (reset! *ns-failed? true))

(defn- report-summary [m]
  (test/with-test-out
    (println "╶───╴")
    (println "Ran" (:test m) "tests containing"
      (+ (:pass m) (:fail m) (:error m)) "assertions"
      "in" (- (System/currentTimeMillis) @*time-total) "ms.")
    (println (:fail m) "failures," (:error m) "errors.")
    (reset! *time-total nil)))

(defn- assert-expr-= [msg form]
  (if (= 3 (count form))
    `(let [expected# ~(nth form 1)
           actual#   ~(nth form 2)
           [missing# extra# common#] (data/diff expected# actual#)
           result#   (= expected# actual#)]
       (test/do-report
         (cond->
           {:type     (if result# :pass :fail)
            :message  ~msg
            :form     '~form
            :expected expected#
            :actual   actual#}
           (and common# missing#) (assoc :missing missing#)
           (and common# extra#) (assoc :extra extra#)))
       result#)
    `(let [actual# [~@(next form)]
           result# (apply = actual#)]
       (test/do-report
         {:type     (if result# :pass :fail)
          :message  ~msg
          :expected '~form
          :actual   (list* '~'not= actual#)})
       result#)))

(defn- assert-expr-not= [msg form]
  (if (= 3 (count form))
    `(let [expected# ~(nth form 1)
           actual#   ~(nth form 2)
           result#   (not= expected# actual#)]
       (test/do-report 
         {:type     (if result# :pass :fail)
          :message  ~msg
          :form     '~form
          :expected expected#
          :actual   actual#})
       result#)
    `(let [actual# [~@(next form)]
           result# (apply not= actual#)]
       (test/do-report
         {:type     (if result# :pass :fail)
          :message  ~msg
          :expected '~form
          :actual   (list* '~'= actual#)})
       result#)))

(defn- assert-expr-not [msg form]
  `(let [expected# ~form
         actual#   ~(nth form 1)
         result#   (= expected# actual#)]
     (test/do-report
       {:type     (if result# :pass :fail)
        :message  ~msg
        :expected '~form
        :actual   actual#})
     result#))

(def ^:private patched-methods-report
  {:begin-test-ns  #'report-begin-test-ns
   :end-test-ns    #'report-end-test-ns
   :begin-test-var #'report-begin-test-var
   :end-test-var   #'report-end-test-var
   :pass           #'report-pass
   :fail           #'report-fail
   :error          #'report-error
   :summary        #'report-summary})

(def ^:private clojure-methods-report
  (into {}
    (for [[k _] patched-methods-report]
      [k (get-method test/report k)])))

(defn install!
  "Improves output of clojure.test. Possible options:
   
     :capture-output?  <bool> :: Whether output of successful tests should be
                                 silenced. True by default."
  ([]
   (install! {}))
  ([opts]
   (.doReset #'config (merge (default-config) opts))
   (doseq [[k m] patched-methods-report]
     (MultiFn/.addMethod test/report k @m))
   (MultiFn/.addMethod test/assert-expr '= assert-expr-=)
   (MultiFn/.addMethod test/assert-expr 'not= assert-expr-not=)
   (MultiFn/.addMethod test/assert-expr 'not assert-expr-not)))

(defn uninstall!
  "Restore default clojure.test behaviour"
  []
  (doseq [[k m] clojure-methods-report]
    (MultiFn/.addMethod test/report k m))
  (MultiFn/.removeMethod test/assert-expr '=)
  (MultiFn/.removeMethod test/assert-expr 'not=))

(defn run
  "Universal test runner that accepts everything: namespaces, vars, symbols,
   regexps. Replaces run-tests, run-all-tests, run-test-var, run-test.
   If invoked with no arguments, runs all tests."
  [& args]
  (let [vars (for [arg       (if (empty? args) (all-ns) args)
                   var-or-ns (cond
                               (symbol? arg)
                               [(or
                                  (resolve arg)
                                  (the-ns arg)
                                  (throw (ex-info (str "Can’t resolve symbol " arg) {:symbol arg})))]

                               (instance? Namespace arg)
                               [arg]

                               (instance? Var arg)
                               [arg]

                               (instance? Pattern arg)
                               (filter #(re-matches arg (name (ns-name %))) (all-ns)))
                   var       (if (instance? Namespace var-or-ns)
                               (vals (ns-interns var-or-ns))
                               [var-or-ns])
                   :when     (:test (meta var))]
               var)
        ns+vars (->> vars
                  (group-by #(:ns (meta %)))
                  (sort-by (fn [[ns _]] (name (ns-name ns)))))]
    (binding [test/*report-counters* (ref test/*initial-report-counters*)]
      (doseq [[idx [ns vars]] (map vector (range) ns+vars)]
        (test/do-report {:type :begin-test-ns, :ns ns, :idx idx, :count (count ns+vars)})
        (try
          (let [once-fixture-fn (test/join-fixtures (::once-fixtures (meta ns)))
                each-fixture-fn (test/join-fixtures (::each-fixtures (meta ns)))]
            (once-fixture-fn
              (fn []
                (doseq [v (->> vars
                            (sort-by #(name (:name (meta %)))))]
                  (try
                    (each-fixture-fn
                      (fn []
                        (test/test-var v)))
                    (catch Throwable t
                      (test/do-report {:type    :error
                                       :message "Uncaught exception, not in assertion."
                                       :actual  t})))))))
          (catch Throwable t
            (test/do-report {:type    :error
                             :message "Uncaught exception, not in assertion."
                             :actual  t})))
        (test/do-report {:type :end-test-ns, :ns ns, :idx idx, :count (count ns+vars)}))
      (test/do-report (assoc @test/*report-counters* :type :summary))
      @test/*report-counters*)))
