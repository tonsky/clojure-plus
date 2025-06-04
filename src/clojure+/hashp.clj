(ns clojure+.hashp
  (:require
   [clojure.string :as str]
   [clojure.pprint :as pprint]
   [clojure.walk :as walk]
   [clojure+.util :as util])
  (:import
   [clojure.lang Compiler TaggedLiteral]
   [java.util List]))

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

(defn- arglists [sym]
  (when sym
    (or
      (:arglists (meta (resolve sym)))
      (condp = sym
        'if    '([test then] [test then else])
        'let*  '([bindings & body])
        'def   '([sym] [sym init] [sym doc init])
        'do    '([& exprs])
        'quote '([form])
        'var   '([sym])
        'throw '([ex])
        'try   '([& body])
        nil))))

(defn- arity-ok? [arglists arity]
  (loop [arglists arglists]
    (let [[arglist & rest] arglists]
      (if (nil? arglist)
        false
        (let [i (.indexOf ^List arglist '&)]
          (if (= -1 i)
            (if (= (count arglist) arity)
              true
              (recur rest))
            (if (>= arity i)
              true
              (recur rest))))))))

(defn- form-ok? [form]
  (cond
    (and (seq? form) (#{'let 'let*} (first form)))
    (vector? (second form))

    (and (seq? form) (= 'var (first form)))
    (symbol? (second form))

    ;; TODO extra validate fn, def, defn etc

    ;; special forms
    (#{'if 'let 'let* 'def 'do 'quote 'var 'throw 'try} form)
    false

    ;; naked macros
    (and (symbol? form) (some-> form resolve meta :macro))
    false

    (not (seq? form))
    true

    (not (symbol? (first form)))
    true

    :else
    (let [sym      (first form)
          arity    (dec (count form))
          arglists (arglists sym)]
      (if arglists
        (arity-ok? arglists arity)
        true))))

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
        form-last  (add-last y-sym form)
        error      (str "Something went wrong, can’t print " form)]
    `((fn
        ([_#]
         ~(if (form-ok? form)
            `(hashp-impl '~form ~form)
            `(throw (Exception. ~error))))
        ([~x-sym ~y-sym]
         (hashp-impl '~form
           (cond
             ~@(when (form-ok? form-last)
                 [`(= ::undef ~x-sym) form-last])
             ~@(when (form-ok? form-first)
                 [`(= ::undef ~y-sym) form-first])
             :else (throw (Exception. ~error))))))
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
