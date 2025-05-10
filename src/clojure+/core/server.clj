(ns clojure+.core.server
  (:require
   [clojure.core.server :as server]
   [clojure.java.io :as io]))

(defn start-server
  "Same as clojure.core.server/start-server, but with better defaults.
   Supports random port, .port-file, and log message when server is started.

   Supported options:

     :address        Host or address, string, defaults to loopback address
     :port           Port, integer, defaults to random port
     :port-file      A file to output port number to. Defaults to \".repl-port\"
                     if :port is nil, nil otherwise
     :start-message? Whether or not to print “REPL started” message. Default to true
     :name           Name, required, defaults to \"repl\"
     :accept         Namespaced symbol of the accept function to invoke, required.
                     Defaults to clojure.core.server/repl
     :args           Vector of args to pass to accept function
     :bind-err       Bind *err* to socket out stream?, defaults to true
     :server-daemon  Is server thread a daemon?, defaults to false
     :client-daemon  Are client threads daemons?, defaults to true

   Returns server socket."
  ([]
   (start-server {}))
  ([opts]
   (let [port      (:port opts)
         has-port? (and port (pos? port))
         port-file (or
                     (:port-file opts)
                     (when-not has-port?
                       ".repl-port"))
         port      (if has-port?
                     port
                     (+ 1024 (rand-int (- 65536 1024))))]
     (when (:start-message? opts true)
       (println "Started Server Socket REPL on port" port))
     (when port-file
       (let [file (io/file port-file)]
         (spit file port)
         (.deleteOnExit file)))
     (server/start-server
       (merge
         {:name          "repl"
          :accept        'clojure.core.server/repl
          :server-daemon false}
         (dissoc opts :port-file :start-message?)
         {:port port})))))

