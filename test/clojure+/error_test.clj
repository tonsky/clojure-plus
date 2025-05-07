(ns clojure+.error-test
  (:require
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
  ... 4 common elements
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
  ... 7 common elements
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
  \033[37m... 3 common elements\033[0m
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
   \033[37m... 3 common elements\033[0m
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
  [...common-elements 4]]
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
 [[...common-elements 7]]
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
  [...common-elements 3]]
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
 [[...common-elements 2]
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
