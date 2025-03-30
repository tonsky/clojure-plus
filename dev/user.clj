(ns user
  (:require
   [duti.core :as duti]))

(duti/set-dirs "src" "dev" "test")

(def reload
  duti/reload)

(def -main
  duti/-main)

(defn test-all []
  (duti/test #"clojure\+\.(?!test-test$).*"))

(defn -test-main [_]
  (duti/test-exit #"clojure\+\.(?!test-test$).*"))
