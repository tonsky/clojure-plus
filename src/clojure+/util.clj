(ns clojure+.util)

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
