(ns clojure+.print
  (:require
   [clojure.java.io :as io]
   [clojure.pprint :as pprint]
   [clojure.string :as str])
  (:import
   [clojure.lang AFunction Agent Atom ATransientSet Compiler Delay IDeref IPending ISeq MultiFn Namespace PersistentQueue  PersistentArrayMap$TransientArrayMap PersistentHashMap PersistentHashMap$TransientHashMap PersistentVector$TransientVector Reduced Ref Volatile]
   [java.io File Writer]
   [java.lang.ref SoftReference WeakReference]
   [java.lang.reflect Field]
   [java.net InetAddress URI URL]
   [java.nio.charset Charset]
   [java.nio.file Path]
   [java.time DayOfWeek Duration Instant LocalDate LocalDateTime LocalTime Month MonthDay OffsetDateTime OffsetTime Period Year YearMonth ZonedDateTime ZoneId ZoneOffset]
   [java.time.temporal ChronoUnit]
   [java.util List]
   [java.util.concurrent Future TimeUnit]
   [java.util.concurrent.atomic AtomicBoolean AtomicInteger AtomicIntegerArray AtomicLong AtomicLongArray AtomicReference AtomicReferenceArray]))

(def runtime-version
  (let [v (System/getProperty "java.version")]
    (if (str/starts-with? v "1.")
      (-> (str/split v #"\.") second parse-long)
      (-> (str/split v #"\.") first parse-long))))

(defmacro if-version-gte
  ([version if-branch]
   (when (<= version runtime-version)
     if-branch))
  ([version if-branch else-branch]
   (if (<= version runtime-version)
     if-branch
     else-branch)))

(def ^:private *catalogue
  (atom #{}))

(defn- pr-on [w x]
  (if *print-dup*
    (print-dup x w)
    (print-method x w))
  nil)

(defmacro defliteral [cls getter quoted-tag ctor]
  (let [tag       (second quoted-tag)
        print-sym (symbol (str "print-" tag))
        read-sym  (symbol (str "read-" tag))
        val-sym   (with-meta (gensym "v") {:tag cls})]
    `(do
       (defn ~print-sym [~val-sym ^Writer w#]
         (.write w# ~(str "#" tag " "))
         (let [rep# (~getter ~val-sym)]
           (if (string? rep#)
             (do
               (.write w# "\"")
               (.write w# (str/replace rep# "\"" "\\\""))
               (.write w# "\""))
             (pr-on w# rep#))))

       (defn ~read-sym [s#]
         (~ctor s#))
     
       (swap! *catalogue conj {:class ~cls :tag ~quoted-tag :print (var ~print-sym) :read (var ~read-sym)}))))

(defmacro defenum [cls quoted-tag values]
  (let [tag       (second quoted-tag)
        print-sym (symbol (str "print-" tag))
        read-sym  (symbol (str "read-" tag))]
    `(do
       (defn ~print-sym [v# ^Writer w#]
         (.write w# ~(str "#" tag " :"))
         (.write w# (str/lower-case (str v#))))

       (defn ~read-sym [kw#]
         (case kw#
           ~@(for [sym values
                   kv  [(-> sym str str/lower-case keyword)
                        (symbol (str cls) (str sym))]]
               kv)))
     
       (swap! *catalogue conj {:class ~cls :tag ~quoted-tag :print (var ~print-sym) :read (var ~read-sym)}))))

(defmacro prefer [a b]
  `(do
     (prefer-method print-method ~a ~b)
     (prefer-method print-dup ~a ~b)
     (prefer-method pprint/simple-dispatch ~a ~b)))


;; arrays

(defn print-bytes [^bytes arr ^Writer w]
  (.write w "#bytes \"")
  (dotimes [i (alength arr)]
    (.write w (format "%02X" (Byte/toUnsignedInt (aget arr i)))))
  (.write w "\""))

(defn- int->byte [i]
  (if (>= i 128)
    (byte (- i 256))
    (byte i)))

(defn read-bytes [^String s]
  (let [cnt (quot (count s) 2)
        arr (byte-array cnt)]
    (dotimes [idx cnt]
      (let [i (Integer/parseInt (subs s (* idx 2) (* (inc idx) 2)) 16)
            b (int->byte i)]
        (aset arr idx (byte b))))
    arr))

(swap! *catalogue conj {:class byte/1  :print #'print-bytes :tag 'bytes :read #'read-bytes})


(defn make-print-array [tag]
  (fn [arr ^Writer w]
    (.write w "#")
    (.write w (str tag))
    (.write w " ")
    (pr-on w (vec arr))))

(swap! *catalogue conj {:class boolean/1 :print (make-print-array 'booleans) :tag 'booleans :read #'boolean-array})
(swap! *catalogue conj {:class char/1    :print (make-print-array 'chars)    :tag 'chars    :read #'char-array})
(swap! *catalogue conj {:class short/1   :print (make-print-array 'shorts)   :tag 'shorts   :read #'short-array})
(swap! *catalogue conj {:class int/1     :print (make-print-array 'ints)     :tag 'ints     :read #'int-array})
(swap! *catalogue conj {:class long/1    :print (make-print-array 'longs)    :tag 'longs    :read #'long-array})
(swap! *catalogue conj {:class float/1   :print (make-print-array 'floats)   :tag 'floats   :read #'float-array})
(swap! *catalogue conj {:class double/1  :print (make-print-array 'doubles)  :tag 'doubles  :read #'double-array})

(defn read-strings [xs]
  (into-array String xs))

(swap! *catalogue conj {:class String/1 :print (make-print-array 'strings) :tag 'strings :read #'read-strings})


(defn- print-array [arr w]
  (let [cls (class arr)]
    (if (and cls (.isArray cls))
      (@#'clojure.core/print-sequential "[" print-array " " "]" arr w)
      (pr-on w arr))))

(defn print-objects [^Object/1 arr ^Writer w]
  (let [cls  (class arr)
        base (.getComponentType cls)]
    (cond
      (= Object base)
      (do
        (.write w "#objects ")
        (pr-on w (vec arr)))
      
      
      :else
      (let [name (pr-str cls)
            name (if (and
                       (str/starts-with? name "java.lang.")
                       (= 9 (.lastIndexOf name ".")))
                   (subs name 10)
                   name)]
        (.write w "#array ^")
        (.write w name)
        (.write w " ")
        (print-array arr w)))))

(swap! *catalogue conj {:class Object/1 :print #'print-objects :tag 'objects :read #'object-array})


(defn read-array [vals]
  (let [class (:tag (meta vals))
        class (cond-> class
                (symbol? class) resolve)
        base  (Class/.getComponentType class)
        arr   ^Object/1 (make-array base (count vals))]
    (doseq [i (range (count vals))
            :let [x (nth vals i)]]
      (aset arr i
        (if (.isArray base)
          (read-array (vary-meta x assoc :tag base))
          x)))
    arr))

(swap! *catalogue conj {:class Object/1 :print #'print-objects :tag 'array :read #'read-array})


;; refs

(defn make-print-ref [tag]
  (fn [ref ^Writer w]
    (.write w "#")
    (.write w (str tag))
    (.write w " ")
    (pr-on w @ref)))

(swap! *catalogue conj {:class Atom     :print (make-print-ref 'atom)     :tag 'atom     :read #'atom})
(swap! *catalogue conj {:class Agent    :print (make-print-ref 'agent)    :tag 'agent    :read #'agent})
(swap! *catalogue conj {:class Ref      :print (make-print-ref 'ref)      :tag 'ref      :read #'ref})
(swap! *catalogue conj {:class Volatile :print (make-print-ref 'volatile) :tag 'volatile :read #'volatile!})
(swap! *catalogue conj {:class Reduced  :print (make-print-ref 'reduced)  :tag 'reduced  :read #'reduced})


(defn print-promise [^IPending ref ^Writer w]
  (.write w "#promise ")
  (if (realized? ref)
    (pr-on w @ref)
    (.write w "<pending...>")))

(defn- read-promise [val]
  (if (= '<pending...> val)
    (promise)
    (deliver (promise) val)))

(prefer IPending IDeref)
(prefer ISeq IPending)
(prefer List IPending)
(swap! *catalogue conj {:class IPending :print print-promise :tag 'promise :read #'read-promise})


(defn print-delay [^Delay ref ^Writer w]
  (.write w "#delay ")
  (if (realized? ref)
    (pr-on w @ref)
    (.write w "<pending...>")))

(defn- read-delay [val]
  (if (= '<pending...> val)
    (throw (ex-info "Can’t read back <pending...> delay" {}))
    (doto (delay val)
      (deref))))

(swap! *catalogue conj {:class Delay :print print-delay :tag 'delay :read #'read-delay})


(defn print-future [^Future ref ^Writer w]
  (.write w "#future ")
  (if (.isDone ref)
    (pr-on w (.get ref))
    (.write w "<pending...>")))

(defn- read-future [val]
  (if (= '<pending...> val)
    (throw (ex-info "Can’t read back <pending...> future" {}))
    (doto (future val)
      (deref))))

(prefer Future IPending)
(prefer Future IDeref)
(swap! *catalogue conj {:class Future :print print-future :tag 'future :read #'read-future})


;; #function

(defn print-fn [^AFunction f ^Writer w]
  (.write w "#fn ")
  (.write w (-> f class .getName Compiler/demunge)))

(defn read-fn [sym]
  (let [cls  (-> sym str (str/split #"/") (->> (map Compiler/munge) (str/join "$") Class/forName))
        ctor (.getDeclaredConstructor cls (make-array Class 0))]
    (if ctor
      (.newInstance ctor (make-array Object 0))
      (throw (ex-info "Can't read closures" {})))))

(swap! *catalogue conj {:class AFunction :print print-fn :tag 'fn :read #'read-fn})


(def ^:private ^Field multifn-name-field
  (doto (.getDeclaredField MultiFn "name")
    (.setAccessible true)))

(defn print-multifn [^MultiFn f ^Writer w]
  (.write w "#multifn ")
  (.write w ^String (.get multifn-name-field f)))

(swap! *catalogue conj {:class MultiFn :print print-multifn :tag 'multifn})


;; #ns

(defliteral Namespace .getName 'ns the-ns)


;; #transient

(defn print-transient-vector [^PersistentVector$TransientVector v ^Writer w]
  (let [cnt (count v)]
    (.write w "#transient [")
    (dotimes [i cnt]
      (pr-on w (nth v i))
      (when (< i (dec cnt))
        (.write w " ")))
    (.write w "]")))

(swap! *catalogue conj {:class PersistentVector$TransientVector :print #'print-transient-vector :tag 'transient :read #'transient})


(def ^:private ^Field array-map-array-field
  (doto (.getDeclaredField PersistentArrayMap$TransientArrayMap "array")
    (.setAccessible true)))

(defn print-transient-array-map [^PersistentArrayMap$TransientArrayMap m ^Writer w]
  (let [cnt (count m)
        arr ^objects (.get array-map-array-field m)]
    (.write w "#transient {")
    (dotimes [i cnt]
      (pr-on w (aget arr (-> i (* 2))))
      (.write w " ")
      (pr-on w (aget arr (-> i (* 2) (+ 1))))
      (when (< i (dec cnt))
        (.write w ", ")))
    (.write w "}")))

(swap! *catalogue conj {:class PersistentArrayMap$TransientArrayMap :print #'print-transient-array-map :tag 'transient :read #'transient})


(def ^:private ^Field hash-map-edit-field
  (doto (.getDeclaredField PersistentHashMap$TransientHashMap "edit")
    (.setAccessible true)))

(defn print-transient-hash-map [^PersistentHashMap$TransientHashMap m ^Writer w]
  (let [edit       ^AtomicReference (.get hash-map-edit-field m)
        edit-value (.get edit)
        m'         (persistent! m)]
    (.write w "#transient ")
    (pr-on w m')
    (.set edit edit-value)))

(swap! *catalogue conj {:class PersistentHashMap$TransientHashMap :print #'print-transient-hash-map :tag 'transient :read #'transient})


(def ^:private ^Field set-impl-field
  (doto (.getDeclaredField ATransientSet "impl")
    (.setAccessible true)))

(defn print-transient-set [^ATransientSet s ^Writer w]
  (let [m          ^PersistentHashMap$TransientHashMap (.get set-impl-field s)
        edit       ^AtomicReference (.get hash-map-edit-field m)
        edit-value (.get edit)
        m'         (persistent! m)
        cnt        (count m')]
    (.write w "#transient #{")
    (doseq [[k idx] (map vector (keys m') (range))]
      (pr-on w k)
      (when (< idx (dec cnt))
        (.write w " ")))
    (.write w "}")
    (.set edit edit-value)))

(swap! *catalogue conj {:class ATransientSet :print #'print-transient-set :tag 'transient :read #'transient})


;; #queue

(defliteral PersistentQueue vec 'queue #(into PersistentQueue/EMPTY %))


;; java.io

(defliteral File .getPath 'file #(File. ^String %))


;; java.lang

(defn print-thread [^Thread t ^Writer w]
  (if-version-gte 21
    (when (.isVirtual t)
      (.write w "^:virtual ")))
  (.write w "#thread [")
  (pr-on w (if-version-gte 19 (.threadId t) (.getId t)))
  (.write w " ")
  (pr-on w (.getName t))
  (let [g (.getThreadGroup t)]
    (when (and g (not= g (.getThreadGroup (Thread/currentThread))))
      (.write w " ")
      (pr-on w (.getName g))))
  (.write w "]"))

(swap! *catalogue conj {:class Thread :print #'print-thread :tag 'thread})


;; java.lang.ref

(defliteral SoftReference .get 'soft-ref SoftReference.)
(defliteral WeakReference .get 'weak-ref WeakReference.)


;; java.net

(defliteral InetAddress .getHostAddress 'inet-address InetAddress/getByName)
(defliteral URI         str             'uri          URI.)
(defliteral URL         str             'url          URL.)



;; java.nio.charset

(defliteral Charset .name 'charset Charset/forName)


;; java.nio.file

(defliteral Path str 'path #(File/.toPath (io/file %)))


;; java.time

(defliteral Duration       str 'duration         Duration/parse)
(defliteral Instant        str 'instant          Instant/parse)
(defliteral LocalDate      str 'local-date       LocalDate/parse)
(defliteral LocalDateTime  str 'local-date-time  LocalDateTime/parse)
(defliteral LocalTime      str 'local-time       LocalTime/parse)
(defliteral MonthDay       str 'month-day        MonthDay/parse)
(defliteral OffsetDateTime str 'offset-date-time OffsetDateTime/parse)
(defliteral OffsetTime     str 'offset-time      OffsetTime/parse)
(defliteral Period         str 'period           Period/parse)
(defliteral Year           str 'year             Year/parse)
(defliteral YearMonth      str 'year-month       YearMonth/parse)
(defliteral ZonedDateTime  str 'zoned-date-time  ZonedDateTime/parse)
(defliteral ZoneId         str 'zone-id          ZoneId/of)
(defliteral ZoneOffset     str 'zone-offset      #(ZoneOffset/of ^String %))

(defenum DayOfWeek 'day-of-week
  [MONDAY TUESDAY WEDNESDAY THURSDAY FRIDAY SATURDAY SUNDAY])

(defenum Month 'month
  [JANUARY FEBRUARY MARCH APRIL MAY JUNE JULY AUGUST SEPTEMBER OCTOBER NOVEMBER DECEMBER])

(defenum ChronoUnit 'chrono-unit
  [NANOS MICROS MILLIS SECONDS MINUTES HOURS HALF_DAYS DAYS WEEKS MONTHS YEARS DECADES CENTURIES MILLENNIA ERAS])


;; java.util.concurrent

(defenum TimeUnit 'time-unit
  [DAYS HOURS MICROSECONDS MILLISECONDS MINUTES NANOSECONDS SECONDS])


;; java.util.concurrent.atomic

(defliteral AtomicBoolean   .get 'atomic-boolean AtomicBoolean.)
(defliteral AtomicInteger   .get 'atomic-int     AtomicInteger.)
(defliteral AtomicLong      .get 'atomic-long    AtomicLong.)
(defliteral AtomicReference .get 'atomic-ref     AtomicReference.)

(defn print-atomic-ints [^AtomicIntegerArray a ^Writer w]
  (.write w "#atomic-ints [")
  (dotimes [i (.length a)]
    (.write w (str (.get a i)))
    (when (< i (dec (.length a)))
      (.write w " ")))
  (.write w "]"))

(defn read-atomic-ints [xs]
  (AtomicIntegerArray. (int-array xs)))

(swap! *catalogue conj {:class AtomicIntegerArray :print #'print-atomic-ints :tag 'atomic-ints :read #'read-atomic-ints})


(defn print-atomic-longs [^AtomicLongArray a ^Writer w]
  (.write w "#atomic-longs [")
  (dotimes [i (.length a)]
    (.write w (str (.get a i)))
    (when (< i (dec (.length a)))
      (.write w " ")))
  (.write w "]"))

(defn read-atomic-longs [xs]
  (AtomicLongArray. (long-array xs)))

(swap! *catalogue conj {:class AtomicLongArray :print #'print-atomic-longs :tag 'atomic-longs :read #'read-atomic-longs})


(defn print-atomic-refs [^AtomicReferenceArray a ^Writer w]
  (.write w "#atomic-refs [")
  (dotimes [i (.length a)]
    (pr-on w (.get a i))
    (when (< i (dec (.length a)))
      (.write w " ")))
  (.write w "]"))

(defn read-atomic-refs [xs]
  (AtomicReferenceArray. ^objects (into-array Object xs)))

(swap! *catalogue conj {:class AtomicReferenceArray :print #'print-atomic-refs :tag 'atomic-refs :read #'read-atomic-refs})


;; install

(defn- catalogue [{:keys [include exclude]}]
  (cond->> @*catalogue
    exclude (remove #((set exclude) (:tag %)))
    include (filter #((set include) (:tag %)))))

(defn install-printers!
  "Install printers for most of Clojure built-in data structures.
   
   After running this, things like atoms and transients will print like this:
   
     (atom 123)          ; => #atom 123
     (transient [1 2 3]) ; => #transient [1 2 3]
   
   Possible opts:
   
     :include :: [sym ...] - list of tags to include (white list)
     :exclude :: [sym ...] - list of tags to exclude (black list)"
  ([]
   (install-printers! {}))
  ([opts]
   (let [catalogue (catalogue opts)]
     (doseq [{:keys [class print]} catalogue]
       (MultiFn/.addMethod print-method class print)
       (MultiFn/.addMethod print-dup class print)
       (MultiFn/.addMethod pprint/simple-dispatch class #(print % *out*))))))

(defn data-readers
  "List of all readers this library can install. Returns map of {symbol -> var}
   
   Possible opts:
   
     :include :: [sym ...] - list of tags to include (white list)
     :exclude :: [sym ...] - list of tags to exclude (black list)"
  ([]
   (data-readers {}))
  ([opts]
   (into {} (map (juxt :tag :read) (catalogue opts)))))

(defn install-readers!
  "Install readers for most of Clojure built-in data structures.
   
   After running this, things like atoms and transients will readable by reader:
   
     (read-string \"#atom 123\")
     (read-string \"#transient [1 2 3]\")
   
   Possible opts:
   
     :include :: [sym ...] - list of tags to include (white list)
     :exclude :: [sym ...] - list of tags to exclude (black list)"
  ([]
   (install-readers! {}))
  ([opts]
   (let [readers (data-readers opts)]
     (alter-var-root #'*data-readers* merge readers)
     (when (thread-bound? #'*data-readers*)
       (set! *data-readers* (merge *data-readers* readers))))))

(defn install!
  "Install both printers and readers for most of Clojure built-in data structures.
   
   After running this, things like atoms and transients will print like this:
   
     (atom 123)          ; => #atom 123
     (transient [1 2 3]) ; => #transient [1 2 3]
   
   and will become readable by reader:
   
     (read-string \"#atom 123\")
     (read-string \"#transient [1 2 3]\")
   
   Note: this representation doesn't track identity. So printing atom and reading it back
   will produce a new atom. Same goes for arrays, transients etc.

   Possible opts:
   
     :include :: [sym ...] - list of tags to include (white list)
     :exclude :: [sym ...] - list of tags to exclude (black list)"
  ([]
   (install! {}))
  ([opts]
   (install-printers! opts)
   (install-readers! opts)))
