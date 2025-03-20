(ns clojure+.test
  (:require
   [clojure.test :as test])
  (:import
   [clojure.lang MultiFn]
   [java.io ByteArrayOutputStream PrintStream OutputStreamWriter]))

(def system-out
  System/out)

(def system-err
  System/err)

(def out
  *out*)

(def *buffer
  (atom nil))

(defn capture-output []
  (let [buffer (ByteArrayOutputStream.)
        _      (reset! *buffer buffer)
        ps     (PrintStream. buffer)
        out    (OutputStreamWriter. buffer)]
    (System/setOut ps)
    (System/setErr ps)
    (push-thread-bindings {#'*out* out
                           #'*err* out})))

(defn restore-output []
  (System/setOut system-out)
  (System/setErr system-err)
  (pop-thread-bindings))

(defn reprint-output []
  (print (str @*buffer))
  (flush))

(defn report-begin-test-ns [m]
  (test/with-test-out
    (println "Testing" (ns-name (:ns m)))))

(defn report-begin-test-var [m]
  (capture-output))

(defn report-end-test-var [m]
  (restore-output))

(defn report-pass [m]
  (test/with-test-out
    (test/inc-report-counter :pass)))

(defn report-fail [m]
  (test/with-test-out
    (test/inc-report-counter :fail)
    (reprint-output)
    (println "FAIL in" (test/testing-vars-str m))
    (when (seq test/*testing-contexts*)
      (println (test/testing-contexts-str)))
    (when-some [message (:message m)]
      (println message))
    (println "expected:" (pr-str (:expected m)))
    (println "  actual:" (pr-str (:actual m)))))

(defn report-error [m]
  (test/with-test-out
    (test/inc-report-counter :error)
    (reprint-output)
    (println "ERROR in" (test/testing-vars-str m))
    (when (seq test/*testing-contexts*)
      (println (test/testing-contexts-str)))
    (when-some [message (:message m)]
      (println message))
    (println "expected:" (pr-str (:expected m)))
    (print "  actual: ")
    (let [actual (:actual m)]
      (if (instance? Throwable actual)
        (println actual)
        (prn actual)))))

(def patched-methods
  {:begin-test-ns  report-begin-test-ns
   :begin-test-var report-begin-test-var
   :end-test-var   report-end-test-var
   :pass           report-pass
   :fail           report-fail
   :error          report-error})

(def clojure-methods
  (into {}
    (for [[k _] patched-methods]
      [k (get-method test/report k)])))

(defn install! []
  (doseq [[k m] patched-methods]
    (MultiFn/.addMethod test/report k m)))

(defn uninstall! []
  (doseq [[k m] clojure-methods]
    (MultiFn/.addMethod test/report k m)))
