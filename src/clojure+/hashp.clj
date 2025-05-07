(ns clojure+.hashp
  (:require
   [clojure.string :as str]
   [clojure.pprint :as pprint]
   [clojure.walk :as walk]
   [clojure+.util :as util])
  (:import
   [clojure.lang Compiler TaggedLiteral]))

(defn- default-config []
  {:symbol 'p
   :color? (util/color?)})

(def config
  (default-config))

(def ^:private lock
  (Object.))

(defn- pos []
  (let [trace (->> (Thread/currentThread)
                (.getStackTrace)
                (seq))
        el    ^StackTraceElement (nth trace 7)
        cls   (Compiler/demunge (.getClassName el))
        file  (.getFileName el)
        line  (.getLineNumber el)]
    (str "["
      cls
      (when file (str " " file))
      (when line (str ":" line))
      "]")))

(defn- ansi-blue []
  (when (:color? config)
    "\033[34m"))

(defn- ansi-grey []
  (when (:color? config)
    "\033[37m"))

(defn- ansi-reset []
  (when (:color? config)
    "\033[0m"))

(defn hashp-impl [form res]
  (let [position (pos)
        form     (walk/postwalk
                   (fn [form]
                     (if (and (seq? form) (seq? (first form)) (= ::undef (second form)))
                       (let [form (-> form   ; ((fn ([_#] ...)))
                                    first    ;  (fn ([_#] ...))
                                    second   ;      ([_#] (hashp-impl ...))
                                    second   ;            (hashp-impl '~form ~form)
                                    second   ;                        '~form
                                    second)] ;                         ~form
                         (TaggedLiteral/create (:symbol config) form))
                       form))
                   form)]
    (locking lock
      (println (str (ansi-blue) "#" (:symbol config) " " form " " (ansi-grey) position (ansi-reset)))
      (pprint/pprint res))
    res))

(defn- add-first [x form]
  (if (seq? form)
    (list* (first form) x (next form))
    (list form x)))

(defn- add-last [x form]
  (if (seq? form)
    (list* (concat form [x]))
    (list form x)))

(defn hashp [form]
  (let [x-sym      (gensym "x")
        y-sym      (gensym "y")
        form-first (add-first x-sym form)
        form-last  (add-last y-sym form)]
    `((fn
        ([_#]
         (hashp-impl '~form ~form))
        ([~x-sym ~y-sym]
         (hashp-impl '~form
           (cond
             (= ::undef ~x-sym) ~form-last
             (= ::undef ~y-sym) ~form-first
             :else              (throw (Exception. "Impossible!"))))))
      ::undef)))

(defn install!
  "Enables #p reader tag. Add #p before any form to quickly print its value
   to output next time it’s evaluated. Works inside -> ->> too!

     #p (+ 1 2)

     => #p (+ 1 2) [user/eval4348:77]
        3

   Possible options:

     :color?           <bool> :: Whether to use color output. Autodetect by default.
     :symbol           <sym>  :: Which symbol to use for reader tag. 'p by default."
  ([]
   (install! {}))
  ([opts]
   (let [config (merge (default-config) opts)
         _      (.doReset #'config config)
         sym    (:symbol config)]
     (alter-var-root #'*data-readers* assoc sym #'hashp)
     (when (thread-bound? #'*data-readers*)
       (set! *data-readers* (assoc *data-readers* sym #'hashp))))))

(defn uninstall! []
  (let [sym (:symbol config)]
    (alter-var-root #'*data-readers* dissoc sym)
    (when (thread-bound? #'*data-readers*)
      (set! *data-readers* (dissoc *data-readers* sym)))))
