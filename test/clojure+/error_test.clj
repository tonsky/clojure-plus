(ns clojure+.error-test
  (:require
   [clojure.repl :as repl]
   [clojure.string :as str]
   [clojure.test :as test :refer [are deftest is testing use-fixtures]]
   [clojure+.error :as error])
  (:import
   [clojure.lang ExceptionInfo]))

(use-fixtures :each
  (fn [f]
    (f)
    (error/uninstall!)))

(def ^Exception cause
  (Exception. "Cause" nil))

(.setStackTrace cause
  (into-array 
    [(StackTraceElement. "clojure_sublimed.core$track_vars_STAR_" "invokeStatic" "NO_SOURCE_FILE" 170)
     (StackTraceElement. "clojure_sublimed.core$track_vars_STAR_" "invoke" "NO_SOURCE_FILE" 163)
     (StackTraceElement. "clojure_sublimed.socket_repl$fork_eval$fn__4492" "invoke" "NO_SOURCE_FILE" 330)
     (StackTraceElement. "clojure.core$binding_conveyor_fn$fn__5842" "invoke" "core.clj" 2047)
     (StackTraceElement. "clojure.lang.AFn" "call" "AFn.java" 18)
     (StackTraceElement. "java.util.concurrent.ThreadPoolExecutor$Worker" "run" "ThreadPoolExecutor.java" 642)
     (StackTraceElement. "java.lang.Thread" "run" "Thread.java" 1575)]))

(def ^ExceptionInfo effect
  (ExceptionInfo. "Effect of \"Cause\"" {:a 1, :b "a \"string\""} cause))

(.setStackTrace effect
  (into-array 
    [(StackTraceElement. "user$eval10830" "invokeStatic" nil 0)
     (StackTraceElement. "user$eval10830" "invoke" nil 0)
     (StackTraceElement. "clojure.core$binding_conveyor_fn$fn__5842" "invoke" "core.clj" 2047)
     (StackTraceElement. "clojure.lang.AFn" "call" "AFn.java" 18)
     (StackTraceElement. "java.util.concurrent.ThreadPoolExecutor$Worker" "run" "ThreadPoolExecutor.java" 642)
     (StackTraceElement. "java.lang.Thread" "run" "Thread.java" 1575)]))

(def ^ExceptionInfo effect-2
  (ExceptionInfo. "Effect 2" {} cause))

(.setStackTrace effect-2
  (.getStackTrace cause))

(defn trace-transform [trace]
  (remove #(= "ThreadPoolExecutor.java" (:file %)) trace))

(defn exception-with-trace [frames]
  (doto (Exception. "Oops" nil)
    (.setStackTrace (into-array StackTraceElement frames))))

(deftest trace-transform-omissions-test
  (let [e (exception-with-trace
            (concat
              [(StackTraceElement. "example$fail" "invokeStatic" "example.clj" 10)]
              (repeat 10 (StackTraceElement. "java.lang.Thread" "run" "Thread.java" 20))
              [(StackTraceElement. "example$caller" "invokeStatic" "example.clj" 30)]))]
    (doseq [[marker message] [['...common-frames "... 10 common frames"]
                              ['...REPL-frames "... 10 REPL frames omitted"]
                              ['...test-frames "... 10 test frames omitted"]
                              ['...custom-frames "... 10 custom frames omitted"]]
            reverse? [false true]
            dynamic? [false true]]
      (let [transform (fn [trace]
                        [(first trace) [marker 10] (last trace)])
            humanly ["example/fail    example.clj 10"
                     message
                     "example/caller  example.clj 30"]
            readably ["[example/fail    \"example.clj\" 10]"
                      (pr-str [marker 10])
                      "[example/caller  \"example.clj\" 30]"]]
        (error/install! {:color? false
                         :reverse? reverse?
                         :trace-transform (when-not dynamic? transform)})
        (binding [error/*trace-transform* (when dynamic? transform)]
          (is (= (if reverse?
                   (str "\n  " (str/join "\n  " (reverse humanly)) "\nException: Oops")
                   (str "Exception: Oops\n  " (str/join "\n  " humanly)))
                (with-out-str (print e))))
          (is (= (if reverse?
                   (str "\n#error {\n :trace\n [" (str/join "\n  " (reverse readably))
                     "]\n :message \"Oops\"\n :class   java.lang.Exception}")
                   (str "\n#error {\n :class   java.lang.Exception\n :message \"Oops\"\n :trace\n ["
                     (str/join "\n  " readably) "]}"))
                (with-out-str (pr e)))))))))

(def repl-trace
  [(StackTraceElement. "clojure.lang.Numbers" "divide" "Numbers.java" 190)
   (StackTraceElement. "example$eval12" "invokeStatic" "NO_SOURCE_FILE" 1)
   (StackTraceElement. "example$eval12" "invoke" "NO_SOURCE_FILE" 1)
   (StackTraceElement. "clojure.lang.Compiler" "eval" "Compiler.java" 7700)
   (StackTraceElement. "clojure.core$eval" "invokeStatic" "core.clj" 3232)
   (StackTraceElement. "clojure.core$eval" "invoke" "core.clj" 3228)
   (StackTraceElement. "clojure.main$repl$read_eval_print__9244" "invoke" "main.clj" 437)
   (StackTraceElement. "java.lang.Thread" "run" "Thread.java" 1575)])

(deftest collapse-repl-test
  (doseq [clean? [false true]
          file ["NO_SOURCE_FILE" "example.clj" nil]]
    (error/install! {:clean? clean? :color? false})
    (let [frames (-> repl-trace
                  (assoc 1 (StackTraceElement. "example$eval12" "invokeStatic" file 1))
                  (assoc 2 (StackTraceElement. "example$eval12" "invoke" file 1)))
          e (exception-with-trace frames)
          trace (:trace (error/datafy-throwable e))]
      (is (= (if clean? 3 4) (count trace)))
      (is (= (if clean? ['...REPL-frames 4] ['...REPL-frames 5]) (last trace)))
      (is (= (take (dec (count trace)) frames) (map :element (butlast trace))))
      (is (str/includes? (with-out-str (print e))
            (if clean? "... 4 REPL frames omitted" "... 5 REPL frames omitted")))
      (is (str/includes? (with-out-str (pr e))
            (if clean? "[...REPL-frames 4]" "[...REPL-frames 5]")))))
  (testing "can be disabled"
    (error/install! {:collapse-repl? false :clean? false})
    (is (= repl-trace (map :element (:trace (error/datafy-throwable (exception-with-trace repl-trace)))))))
  (testing "an eval wrapper alone does not trigger elision"
    (error/install! {:clean? false})
    (let [frames (take 3 repl-trace)]
      (is (= frames (map :element (:trace (error/datafy-throwable (exception-with-trace frames))))))))
  (testing "hooks receive and can retain the REPL marker"
    (error/install! {:trace-transform #(cons ['...custom-frames 2] %)})
    (is (= [['...custom-frames 2] ['...REPL-frames 4]]
          (filter vector? (:trace (error/datafy-throwable (exception-with-trace repl-trace))))))))

(deftest collapse-repl-boundary-test
  (let [compiler (StackTraceElement. "clojure.lang.Compiler" "eval" "Compiler.java" 7700)
        core-eval [(StackTraceElement. "clojure.core$eval" "invokeStatic" "core.clj" 3232)
                   (StackTraceElement. "clojure.core$eval" "invoke" "core.clj" 3228)]
        application (StackTraceElement. "example$programmatic_eval" "invokeStatic" "example.clj" 10)]
    (doseq [clean? [false true]
            compiler-count [1 2]
            core-eval? [false true]
            repl-class ["clojure.main$repl$read_eval_print__1234"
                        "rebel_readline.clojure.main$repl_STAR_$fn__1234"
                        "nrepl.middleware.interruptible_eval$evaluator$run__1234"
                        "clojure_sublimed.socket_repl$eval_code$fn__1234"
                        "clojure.core.server$prepl$fn__1234"]]
      (error/install! {:clean? clean?})
      (let [repl (StackTraceElement. repl-class "invoke" "repl.clj" 20)
            tail (concat (repeat compiler-count compiler)
                   (when core-eval? core-eval)
                   [repl (last repl-trace)])
            frames (cons application tail)
            trace (:trace (error/datafy-throwable (exception-with-trace frames)))
            n (+ compiler-count (if core-eval? (if clean? 1 2) 0) 2)]
        (is (= [application] (map :element (butlast trace))))
        (is (= ['...REPL-frames n] (last trace)))))
    (testing "programmatic eval callers are preserved"
      (error/install! {:clean? false})
      (let [frames (vec (concat (take 3 repl-trace) [compiler] core-eval [application]))
            trace (:trace (error/datafy-throwable (exception-with-trace frames)))]
        (is (= frames (map :element trace)))
        (testing "even when a later REPL boundary exists"
          (let [outer [(StackTraceElement. "clojure.lang.Compiler" "eval" "Compiler.java" 7655)
                       (StackTraceElement. "clojure.main$repl" "invokeStatic" "main.clj" 437)
                       (last repl-trace)]
                trace (:trace (error/datafy-throwable (exception-with-trace (concat frames outer))))]
            (is (= frames (map :element (butlast trace))))
            (is (= ['...REPL-frames 3] (last trace)))))))
    (testing "compiler eval needs an immediately following REPL evaluator"
      (doseq [frames [[compiler]
                      (into [compiler] core-eval)
                      [compiler application (nth repl-trace 6)]
                      [(StackTraceElement. "clojure.lang.Compiler" "load" "Compiler.java" 1)
                       (nth repl-trace 6)]]]
        (is (= frames (map :element (:trace (error/datafy-throwable (exception-with-trace frames))))))))))

(deftest omission-common-tail-test
  (testing "identical markers do not prove the omitted frames are common"
    (error/install! {:trace-transform (constantly [['...test-frames 10]])})
    (is (zero? (:common (error/datafy-throwable effect)))))
  (testing "common frames after a marker still collapse"
    (error/install! {:color? false
                     :trace-transform #(cons ['...custom-frames 10] (take-last 2 %))})
    (is (= 2 (:common (error/datafy-throwable effect))))
    (is (str/includes? (with-out-str (print effect))
          "... 10 custom frames omitted\n  ... 2 common frames"))
    (is (str/includes? (with-out-str (pr effect))
          "[...custom-frames 10]\n  [...common-frames 2]"))))

(deftest omission-only-test
  (let [e (exception-with-trace [])]
    (error/install! {:color? true
                     :trace-transform (constantly [['...REPL-frames 10]])})
    (is (= "\033[31mException:\033[0m Oops\n  \033[37m... 10 REPL frames omitted\033[0m"
          (with-out-str (print e))))
    (binding [*print-length* 1
              *print-level* 1]
      (is (= "\n#error {\n :class   java.lang.Exception\n :message \"Oops\"\n :trace\n [[...REPL-frames 10]]}"
            (with-out-str (pr e)))))))

(defmacro with-err-str [& body]
  `(let [w# (java.io.StringWriter.)]
     (binding [*err* w#]
       ~@body)
     (str w#)))

(deftest humanly-nothing-test
  (error/install! {:color?           false
                   :reverse?         false
                   :clean?           false
                   :trace-transform  nil
                   :collapse-common? false
                   :root-cause-only? false
                   :indent           2})
  (is (= "ExceptionInfo: Effect of \"Cause\" {:a 1, :b a \"string\"}
  user$eval10830.invokeStatic
  user$eval10830.invoke
  clojure.core$binding_conveyor_fn$fn__5842.invoke        core.clj 2047
  clojure.lang.AFn.call                                   AFn.java 18
  java.util.concurrent.ThreadPoolExecutor$Worker.run      ThreadPoolExecutor.java 642
  java.lang.Thread.run                                    Thread.java 1575
Caused by: Exception: Cause
  clojure_sublimed.core$track_vars_STAR_.invokeStatic
  clojure_sublimed.core$track_vars_STAR_.invoke
  clojure_sublimed.socket_repl$fork_eval$fn__4492.invoke
  clojure.core$binding_conveyor_fn$fn__5842.invoke        core.clj 2047
  clojure.lang.AFn.call                                   AFn.java 18
  java.util.concurrent.ThreadPoolExecutor$Worker.run      ThreadPoolExecutor.java 642
  java.lang.Thread.run                                    Thread.java 1575"
        (with-out-str (print effect)))))

(deftest humanly-color-test
  (error/install! {:color?           true
                   :reverse?         false
                   :clean?           false
                   :trace-transform  nil
                   :collapse-common? false
                   :root-cause-only? false
                   :indent           2})
  (is (= "\033[31mExceptionInfo:\033[0m Effect of \"Cause\" \033[37m{:a 1, :b a \"string\"}\033[0m
  \033[37muser$eval10830.\033[0minvokeStatic
  \033[37muser$eval10830.\033[0minvoke
  \033[37mclojure.core$binding_conveyor_fn$fn__5842.\033[0minvoke        core.clj \033[37m2047\033[0m
  \033[37mclojure.lang.AFn.\033[0mcall                                   AFn.java \033[37m18\033[0m
  \033[37mjava.util.concurrent.ThreadPoolExecutor$Worker.\033[0mrun      ThreadPoolExecutor.java \033[37m642\033[0m
  \033[37mjava.lang.Thread.\033[0mrun                                    Thread.java \033[37m1575\033[0m
Caused by: \033[31mException:\033[0m Cause
  \033[37mclojure_sublimed.core$track_vars_STAR_.\033[0minvokeStatic
  \033[37mclojure_sublimed.core$track_vars_STAR_.\033[0minvoke
  \033[37mclojure_sublimed.socket_repl$fork_eval$fn__4492.\033[0minvoke
  \033[37mclojure.core$binding_conveyor_fn$fn__5842.\033[0minvoke        core.clj \033[37m2047\033[0m
  \033[37mclojure.lang.AFn.\033[0mcall                                   AFn.java \033[37m18\033[0m
  \033[37mjava.util.concurrent.ThreadPoolExecutor$Worker.\033[0mrun      ThreadPoolExecutor.java \033[37m642\033[0m
  \033[37mjava.lang.Thread.\033[0mrun                                    Thread.java \033[37m1575\033[0m"
        (with-out-str (print effect)))))

(deftest humanly-reverse-test
  (error/install! {:color?           false
                   :reverse?         true
                   :clean?           false
                   :trace-transform  nil
                   :collapse-common? false
                   :root-cause-only? false
                   :indent           2})
  (is (= "
  java.lang.Thread.run                                    Thread.java 1575
  java.util.concurrent.ThreadPoolExecutor$Worker.run      ThreadPoolExecutor.java 642
  clojure.lang.AFn.call                                   AFn.java 18
  clojure.core$binding_conveyor_fn$fn__5842.invoke        core.clj 2047
  clojure_sublimed.socket_repl$fork_eval$fn__4492.invoke
  clojure_sublimed.core$track_vars_STAR_.invoke
  clojure_sublimed.core$track_vars_STAR_.invokeStatic
Caused by: Exception: Cause
  java.lang.Thread.run                                    Thread.java 1575
  java.util.concurrent.ThreadPoolExecutor$Worker.run      ThreadPoolExecutor.java 642
  clojure.lang.AFn.call                                   AFn.java 18
  clojure.core$binding_conveyor_fn$fn__5842.invoke        core.clj 2047
  user$eval10830.invoke
  user$eval10830.invokeStatic
ExceptionInfo: Effect of \"Cause\" {:a 1, :b a \"string\"}"
        (with-out-str (print effect)))))

(deftest humanly-clean-test
  (error/install! {:color?           false
                   :reverse?         false
                   :clean?           true
                   :trace-transform  nil
                   :collapse-common? false
                   :root-cause-only? false
                   :indent           2})
  (is (= "ExceptionInfo: Effect of \"Cause\" {:a 1, :b a \"string\"}
  user/eval
  clojure.core/binding-conveyor-fn/fn        core.clj 2047
  ThreadPoolExecutor$Worker.run              ThreadPoolExecutor.java 642
  Thread.run                                 Thread.java 1575
Caused by: Exception: Cause
  clojure-sublimed.core/track-vars*
  clojure-sublimed.socket-repl/fork-eval/fn
  clojure.core/binding-conveyor-fn/fn        core.clj 2047
  ThreadPoolExecutor$Worker.run              ThreadPoolExecutor.java 642
  Thread.run                                 Thread.java 1575"
        (with-out-str (print effect)))))

(deftest humanly-trace-transform-test
  (error/install! {:color?           false
                   :reverse?         false
                   :clean?           false
                   :trace-transform  trace-transform
                   :collapse-common? false
                   :root-cause-only? false
                   :indent           2})
  (is (= "ExceptionInfo: Effect of \"Cause\" {:a 1, :b a \"string\"}
  user$eval10830.invokeStatic
  user$eval10830.invoke
  clojure.core$binding_conveyor_fn$fn__5842.invoke        core.clj 2047
  clojure.lang.AFn.call                                   AFn.java 18
  java.lang.Thread.run                                    Thread.java 1575
Caused by: Exception: Cause
  clojure_sublimed.core$track_vars_STAR_.invokeStatic
  clojure_sublimed.core$track_vars_STAR_.invoke
  clojure_sublimed.socket_repl$fork_eval$fn__4492.invoke
  clojure.core$binding_conveyor_fn$fn__5842.invoke        core.clj 2047
  clojure.lang.AFn.call                                   AFn.java 18
  java.lang.Thread.run                                    Thread.java 1575"
        (with-out-str (print effect)))))

(deftest humanly-collapse-common-test
  (error/install! {:color?           false
                   :reverse?         false
                   :clean?           false
                   :trace-transform  nil
                   :collapse-common? true
                   :root-cause-only? false
                   :indent           2})
  (is (= "ExceptionInfo: Effect of \"Cause\" {:a 1, :b a \"string\"}
  user$eval10830.invokeStatic
  user$eval10830.invoke
  ... 4 common frames
Caused by: Exception: Cause
  clojure_sublimed.core$track_vars_STAR_.invokeStatic
  clojure_sublimed.core$track_vars_STAR_.invoke
  clojure_sublimed.socket_repl$fork_eval$fn__4492.invoke
  clojure.core$binding_conveyor_fn$fn__5842.invoke        core.clj 2047
  clojure.lang.AFn.call                                   AFn.java 18
  java.util.concurrent.ThreadPoolExecutor$Worker.run      ThreadPoolExecutor.java 642
  java.lang.Thread.run                                    Thread.java 1575"
        (with-out-str (print effect))))
  
  (is (= "ExceptionInfo: Effect 2 {}
  ... 7 common frames
Caused by: Exception: Cause
  clojure_sublimed.core$track_vars_STAR_.invokeStatic
  clojure_sublimed.core$track_vars_STAR_.invoke
  clojure_sublimed.socket_repl$fork_eval$fn__4492.invoke
  clojure.core$binding_conveyor_fn$fn__5842.invoke        core.clj 2047
  clojure.lang.AFn.call                                   AFn.java 18
  java.util.concurrent.ThreadPoolExecutor$Worker.run      ThreadPoolExecutor.java 642
  java.lang.Thread.run                                    Thread.java 1575"
        (with-out-str (print effect-2)))))

(deftest humanly-root-cause-only-test
  (error/install! {:color?           false
                   :reverse?         false
                   :clean?           false
                   :trace-transform  nil
                   :collapse-common? false
                   :root-cause-only? true
                   :indent           2})
  (is (= "Exception: Cause
  clojure_sublimed.core$track_vars_STAR_.invokeStatic
  clojure_sublimed.core$track_vars_STAR_.invoke
  clojure_sublimed.socket_repl$fork_eval$fn__4492.invoke
  clojure.core$binding_conveyor_fn$fn__5842.invoke        core.clj 2047
  clojure.lang.AFn.call                                   AFn.java 18
  java.util.concurrent.ThreadPoolExecutor$Worker.run      ThreadPoolExecutor.java 642
  java.lang.Thread.run                                    Thread.java 1575"
        (with-out-str (print effect))))
  
  (error/install! {:color?           false
                   :reverse?         false
                   :clean?           false
                   :trace-transform  nil
                   :collapse-common? false
                   :root-cause-only? false
                   :indent           2})
  (is (= "Exception: Cause
  clojure_sublimed.core$track_vars_STAR_.invokeStatic
  clojure_sublimed.core$track_vars_STAR_.invoke
  clojure_sublimed.socket_repl$fork_eval$fn__4492.invoke
  clojure.core$binding_conveyor_fn$fn__5842.invoke        core.clj 2047
  clojure.lang.AFn.call                                   AFn.java 18
  java.util.concurrent.ThreadPoolExecutor$Worker.run      ThreadPoolExecutor.java 642
  java.lang.Thread.run                                    Thread.java 1575"
        (with-out-str (print cause)))))

(deftest humanly-indent-test
  (error/install! {:color?           false
                   :reverse?         false
                   :clean?           false
                   :trace-transform  nil
                   :collapse-common? false
                   :root-cause-only? false
                   :indent           4})
  (is (= "ExceptionInfo: Effect of \"Cause\" {:a 1, :b a \"string\"}
    user$eval10830.invokeStatic
    user$eval10830.invoke
    clojure.core$binding_conveyor_fn$fn__5842.invoke        core.clj 2047
    clojure.lang.AFn.call                                   AFn.java 18
    java.util.concurrent.ThreadPoolExecutor$Worker.run      ThreadPoolExecutor.java 642
    java.lang.Thread.run                                    Thread.java 1575
Caused by: Exception: Cause
    clojure_sublimed.core$track_vars_STAR_.invokeStatic
    clojure_sublimed.core$track_vars_STAR_.invoke
    clojure_sublimed.socket_repl$fork_eval$fn__4492.invoke
    clojure.core$binding_conveyor_fn$fn__5842.invoke        core.clj 2047
    clojure.lang.AFn.call                                   AFn.java 18
    java.util.concurrent.ThreadPoolExecutor$Worker.run      ThreadPoolExecutor.java 642
    java.lang.Thread.run                                    Thread.java 1575"
        (with-out-str (print effect))))
  
  (error/install! {:color?           false
                   :reverse?         false
                   :clean?           false
                   :trace-transform  nil
                   :collapse-common? false
                   :root-cause-only? false
                   :indent           "\t"})
  (is (= "ExceptionInfo: Effect of \"Cause\" {:a 1, :b a \"string\"}
\tuser$eval10830.invokeStatic
\tuser$eval10830.invoke
\tclojure.core$binding_conveyor_fn$fn__5842.invoke        core.clj 2047
\tclojure.lang.AFn.call                                   AFn.java 18
\tjava.util.concurrent.ThreadPoolExecutor$Worker.run      ThreadPoolExecutor.java 642
\tjava.lang.Thread.run                                    Thread.java 1575
Caused by: Exception: Cause
\tclojure_sublimed.core$track_vars_STAR_.invokeStatic
\tclojure_sublimed.core$track_vars_STAR_.invoke
\tclojure_sublimed.socket_repl$fork_eval$fn__4492.invoke
\tclojure.core$binding_conveyor_fn$fn__5842.invoke        core.clj 2047
\tclojure.lang.AFn.call                                   AFn.java 18
\tjava.util.concurrent.ThreadPoolExecutor$Worker.run      ThreadPoolExecutor.java 642
\tjava.lang.Thread.run                                    Thread.java 1575"
        (with-out-str (print effect)))))

(deftest humanly-default-test  
  (error/install! {:color? true})
  (is (= "\033[31mExceptionInfo:\033[0m Effect of \"Cause\" \033[37m{:a 1, :b a \"string\"}\033[0m
  \033[37muser/\033[0meval
  \033[37m... 3 common frames\033[0m
Caused by: \033[31mException:\033[0m Cause
  \033[37mclojure-sublimed.core/\033[0mtrack-vars*
  \033[37mclojure-sublimed.socket-repl/\033[0mfork-eval/fn
  \033[37mclojure.core/\033[0mbinding-conveyor-fn/fn        core.clj \033[37m2047\033[0m
  \033[37mThreadPoolExecutor$Worker.\033[0mrun              ThreadPoolExecutor.java \033[37m642\033[0m
  \033[37mThread.\033[0mrun                                 Thread.java \033[37m1575\033[0m"
        (with-out-str (print effect)))))

(deftest humanly-default-nice-test
  (error/install! {:color?   true
                   :reverse? true
                   :indent   3})
  (is (= "
   \033[37mThread.\033[0mrun                                 Thread.java \033[37m1575\033[0m
   \033[37mThreadPoolExecutor$Worker.\033[0mrun              ThreadPoolExecutor.java \033[37m642\033[0m
   \033[37mclojure.core/\033[0mbinding-conveyor-fn/fn        core.clj \033[37m2047\033[0m
   \033[37mclojure-sublimed.socket-repl/\033[0mfork-eval/fn
   \033[37mclojure-sublimed.core/\033[0mtrack-vars*
Caused by: \033[31mException:\033[0m Cause
   \033[37m... 3 common frames\033[0m
   \033[37muser/\033[0meval
\033[31mExceptionInfo:\033[0m Effect of \"Cause\" \033[37m{:a 1, :b a \"string\"}\033[0m"
        (with-out-str (print effect)))))

(deftest humanly-everything-test
  (error/install! {:color?           true
                   :reverse?         true
                   :clean?           true
                   :trace-transform  trace-transform
                   :collapse-common? true
                   :root-cause-only? true
                   :indent           4})
  (is (= "
    \033[37mThread.\033[0mrun                                 Thread.java \033[37m1575\033[0m
    \033[37mclojure.core/\033[0mbinding-conveyor-fn/fn        core.clj \033[37m2047\033[0m
    \033[37mclojure-sublimed.socket-repl/\033[0mfork-eval/fn
    \033[37mclojure-sublimed.core/\033[0mtrack-vars*
\033[31mException:\033[0m Cause"
        (with-out-str (print effect)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; PRINT READABLY

(deftest readably-nothing-test
  (error/install! {:reverse?         false
                   :clean?           false
                   :trace-transform  nil
                   :collapse-common? false
                   :root-cause-only? false})
  (is (= "
#error {
 :class   clojure.lang.ExceptionInfo
 :message \"Effect of \\\"Cause\\\"\"
 :data    {:a 1, :b \"a \\\"string\\\"\"}
 :trace
 [[user$eval10830.invokeStatic]
  [user$eval10830.invoke]
  [clojure.core$binding_conveyor_fn$fn__5842.invoke         \"core.clj\" 2047]
  [clojure.lang.AFn.call                                    \"AFn.java\" 18]
  [java.util.concurrent.ThreadPoolExecutor$Worker.run       \"ThreadPoolExecutor.java\" 642]
  [java.lang.Thread.run                                     \"Thread.java\" 1575]]
 :cause
 #error {
  :class   java.lang.Exception
  :message \"Cause\"
  :trace
  [[clojure_sublimed.core$track_vars_STAR_.invokeStatic]
   [clojure_sublimed.core$track_vars_STAR_.invoke]
   [clojure_sublimed.socket_repl$fork_eval$fn__4492.invoke]
   [clojure.core$binding_conveyor_fn$fn__5842.invoke        \"core.clj\" 2047]
   [clojure.lang.AFn.call                                   \"AFn.java\" 18]
   [java.util.concurrent.ThreadPoolExecutor$Worker.run      \"ThreadPoolExecutor.java\" 642]
   [java.lang.Thread.run                                    \"Thread.java\" 1575]]}}"
        (with-out-str (pr effect)))))

(deftest readably-reverse-test
  (error/install! {:reverse?         true
                   :clean?           false
                   :trace-transform  nil
                   :collapse-common? false
                   :root-cause-only? false})
  (is (= "
#error {
 :cause
 #error {
  :trace
  [[java.lang.Thread.run                                    \"Thread.java\" 1575]
   [java.util.concurrent.ThreadPoolExecutor$Worker.run      \"ThreadPoolExecutor.java\" 642]
   [clojure.lang.AFn.call                                   \"AFn.java\" 18]
   [clojure.core$binding_conveyor_fn$fn__5842.invoke        \"core.clj\" 2047]
   [clojure_sublimed.socket_repl$fork_eval$fn__4492.invoke]
   [clojure_sublimed.core$track_vars_STAR_.invoke]
   [clojure_sublimed.core$track_vars_STAR_.invokeStatic]]
  :message \"Cause\"
  :class   java.lang.Exception}
 :trace
 [[java.lang.Thread.run                                     \"Thread.java\" 1575]
  [java.util.concurrent.ThreadPoolExecutor$Worker.run       \"ThreadPoolExecutor.java\" 642]
  [clojure.lang.AFn.call                                    \"AFn.java\" 18]
  [clojure.core$binding_conveyor_fn$fn__5842.invoke         \"core.clj\" 2047]
  [user$eval10830.invoke]
  [user$eval10830.invokeStatic]]
 :data    {:a 1, :b \"a \\\"string\\\"\"}
 :message \"Effect of \\\"Cause\\\"\"
 :class   clojure.lang.ExceptionInfo}"
        (with-out-str (pr effect)))))

(deftest readably-clean-test
  (error/install! {:reverse?         false
                   :clean?           true
                   :trace-transform  nil
                   :collapse-common? false
                   :root-cause-only? false})
  (is (= "
#error {
 :class   clojure.lang.ExceptionInfo
 :message \"Effect of \\\"Cause\\\"\"
 :data    {:a 1, :b \"a \\\"string\\\"\"}
 :trace
 [[user/eval]
  [clojure.core/binding-conveyor-fn/fn         \"core.clj\" 2047]
  [ThreadPoolExecutor$Worker.run               \"ThreadPoolExecutor.java\" 642]
  [Thread.run                                  \"Thread.java\" 1575]]
 :cause
 #error {
  :class   java.lang.Exception
  :message \"Cause\"
  :trace
  [[clojure-sublimed.core/track-vars*]
   [clojure-sublimed.socket-repl/fork-eval/fn]
   [clojure.core/binding-conveyor-fn/fn        \"core.clj\" 2047]
   [ThreadPoolExecutor$Worker.run              \"ThreadPoolExecutor.java\" 642]
   [Thread.run                                 \"Thread.java\" 1575]]}}"
        (with-out-str (pr effect)))))

(deftest readably-trace-transform-test
  (error/install! {:reverse?         false
                   :clean?           false
                   :trace-transform  trace-transform
                   :collapse-common? false
                   :root-cause-only? false})
  (is (= "
#error {
 :class   clojure.lang.ExceptionInfo
 :message \"Effect of \\\"Cause\\\"\"
 :data    {:a 1, :b \"a \\\"string\\\"\"}
 :trace
 [[user$eval10830.invokeStatic]
  [user$eval10830.invoke]
  [clojure.core$binding_conveyor_fn$fn__5842.invoke         \"core.clj\" 2047]
  [clojure.lang.AFn.call                                    \"AFn.java\" 18]
  [java.lang.Thread.run                                     \"Thread.java\" 1575]]
 :cause
 #error {
  :class   java.lang.Exception
  :message \"Cause\"
  :trace
  [[clojure_sublimed.core$track_vars_STAR_.invokeStatic]
   [clojure_sublimed.core$track_vars_STAR_.invoke]
   [clojure_sublimed.socket_repl$fork_eval$fn__4492.invoke]
   [clojure.core$binding_conveyor_fn$fn__5842.invoke        \"core.clj\" 2047]
   [clojure.lang.AFn.call                                   \"AFn.java\" 18]
   [java.lang.Thread.run                                    \"Thread.java\" 1575]]}}"
        (with-out-str (pr effect)))))

(deftest readably-collapse-common-test
  (error/install! {:reverse?         false
                   :clean?           false
                   :trace-transform  nil
                   :collapse-common? true
                   :root-cause-only? false})
  (is (= "
#error {
 :class   clojure.lang.ExceptionInfo
 :message \"Effect of \\\"Cause\\\"\"
 :data    {:a 1, :b \"a \\\"string\\\"\"}
 :trace
 [[user$eval10830.invokeStatic]
  [user$eval10830.invoke]
  [...common-frames 4]]
 :cause
 #error {
  :class   java.lang.Exception
  :message \"Cause\"
  :trace
  [[clojure_sublimed.core$track_vars_STAR_.invokeStatic]
   [clojure_sublimed.core$track_vars_STAR_.invoke]
   [clojure_sublimed.socket_repl$fork_eval$fn__4492.invoke]
   [clojure.core$binding_conveyor_fn$fn__5842.invoke        \"core.clj\" 2047]
   [clojure.lang.AFn.call                                   \"AFn.java\" 18]
   [java.util.concurrent.ThreadPoolExecutor$Worker.run      \"ThreadPoolExecutor.java\" 642]
   [java.lang.Thread.run                                    \"Thread.java\" 1575]]}}"
        (with-out-str (pr effect))))
  
  (is (= "
#error {
 :class   clojure.lang.ExceptionInfo
 :message \"Effect 2\"
 :data    {}
 :trace
 [[...common-frames 7]]
 :cause
 #error {
  :class   java.lang.Exception
  :message \"Cause\"
  :trace
  [[clojure_sublimed.core$track_vars_STAR_.invokeStatic]
   [clojure_sublimed.core$track_vars_STAR_.invoke]
   [clojure_sublimed.socket_repl$fork_eval$fn__4492.invoke]
   [clojure.core$binding_conveyor_fn$fn__5842.invoke        \"core.clj\" 2047]
   [clojure.lang.AFn.call                                   \"AFn.java\" 18]
   [java.util.concurrent.ThreadPoolExecutor$Worker.run      \"ThreadPoolExecutor.java\" 642]
   [java.lang.Thread.run                                    \"Thread.java\" 1575]]}}"
        (with-out-str (pr effect-2)))))

(deftest readably-root-cause-only-test
  (error/install! {:reverse?         false
                   :clean?           false
                   :trace-transform  nil
                   :collapse-common? false
                   :root-cause-only? true})
  (is (= "
#error {
 :class   java.lang.Exception
 :message \"Cause\"
 :trace
 [[clojure_sublimed.core$track_vars_STAR_.invokeStatic]
  [clojure_sublimed.core$track_vars_STAR_.invoke]
  [clojure_sublimed.socket_repl$fork_eval$fn__4492.invoke]
  [clojure.core$binding_conveyor_fn$fn__5842.invoke        \"core.clj\" 2047]
  [clojure.lang.AFn.call                                   \"AFn.java\" 18]
  [java.util.concurrent.ThreadPoolExecutor$Worker.run      \"ThreadPoolExecutor.java\" 642]
  [java.lang.Thread.run                                    \"Thread.java\" 1575]]}"
        (with-out-str (pr effect))))
  
  (is (= "
#error {
 :class   java.lang.Exception
 :message \"Cause\"
 :trace
 [[clojure_sublimed.core$track_vars_STAR_.invokeStatic]
  [clojure_sublimed.core$track_vars_STAR_.invoke]
  [clojure_sublimed.socket_repl$fork_eval$fn__4492.invoke]
  [clojure.core$binding_conveyor_fn$fn__5842.invoke        \"core.clj\" 2047]
  [clojure.lang.AFn.call                                   \"AFn.java\" 18]
  [java.util.concurrent.ThreadPoolExecutor$Worker.run      \"ThreadPoolExecutor.java\" 642]
  [java.lang.Thread.run                                    \"Thread.java\" 1575]]}"
        (with-out-str (pr cause)))))

(deftest readably-default-test
  (error/install!)
  (is (= "
#error {
 :class   clojure.lang.ExceptionInfo
 :message \"Effect of \\\"Cause\\\"\"
 :data    {:a 1, :b \"a \\\"string\\\"\"}
 :trace
 [[user/eval]
  [...common-frames 3]]
 :cause
 #error {
  :class   java.lang.Exception
  :message \"Cause\"
  :trace
  [[clojure-sublimed.core/track-vars*]
   [clojure-sublimed.socket-repl/fork-eval/fn]
   [clojure.core/binding-conveyor-fn/fn        \"core.clj\" 2047]
   [ThreadPoolExecutor$Worker.run              \"ThreadPoolExecutor.java\" 642]
   [Thread.run                                 \"Thread.java\" 1575]]}}"
        (with-out-str (pr effect)))))

(deftest readably-almost-everything-test
  (error/install! {:reverse?         true
                   :clean?           true
                   :trace-transform  trace-transform
                   :collapse-common? true
                   :root-cause-only? false})
  (is (= "
#error {
 :cause
 #error {
  :trace
  [[Thread.run                                 \"Thread.java\" 1575]
   [clojure.core/binding-conveyor-fn/fn        \"core.clj\" 2047]
   [clojure-sublimed.socket-repl/fork-eval/fn]
   [clojure-sublimed.core/track-vars*]]
  :message \"Cause\"
  :class   java.lang.Exception}
 :trace
 [[...common-frames 2]
  [user/eval]]
 :data    {:a 1, :b \"a \\\"string\\\"\"}
 :message \"Effect of \\\"Cause\\\"\"
 :class   clojure.lang.ExceptionInfo}"
        (with-out-str (pr effect)))))

(deftest readably-everything-test
  (error/install! {:reverse?         true
                   :clean?           true
                   :trace-transform  trace-transform
                   :collapse-common? true
                   :root-cause-only? true})
  (is (= "
#error {
 :trace
 [[Thread.run                                 \"Thread.java\" 1575]
  [clojure.core/binding-conveyor-fn/fn        \"core.clj\" 2047]
  [clojure-sublimed.socket-repl/fork-eval/fn]
  [clojure-sublimed.core/track-vars*]]
 :message \"Cause\"
 :class   java.lang.Exception}"
        (with-out-str (pr effect)))))

(deftest pst-test
  (error/install! {:color? false})
  (testing "exception"
    (is (= "ExceptionInfo: Effect of \"Cause\" {:a 1, :b a \"string\"}
  user/eval
  ... 3 common frames
Caused by: Exception: Cause
  clojure-sublimed.core/track-vars*
  clojure-sublimed.socket-repl/fork-eval/fn
  clojure.core/binding-conveyor-fn/fn        core.clj 2047
  ThreadPoolExecutor$Worker.run              ThreadPoolExecutor.java 642
  Thread.run                                 Thread.java 1575
"
          (with-err-str (repl/pst effect)))))

  (testing "exception + depth"
    (is (= "ExceptionInfo: Effect of \"Cause\" {:a 1, :b a \"string\"}
  user/eval
  ... 3 common frames
Caused by: Exception: Cause
  clojure-sublimed.core/track-vars*
  clojure-sublimed.socket-repl/fork-eval/fn
"
          (with-err-str (repl/pst effect 2)))))

  (testing "root cause of *e"
    (binding [*e effect]
      (is (= "Exception: Cause
  clojure-sublimed.core/track-vars*
  clojure-sublimed.socket-repl/fork-eval/fn
  clojure.core/binding-conveyor-fn/fn        core.clj 2047
  ThreadPoolExecutor$Worker.run              ThreadPoolExecutor.java 642
  Thread.run                                 Thread.java 1575
"
            (with-err-str (repl/pst))))
      (is (= "Exception: Cause
  clojure-sublimed.core/track-vars*
  clojure-sublimed.socket-repl/fork-eval/fn
"
            (with-err-str (repl/pst 2))))))

  (testing "no *e"
    (binding [*e nil]
      (is (= "" (with-err-str (repl/pst))))))

  (testing "config"
    (error/install! {:color? false, :reverse? true})
    (is (= "
  clojure-sublimed.socket-repl/fork-eval/fn
  clojure-sublimed.core/track-vars*
Caused by: Exception: Cause
  ... 3 common frames
  user/eval
ExceptionInfo: Effect of \"Cause\" {:a 1, :b a \"string\"}
"
          (with-err-str (repl/pst effect 2)))))

  (testing "uninstall!"
    (error/uninstall!)
    (is (= "Exception Cause\n" (with-err-str (repl/pst cause 0))))))
