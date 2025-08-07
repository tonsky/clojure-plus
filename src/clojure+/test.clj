(ns clojure+.test
  (:require
   [clojure.data :as data]
   [clojure.java.io :as io]
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

(def ^:private ^:dynamic ^ByteArrayOutputStream *buffer*)

(def ^:private *time-ns
  (atom nil))

(def ^:private *time-total
  (atom nil))

(def ^:private *ns-failed?
  (atom false))

(defn- default-config []
  {:capture-output? true})

(def ^:dynamic config
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
  (.reset *buffer*))

(defn- restore-output []
  (pop-thread-bindings)
  (System/setErr system-err)
  (System/setOut system-out)
  (Var/.doReset #'clojure-test-out nil)
  (Var/.doReset #'system-err nil)
  (Var/.doReset #'system-out nil))

(defn- inc-report-counter [name]
  (condp instance? test/*report-counters*
    clojure.lang.Ref
    (dosync (commute test/*report-counters* update name (fnil inc 0)))

    clojure.lang.Atom
    (swap! test/*report-counters* update name (fnil inc 0))

    :noop))

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
    (inc-report-counter :pass)))

(defn- file-and-line
  "Finds line inside test that actually threw the exception. Like

     (defn f []
       (throw (Exception.)))

     (deftest test
       ((fn [x]
          (let [y 2]
            (f))) 1)) ;; <-- finds this line"
  [stacktrace]
  (let [test-var (last test/*testing-vars*)
        file     (some-> (meta test-var) :file io/file .getName)
        line     (some-> (meta test-var) :line)]
    (when (and file line)
      (when-some [trace (->> stacktrace
                          reverse
                          (drop-while
                            (fn [^StackTraceElement el]
                              (not=
                                [file line]
                                [(.getFileName el) (.getLineNumber el)])))
                          not-empty)]
        (let [cls (.getClassName ^StackTraceElement (first trace))]
          (when-some [inside-trace (->> trace
                                     (take-while
                                       (fn [^StackTraceElement el]
                                         (str/starts-with? (.getClassName el) cls)))
                                     not-empty)]
            (let [el ^StackTraceElement (last inside-trace)]
              {:file (.getFileName el)
               :line (.getLineNumber el)})))))))

(defn- report-fail [m]
  (inc-report-counter :fail)
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
  (when (:capture-output? config)
    (flush-output))
  (reset! *ns-failed? true))

(defn- trace-transform [trace]
  (take-while
    (fn [{:keys [ns method file]}]
      (not= ["clojure.test" "test-var/fn" "test.clj"] [ns method file]))
    trace))

(defn- report-error [m]
  (when (instance? InterruptedException (:actual m))
    (throw (:actual m)))
  (inc-report-counter :error)
  (test/with-test-out
    (println "ERROR in" (testing-vars-str (merge m
                                            (some-> (:actual m) (#(.getStackTrace ^Throwable %)) file-and-line))))
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
  (when (:capture-output? config)
    (flush-output))
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
  `(let [actual#   ~(nth form 1)
         result#   (not actual#)]
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
     (.addMethod ^MultiFn test/report k @m))
   (.addMethod ^MultiFn test/assert-expr '= assert-expr-=)
   (.addMethod ^MultiFn test/assert-expr 'not= assert-expr-not=)
   (.addMethod ^MultiFn test/assert-expr 'not assert-expr-not)))

(defn uninstall!
  "Restore default clojure.test behaviour"
  []
  (doseq [[k m] clojure-methods-report]
    (.addMethod ^MultiFn test/report k m))
  (.removeMethod ^MultiFn test/assert-expr '=)
  (.removeMethod ^MultiFn test/assert-expr 'not=)
  (.removeMethod ^MultiFn test/assert-expr 'not))

(defn installed? []
  (= report-summary (.getMethod ^MultiFn test/report :summary)))

(defn- ns-keyfn [ns]
  (fn [[ns _]] (name (ns-name ns))))

(defn- var-keyfn [var]
  [(:line (meta var))
   (name (:name (meta var)))])

(defn- test-var [v]
  (when-let [t (:test (meta v))]
    (binding [test/*testing-vars* (conj test/*testing-vars* v)]
      (test/do-report {:type :begin-test-var, :var v})
      (inc-report-counter :test)
      (try
        (t)
        (catch Throwable e
          (test/do-report {:type :error, :message "Uncaught exception, not in assertion."
                           :expected nil, :actual e})))
      (test/do-report {:type :end-test-var, :var v}))))

(defn run
  "Universal test runner that accepts everything: namespaces, vars, symbols,
   regexps. Replaces run-tests, run-all-tests, run-test-var, run-test.

   First argument can be a map of options:

     :randomize?       <bool> :: Whether to randomize test order or not.
                                 True by deafult.
     :capture-output?  <bool> :: Whether output of successful tests should be
                                 silenced. Overrides :capture-output? from install!

   Supports focusing tests: add ^:only to deftest var and only this test will run.

   If invoked with no arguments, runs all tests."
  [& args]
  (let [[opts args] (if (map? (first args))
                      [(first args) (next args)]
                      [{} args])
        {:keys [randomize? capture-output?]
         :or {randomize? true}} opts
        vars (for [arg       (if (empty? args) (all-ns) args)
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
        focused-vars (filter #(:only (meta %)) vars)
        vars         (or (not-empty focused-vars) vars)
        ns+vars      (group-by #(:ns (meta %)) vars)
        ns+vars      (if randomize?
                       (shuffle (or (seq ns+vars) ()))
                       (sort-by ns-keyfn ns+vars))]
    (binding [config                 (cond-> config
                                       (some? capture-output?) (assoc :capture-output? capture-output?))
              test/*report-counters* ((if (installed?) atom ref) test/*initial-report-counters*)]
      (try
        (doseq [[idx [ns vars]] (map vector (range) ns+vars)]
          (test/do-report {:type :begin-test-ns, :ns ns, :idx idx, :count (count ns+vars)})
          (try
            (let [once-fixture-fn (test/join-fixtures (::test/once-fixtures (meta ns)))
                  each-fixture-fn (test/join-fixtures (::test/each-fixtures (meta ns)))]
              (once-fixture-fn
                (fn []
                  (doseq [v (if randomize?
                              (shuffle vars)
                              (sort-by var-keyfn vars))]
                    (try
                      (each-fixture-fn
                        (fn []
                          (when (and (installed?) (.isInterrupted (Thread/currentThread)))
                            (throw (InterruptedException.)))
                          (test-var v)))
                      (catch Throwable t
                        (test/do-report {:type    :error
                                         :message "Uncaught exception, not in assertion."
                                         :actual  t})))))))
            (catch Throwable t
              (test/do-report {:type    :error
                               :message "Uncaught exception, not in assertion."
                               :actual  t}))
            (finally
              (test/do-report {:type :end-test-ns, :ns ns, :idx idx, :count (count ns+vars)}))))
        (finally
          (test/do-report (assoc @test/*report-counters* :type :summary))))
      @test/*report-counters*)))
