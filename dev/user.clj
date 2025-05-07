(ns user
  (:require
   [clj-reload.core :as reload]
   [clojure.core.server :as server]
   [clojure.java.io :as io]
   [clojure.test :as test]))

(reload/init
  {:dirs      ["src" "dev" "test"]
   :no-reload '#{user}
   :output    :quieter})

(def reload
  reload/reload)

(defn -main [& args]
  (let [{port "--port"} args
        port (if port
               (Long/parseLong port)
               (+ 1024 (rand-int 64512)))
        _    (println "Started Server Socket REPL on port" port)
        file (io/file ".repl-port")]
    (spit file port)
    (.deleteOnExit file)
    (server/start-server
      {:name          "repl"
       :accept        'clojure.core.server/repl
       :server-daemon false
       :port          port})))

(defn- run-tests [re]
  (reload/reload {:only re})
  (let [vars (for [ns    (reload/find-namespaces re)
                   var   (vals (ns-interns (the-ns ns)))
                   :when (:test (meta var))
                   :when (:only (meta var))]
               var)]
    (if (empty? vars)
      (test/run-all-tests re)
      (binding [test/*report-counters* (ref test/*initial-report-counters*)]
        (test/test-vars vars)
        (test/do-report (assoc @test/*report-counters* :type :summary))
        @test/*report-counters*))))

(defn test-all []
  (run-tests #"clojure\+\.(?!test-test$).*"))

(defn -test-main [_]
  (let [{:keys [fail error]} (run-tests #"clojure\+\.(?!test-test$).*")]
    (System/exit (+ fail error))))
