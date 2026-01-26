(ns clojure+.util
  (:require
   [clojure.string :as str]))

(def runtime-version
  (let [v (System/getProperty "java.version")]
    (if (str/starts-with? v "1.")
      (-> (str/split v #"\.") second Long/parseLong)
      (-> (str/split v #"\.") first Long/parseLong))))

(defmacro if-java-version-gte
  ([version if-branch]
   (when (<= version runtime-version)
     if-branch))
  ([version if-branch else-branch]
   (if (<= version runtime-version)
     if-branch
     else-branch)))

(defmacro if-clojure-version-gte
  ([version if-branch]
   `(if-clojure-version-gte ~version ~if-branch nil))
  ([version if-branch else-branch]
   (if (>= (compare
             [(:major *clojure-version*) (:minor *clojure-version*) (:incremental *clojure-version* 0)]
             (->> (str/split version #"\.") (mapv #(Long/parseLong %))))
         0)
     if-branch
     else-branch)))

(def bb?
  (System/getProperty "babashka.version"))

(defmacro if-not-bb
  ([if-branch]
   (when-not bb?
     if-branch))
  ([if-branch else-branch]
   (if-not bb?
     if-branch
     else-branch)))

(defn rebind-dynamic-impl
  "Clojure creates a lot of layers of dynamic bindings for *data-readers*
   (e.g. clojure.main, require, Compiler/load etc). Some of them are not
   directly nested, but instead siblings. This causes issues even in simplest
   cases: e.g. loading namespace vs executing a function from that namespace,
   latter won't see changes made by former.

   This might lead to very confusing behaviors, like:

       (print/install!)

       (defn -main [& args]
         (println *data-readers*))
       ;; => {}

   Modifying root binding is not enough, as the changes don't automatically
   propagate down the stack. Here we are abusing pop/pushBindings to add
   desired readers to every frame in dynamic vars stack."
  [var f args]
  (let [bindings (clojure.lang.Var/getThreadBindings)]
    (try
      (clojure.lang.Var/popThreadBindings)
      (try
        (rebind-dynamic-impl var f args)
        (finally
          (clojure.lang.Var/pushThreadBindings
            (apply update bindings var f args))))
      (catch IllegalStateException _
        nil))))

(defmacro rebind-dynamic [var f & args]
  `(do
     (alter-var-root (var ~var) ~f ~@args)
     ~(if true #_bb?
        `(when (thread-bound? (var ~var))
           (set! ~var (~f ~var ~@args)))
        `(rebind-dynamic-impl (var ~var) ~f ~(vec args)))))

(defn color? []
  (cond
    (System/getenv "NO_COLOR")
    false

    (= "true" (System/getProperty "clojure-plus.color"))
    true

    (System/getProperty "clojure-plus.color")
    false

    (find-ns 'nrepl.core)
    true

    (System/console)
    true

    :else
    true))
