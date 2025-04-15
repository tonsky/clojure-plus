(ns clojure+.error
  (:require
   [clojure.java.io :as io]
   [clojure.stacktrace :as stacktrace]
   [clojure.string :as str])
  (:import
   [clojure.lang Compiler ExceptionInfo MultiFn]
   [java.io Writer]))

(defn- color? []
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

(defn- default-config []
  {:clean?           true
   :trace-transform  nil
   :collapse-common? true
   :color?           (color?)
   :reverse?         false
   :root-cause-only? false
   :indent           2})

(def ^:dynamic *trace-transform*
  nil)

(def config
  (default-config))

(defn- noise? [^StackTraceElement el]
  (let [class (.getClassName el)]
    (#{"clojure.lang.RestFn" "clojure.lang.AFn"} class)))

(defn- duplicate? [^StackTraceElement prev-el ^StackTraceElement el]
  (and
    (= (.getClassName prev-el) (.getClassName el))
    (= (.getFileName prev-el) (.getFileName el))
    (#{"invokeStatic"} (.getMethodName prev-el))
    (#{"invoke" "doInvoke" "invokePrim"} (.getMethodName el))))

(defn- clear-duplicates [els]
  (for [[prev-el el] (map vector (cons nil els) els)
        :when (or (nil? prev-el) (not (duplicate? prev-el el)))]
    el))

(defn- trace-element [^StackTraceElement el]
  (let [file     (.getFileName el)
        line     (.getLineNumber el)
        cls      (.getClassName el)
        method   (.getMethodName el)
        clojure? (if file
                   (or (.endsWith file ".clj") (.endsWith file ".cljc") (= file "NO_SOURCE_FILE"))
                   (#{"invoke" "doInvoke" "invokePrim" "invokeStatic"} method))
        
        [ns separator method]
        (cond
          (not (:clean? config))
          [cls "." method]
                      
          (not clojure?)
          [(-> cls (str/split #"\.") last) "." method]
          
          (#{"invoke" "doInvoke" "invokeStatic"} method)
          (let [[ns method] (str/split (Compiler/demunge cls) #"/" 2)
                method (-> method
                         (str/replace #"eval\d{3,}" "eval")
                         (str/replace #"--\d{3,}" ""))]
            [ns "/" method])
          
          :else
          [(Compiler/demunge cls) "/" (Compiler/demunge method)])]
    {:element   el
     :file      (if (= "NO_SOURCE_FILE" file) nil file)
     :line      line
     :ns        ns
     :separator separator
     :method    method}))

(defn- get-trace [^Throwable t]
  (cond->> (.getStackTrace t)
    (:clean? config)          (remove noise?)
    (:clean? config)          (clear-duplicates)
    true                      (mapv trace-element)
    (:trace-transform config) ((:trace-transform config))
    *trace-transform*         (*trace-transform*)))

(defn datafy-throwable [^Throwable t]
  (let [trace  (get-trace t)
        common (when (:collapse-common? config)
                 (when-some [prev-t (.getCause t)]
                   (let [prev-trace (get-trace prev-t)]
                     (loop [m (dec (count trace))
                            n (dec (count prev-trace))]
                       (if (and (>= m 0) (>= n 0) (= (nth trace m) (nth prev-trace n)))
                         (recur (dec m) (dec n))
                         (- (dec (count trace)) m))))))]
    {:message (.getMessage t)
     :class   (class t)
     :data    (ex-data t)
     :trace   trace
     :common  (or common 0)
     :cause   (some-> (.getCause t) datafy-throwable)}))

(defn- ansi-red []
  (when (:color? config)
    "\033[31m"))

(defn- ansi-grey []
  (when (:color? config)
    "\033[37m"))

(defn- ansi-reset []
  (when (:color? config)
    "\033[0m"))

(defmacro write [w & args]
  (list* 'do
    (for [arg args]
      (if (or (string? arg) (= String (:tag (meta arg))))
        `(Writer/.write ~w ~arg)
        `(Writer/.write ~w (str ~arg))))))

(defn- pad [ch ^long len]
  (when (pos? len)
    (let [sb (StringBuilder. len)]
      (dotimes [_ len]
        (.append sb (char ch)))
      (str sb))))

(defn- split-file [s]
  (if-some [[_ name ext] (re-matches #"(.*)(\.[^.]+)" s)]
    [name ext]
    [s ""]))

(defn- linearize [key xs]
  (->> xs (iterate key) (take-while some?)))

(defn- longest-method [indent ts]
  (reduce max 0
  (for [[t depth] (map vector ts (range))
        el        (:trace t)]
    (+ (* depth indent) (count (:ns el)) (count (:separator el)) (count (:method el))))))

(defn- print-readably-impl [^Writer w ts max-len depth]
  (let [t      (first ts)
        {:keys [class message data trace common]} t
        trace  (drop-last common trace)
        indent (pad \space depth)]
    (write w "\n" indent "#error {")
    
    ;; class
    (write w "\n" indent " :class   " (Class/.getName class))
    
    ;; message
    (when message
      (write w "\n" indent " :message ")
      (print-method message w))
    
    ;; data
    (when data
      (write w "\n" indent " :data    ")
      (print-method data w))
    
    ;; trace
    (write w "\n" indent " :trace")
    (write w "\n" indent " [")
    (loop [trace  trace
           first? true]
      (when-not (empty? trace)
        (let [el (first trace)
              {:keys [ns separator method file line]} el
              right-pad (pad \space (- max-len depth (count ns) (count separator) (count method)))]
          (when-not first?
            (write w "\n" indent "  "))
          (write w "[" ns separator method)
          (cond
            (= -2 line)
            (write w right-pad "  :native-method")
            
            file
            (do
              (write w right-pad "  ")
              (print-method file w)
              (write w " " line)))
          (write w "]")
          (recur (next trace) false))))
    (when (pos? common)
      (let [first? (empty? trace)]
        (when-not first?
          (write w "\n" indent "  "))
        (write w "[...common-elements " common "]")))
    (write w "]")
    
    ;; cause
    (when-some [ts' (next ts)]
      (write w "\n" indent " :cause")
      (print-readably-impl w ts' max-len (inc depth)))
    
    (write w "}")))

(defn print-readably [^Writer w ^Throwable t]
  (let [ts      (linearize :cause (datafy-throwable t))
        max-len (longest-method 1 ts)]
    (print-readably-impl w ts max-len 0)))

(defn- print-readably-reverse-impl [^Writer w ts max-len depth]
  (let [t     (first ts)
        {:keys [class message data trace common]} t
        trace  (drop-last common trace)
        indent (pad \space depth)]
    (write w "\n" indent "#error {")

    ;; cause
    (when-some [ts' (next ts)]
      (write w "\n" indent " :cause")
      (print-readably-reverse-impl w ts' max-len (inc depth)))

    ;; trace
    (write w "\n" indent " :trace")
    (write w "\n" indent " [")
    (when (pos? common)
      (write w "[...common-elements " common "]"))
    (loop [trace  (reverse trace)
           first? (not (pos? common))]
      (when-not (empty? trace)
        (let [el (first trace)
              {:keys [ns separator method file line]} el
              right-pad (pad \space (- max-len depth (count ns) (count separator) (count method)))]
          (when-not first?
            (write w "\n" indent "  "))
          (write w "[" ns separator method)
          (cond
            (= -2 line)
            (write w right-pad "  :native-method")
            
            file
            (do
              (write w right-pad "  ")
              (print-method file w)
              (write w " " line)))
          (write w "]")
          (recur (next trace) false))))
    (write w "]")

    ;; data
    (when data
      (write w "\n" indent " :data    ")
      (print-method data w))

    ;; message
    (when message
      (write w "\n" indent " :message ")
      (print-method message w))

    ;; class
    (write w "\n" indent " :class   " (Class/.getName class))

    (write w "}")))

(defn print-readably-reverse [^Writer w ^Throwable t]
  (let [ts      (linearize :cause (datafy-throwable t))
        max-len (longest-method 1 ts)]
    (print-readably-reverse-impl w ts max-len 0)))

(defn print-humanly [^Writer w ^Throwable t]
  (let [ts      (linearize :cause (datafy-throwable t))
        max-len (longest-method 0 ts)
        indent  (cond
                  (string? (:indent config))
                  (:indent config)
                  
                  (int? (:indent config))
                  (pad \space (:indent config)))]
    (doseq [[idx t] (map vector (range) ts)
            :let [{:keys [class message data trace common]} t]]
      ;; class
      (write w (when (pos? idx) "\nCaused by: ") (ansi-red) (Class/.getSimpleName class))
      
      ;; message
      (if message
        (do
          (write w ":" (ansi-reset) " ")
          (print-method message w))
        (write w (ansi-reset)))
      
      ;; data
      (when data
        (write w " " (ansi-grey))
        (print-method data w)
        (write w (ansi-reset)))
      
      ;; trace
      (doseq [el   (drop-last common trace)
              :let [{:keys [ns separator method file line]} el
                    right-pad (pad \space (- max-len (count ns) (count separator) (count method)))]]
        (write w "\n" indent)
       
        ;; method
        (write w (ansi-grey) ns separator (ansi-reset) method)

        ;; locaiton
        (cond 
          (= -2 line)
          (write w right-pad "  " (ansi-grey) "Native Method" (ansi-reset))
  
          file
          (write w right-pad "  " file " " (ansi-grey) line (ansi-reset))))
      
      ;; ... common elements
      (when (pos? common)
        (write w "\n" indent (ansi-grey) "... " common " common elements" (ansi-reset))))))

(defn print-humanly-reverse [^Writer w ^Throwable t]
  (let [ts      (linearize :cause (datafy-throwable t))
        max-len (longest-method 0 ts)
        indent  (cond
                  (string? (:indent config))
                  (:indent config)
                  
                  (int? (:indent config))
                  (pad \space (:indent config)))]
    (doseq [[idx t] (map vector (range) (reverse ts))
            :let [{:keys [class message data trace common]} t]]
      ;; ... common elements
      (when (pos? common)
        (write w "\n" indent (ansi-grey) "... " common " common elements" (ansi-reset)))
      
      ;; trace
      (doseq [el   (->> trace (drop-last common) reverse)
              :let [{:keys [ns separator method file line]} el
                    right-pad (pad \space (- max-len (count ns) (count separator) (count method)))]]
        (write w "\n" indent)
       
        ;; method
        (write w (ansi-grey) ns separator (ansi-reset) method)
        
        ;; locaiton
        (cond 
          (= -2 line)
          (write w right-pad "  " (ansi-grey) "Native Method" (ansi-reset))
  
          file
          (write w right-pad "  " file " " (ansi-grey) line (ansi-reset))))
      
      ;; class
      (write w "\n" (when (< idx (dec (count ts))) "Caused by: ") (ansi-red) (Class/.getSimpleName class))
      
      ;; message
      (if message
        (do
          (write w ":" (ansi-reset) " ")
          (print-method message w))
        (write w (ansi-reset)))
      
      ;; data
      (when data
        (write w " " (ansi-grey))
        (print-method data w)
        (write w (ansi-reset))))))

(defn root-cause [^Throwable t]
  (if-some [c (.getCause t)]
    (recur c)
    t))

(defonce ^:private clojure-print-method
  (get-method print-method Throwable))

(defn- patched-print-method [^Throwable t ^Writer w]
  (let [t (if (:root-cause-only? config)
            (root-cause t)
            t)]
    (if *print-readably*
      (if (:reverse? config)
        (print-readably-reverse w t)
        (print-readably w t))
      (if (:reverse? config)
        (print-humanly-reverse w t)
        (print-humanly w t)))))

(defn install!
  "Improves the way exceptions are printed, including print*, pr*,
   and clojure.pprint/pprint.
   
   Possible options:
   
     :clean?           <bool> :: Converts Clojure-specific stack trace elements
                                 to be more clojure-like. True by default.
     :collapse-common? <bool> :: With chained exceptions, skips common part of
                                 stack traces. True by default.
     :trace-transform  <fn>   :: A fn accepting and returning trace--a sequence
                                 of maps {:keys [element file line ns method]}
                                 where element is original StackTraceElement.
     :color?           <bool> :: Whether to use color output. Autodetect by default.
     :reverse?         <bool> :: Whether to print stack trace and cause chain
                                 inner-to-outer (Java default) or outer-to-inner.
                                 Useful for REPL and small terminals as class,
                                 message and source will always be at the bottom.
                                 False by default.
     :root-cause-only? <bool> :: Only print root cause. False by default.
     :indent           <int> | <string> :: how many spaces to use to indent
                                 stack trace elements. Java uses \"\\t\", we use
                                 2 spaces by default."
  ([]
   (install! {}))
  ([opts]
   (.doReset #'config (merge (default-config) opts))
   (MultiFn/.addMethod print-method Throwable patched-print-method)))

(defn uninstall!
  "Restore default Clojure printer for Throwable"
  []
  (MultiFn/.addMethod print-method Throwable clojure-print-method))

(comment
  (install! {; :color? false
             ; :root-cause-only? true
             ; :clean? false
             ; :collapse-common? false
             ; :reverse? true
             ; :indent 2
             })

  (defn ggg []
    (throw (ex-info "abc" {:a 1 :b 2} (Throwable. "Cause!!!"))))

  (defn fff []
    (ggg))

  (try
    (fff)
    (catch Throwable e
      #_(prn e)
      (println e)
      nil))

  (.printStackTrace (ex-info "abc" {:a 1 :b 2} (Throwable. "CAuse!!!")))
  (println (ex-info "abc" {:a 1 :b 2} (Throwable. "CAuse!!!"))))
