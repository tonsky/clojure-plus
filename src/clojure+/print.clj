(ns clojure+.print
  (:require
   [clojure.string :as str])
  (:import
   [clojure.lang Atom Agent ATransientSet Delay IDeref IPending ISeq Namespace PersistentQueue Ref PersistentArrayMap$TransientArrayMap PersistentHashMap PersistentHashMap$TransientHashMap PersistentVector$TransientVector Volatile]
   [java.io File Writer]
   [java.lang.reflect Field]
   [java.net InetAddress URI URL]
   [java.nio.file Path]
   [java.time DayOfWeek Duration Instant LocalDate LocalDateTime LocalTime Month MonthDay OffsetDateTime OffsetTime Period Year YearMonth ZonedDateTime ZoneId ZoneOffset]
   [java.time.temporal ChronoUnit]
   [java.util.concurrent Future]
   [java.util.concurrent.atomic AtomicBoolean AtomicInteger AtomicIntegerArray AtomicLong AtomicLongArray AtomicReference AtomicReferenceArray]))

(defmacro defprint [type [value writer] & body]
  `(do
     (defmethod print-method ~type [~(vary-meta value assoc :tag type)
                                    ~(vary-meta writer assoc :tag 'java.io.Writer)]
       ~@body)
     (defmethod print-dup ~type [~value ~writer]
       (print-method ~value ~writer))))

(defmacro defprint-read-str
  ([cls tag ctor]
   `(defprint-read-str ~cls ~tag ~ctor str))
  ([cls tag ctor getter]
  `(do
     (defprint ~cls [t# w#]
       (.write w# ~(str "#" tag " \""))
       (.write w# (str/replace (~getter t#) "\"" "\\\""))
       (.write w# "\""))

     (defn ~(symbol (str "read-" tag)) [^String s#]
       (~ctor s#))

     (alter-var-root #'*data-readers* assoc (quote ~tag) (var ~(symbol (str "read-" tag)))))))

(defmacro defprint-read-value [cls tag ctor getter]
  `(do
     (defprint ~cls [v# w#]
       (.write w# ~(str "#" tag " "))
       (pr-on (~getter v#) w#))

     (defn ~(symbol (str "read-" tag)) [^String s#]
       (~ctor s#))

     (alter-var-root #'*data-readers* assoc (quote ~tag) (var ~(symbol (str "read-" tag))))))


(defmacro prefer [a b]
  `(do
     (prefer-method print-method ~a ~b)
     (prefer-method print-dup ~a ~b)))

(def pr-on
  @#'clojure.core/pr-on)

;; File

(defprint File [file w]
  (.write w "#file \"")
  (.write w (str/replace (.getPath file) "\"" "\\\""))
  (.write w "\""))

(defn read-file [^String s]
  (File. s))

(alter-var-root #'*data-readers* assoc 'file #'read-file)


;; Path

(defprint Path [path w]
  (.write w "#path \"")
  (.write w (str/replace (str path) "\"" "\\\""))
  (.write w "\""))

(defn read-path [^String s]
  (Path/of s (make-array String 0)))

(alter-var-root #'*data-readers* assoc 'path #'read-path)


;; arrays

(defprint boolean/1 [arr w]
  (.write w "#booleans ")
  (pr-on (vec arr) w))

(alter-var-root #'*data-readers* assoc 'booleans #'boolean-array)

(defprint byte/1 [arr w]
  (.write w "#bytes \"")
  (dotimes [i (alength arr)]
    (.write w (format "%02X" (Byte/toUnsignedInt (aget arr i)))))
  (.write w "\""))

(defn read-bytes [^String s]
  (let [cnt (quot (count s) 2)
        arr (byte-array cnt)]
    (dotimes [idx cnt]
      (let [i  (Integer/parseInt (subs s (* idx 2) (* (inc idx) 2)) 16)
            b (if (>= i 128)
                (byte (- i 256))
                (byte i))]
        (aset arr idx b)))
    arr))

(alter-var-root #'*data-readers* assoc 'bytes #'read-bytes)

(defprint char/1 [arr w]
  (.write w "#chars ")
  (pr-on (vec arr) w))

(alter-var-root #'*data-readers* assoc 'chars #'char-array)

(defprint short/1 [arr w]
  (.write w "#shorts ")
  (pr-on (vec arr) w))

(alter-var-root #'*data-readers* assoc 'shorts #'short-array)

(defprint int/1 [arr w]
  (.write w "#ints ")
  (pr-on (vec arr) w))

(alter-var-root #'*data-readers* assoc 'ints #'int-array)

(defprint long/1 [arr w]
  (.write w "#longs ")
  (pr-on (vec arr) w))

(alter-var-root #'*data-readers* assoc 'longs #'long-array)

(defprint float/1 [arr w]
  (.write w "#floats ")
  (pr-on (vec arr) w))

(alter-var-root #'*data-readers* assoc 'floats #'float-array)

(defprint double/1 [arr w]
  (.write w "#doubles ")
  (pr-on (vec arr) w))

(alter-var-root #'*data-readers* assoc 'doubles #'double-array)


;; #strings

(defprint String/1 [arr w]
  (.write w "#strings ")
  (pr-on (vec arr) w))

(defn read-strings [xs]
  (into-array String xs))

(alter-var-root #'*data-readers* assoc 'strings #'read-strings)


;; #objects & #array

(defn- print-array [arr w]
  (let [cls (class arr)]
    (if (and cls (.isArray cls))
      (@#'clojure.core/print-sequential "[" print-array " " "]" arr w)
      (pr-on arr w))))

(defprint Object/1 [arr w]
  (let [cls  (class arr)
        base (.componentType cls)]
    (cond
      (= Object base)
      (do
        (.write w "#objects ")
        (pr-on (vec arr) w))
      
      
      :else
      (do
        (.write w "#array ^")
        (if (= "java.lang" (.getPackageName cls))
          (.write w (subs (pr-str cls) (count "java.lang.")))
          (pr-on cls w))
        (.write w " ")
        (print-array arr w)))))

(alter-var-root #'*data-readers* assoc 'objects #'object-array)

(defn- read-array [vals]
  (let [class (:tag (meta vals))
        class (cond-> class
                (symbol? class) resolve)
        base  (Class/.componentType class)
        arr   ^Object/1 (make-array base (count vals))]
    (doseq [i (range (count vals))
            :let [x (nth vals i)]]
      (aset arr i
        (if (.isArray base)
          (read-array (vary-meta x assoc :tag base))
          x)))
    arr))

(alter-var-root #'*data-readers* assoc 'array #'read-array)


;; atom

(defprint Atom [a w]
  (.write w "#atom ")
  (pr-on @a w))

(alter-var-root #'*data-readers* assoc 'atom #'atom)


;; agent

(defprint Agent [a w]
  (.write w "#agent ")
  (pr-on @a w))

(alter-var-root #'*data-readers* assoc 'agent #'agent)


;; ref

(defprint Ref [a w]
  (.write w "#ref ")
  (pr-on @a w))

(alter-var-root #'*data-readers* assoc 'ref #'ref)


;; Volatile

(defprint Volatile [a w]
  (.write w "#volatile ")
  (pr-on @a w))

(alter-var-root #'*data-readers* assoc 'volatile #'volatile!)


;; promise

(defprint IPending [ref w]
  (.write w "#promise ")
  (if (realized? ref)
    (pr-on @ref w)
    (.write w ":<pending...>")))

(defn- read-promise [val]
  (if (= :<pending...> val)
    (promise)
    (deliver (promise) val)))

(alter-var-root #'*data-readers* assoc 'promise #'read-promise)

(prefer IPending IDeref)

(prefer ISeq IPending)

;; delay

(defprint Delay [ref w]
  (.write w "#delay ")
  (if (realized? ref)
    (pr-on @ref w)
    (.write w ":<pending...>")))

(defn- read-delay [val]
  (if (= :<pending...> val)
    (throw (ex-info "Can’t read back :<pending...> delay" {}))
    (doto (delay val)
      (deref))))

(alter-var-root #'*data-readers* assoc 'delay #'read-delay)


;; future

(defprint Future [ref w]
  (.write w "#future ")
  (if (.isDone ref)
    (pr-on (.get ref) w)
    (.write w ":<pending...>")))

(defn- read-future [val]
  (if (= :<pending...> val)
    (throw (ex-info "Can’t read back :<pending...> future" {}))
    (doto (future val)
      (deref))))

(alter-var-root #'*data-readers* assoc 'future #'read-future)

(prefer Future IPending)

(prefer Future IDeref)


;; queue

(defprint PersistentQueue [q w]
  (.write w "#queue ")
  (pr-on (vec q) w))

(defn- read-queue [xs]
  (into PersistentQueue/EMPTY xs))

(alter-var-root #'*data-readers* assoc 'queue #'read-queue)


;; Namespace

(defprint Namespace [n w]
  (.write w "#ns ")
  (.write w (str n)))

(defn- read-ns [sym]
  (the-ns sym))

(alter-var-root #'*data-readers* assoc 'ns #'read-ns)


;; Transients

(defprint PersistentVector$TransientVector [v w]
  (let [cnt (count v)]
    (.write w "#transient [")
    (dotimes [i cnt]
      (pr-on (nth v i) w)
      (when (< i (dec cnt))
        (.write w " ")))
    (.write w "]")))

(def ^:private ^Field array-map-array-field
  (doto (.getDeclaredField PersistentArrayMap$TransientArrayMap "array")
    (.setAccessible true)))

(defprint PersistentArrayMap$TransientArrayMap [m w]
  (let [cnt (count m)
        arr ^objects (.get array-map-array-field m)]
    (.write w "#transient {")
    (dotimes [i cnt]
      (pr-on (aget arr (-> i (* 2))) w)
      (.write w " ")
      (pr-on (aget arr (-> i (* 2) (+ 1))) w)
      (when (< i (dec cnt))
        (.write w ", ")))
    (.write w "}")))

(def ^:private ^Field hash-map-edit-field
  (doto (.getDeclaredField PersistentHashMap$TransientHashMap "edit")
    (.setAccessible true)))

(defprint PersistentHashMap$TransientHashMap [m w]
  (let [edit       ^AtomicReference (.get hash-map-edit-field m)
        edit-value (.get edit)
        m'         (persistent! m)]
    (.write w "#transient ")
    (pr-on m' w)
    (.set edit edit-value)))

(def ^:private ^Field set-impl-field
  (doto (.getDeclaredField ATransientSet "impl")
    (.setAccessible true)))

(defprint ATransientSet [s w]
  (let [m          ^PersistentHashMap$TransientHashMap (.get set-impl-field s)
        edit       ^AtomicReference (.get hash-map-edit-field m)
        edit-value (.get edit)
        m'         (persistent! m)
        cnt        (count m')]
    (.write w "#transient #{")
    (doseq [[k idx] (map vector (keys m') (range))]
      (pr-on k w)
      (when (< idx (dec cnt))
        (.write w " ")))
    (.write w "}")
    (.set edit edit-value)))

(alter-var-root #'*data-readers* assoc 'transient #'transient)

;; Throwable

;; java.time

(defprint-read-str Duration       duration         Duration/parse)
(defprint-read-str Instant        instant          Instant/parse)
(defprint-read-str LocalDate      local-date       LocalDate/parse)
(defprint-read-str LocalDateTime  local-date-time  LocalDateTime/parse)
(defprint-read-str LocalTime      local-time       LocalTime/parse)
(defprint-read-str MonthDay       month-day        MonthDay/parse)
(defprint-read-str OffsetDateTime offset-date-time OffsetDateTime/parse)
(defprint-read-str OffsetTime     offset-time      OffsetTime/parse)
(defprint-read-str Period         period           Period/parse)
(defprint-read-str Year           year             Year/parse)
(defprint-read-str YearMonth      year-month       YearMonth/parse)
(defprint-read-str ZonedDateTime  zoned-date-time  ZonedDateTime/parse)
(defprint-read-str ZoneId         zone-id          ZoneId/of)
(defprint-read-str ZoneOffset     zone-offset      ZoneOffset/of)

(defprint DayOfWeek [t w]
  (.write w "#day-of-week \"")
  (.write w (str/capitalize (str t)))
  (.write w "\""))

(defn read-day-of-week [s]
  (case s
    "Sunday"    DayOfWeek/SUNDAY
    "Monday"    DayOfWeek/MONDAY
    "Tuesday"   DayOfWeek/TUESDAY
    "Wednesday" DayOfWeek/WEDNESDAY
    "Thursday"  DayOfWeek/THURSDAY
    "Friday"    DayOfWeek/FRIDAY
    "Saturday"  DayOfWeek/SATURDAY))

(alter-var-root #'*data-readers* assoc 'day-of-week #'read-day-of-week)

(defprint Month [t w]
  (.write w "#month \"")
  (.write w (str/capitalize (str t)))
  (.write w "\""))

(defn read-month [s]
  (case s
    "January"   Month/JANUARY
    "February"  Month/FEBRUARY
    "March"     Month/MARCH
    "April"     Month/APRIL
    "May"       Month/MAY
    "June"      Month/JUNE
    "July"      Month/JULY
    "August"    Month/AUGUST
    "September" Month/SEPTEMBER
    "October"   Month/OCTOBER
    "November"  Month/NOVEMBER
    "December"  Month/DECEMBER))

(alter-var-root #'*data-readers* assoc 'month #'read-month)

(defprint ChronoUnit [t w]
  (.write w "#chrono-unit \"")
  (.write w (str t))
  (.write w "\""))

(defn read-chrono-unit [s]
  (case s
    "Nanos"     ChronoUnit/NANOS
    "Micros"    ChronoUnit/MICROS
    "Millis"    ChronoUnit/MILLIS
    "Seconds"   ChronoUnit/SECONDS
    "Minutes"   ChronoUnit/MINUTES
    "Hours"     ChronoUnit/HOURS
    "Half_days" ChronoUnit/HALF_DAYS
    "Days"      ChronoUnit/DAYS
    "Weeks"     ChronoUnit/WEEKS
    "Months"    ChronoUnit/MONTHS
    "Years"     ChronoUnit/YEARS
    "Decades"   ChronoUnit/DECADES
    "Centuries" ChronoUnit/CENTURIES
    "Millennia" ChronoUnit/MILLENNIA
    "Eras"      ChronoUnit/ERAS))

(alter-var-root #'*data-readers* assoc 'chrono-unit #'read-chrono-unit)

;; java.net

(defprint-read-str InetAddress inet-address InetAddress/ofLiteral .getHostAddress)
(defprint-read-str URI         uri          URI.)
(defprint-read-str URL         url          URL.)

;; java.util.concurrent.atomic

(defprint-read-value AtomicBoolean   atomic-boolean AtomicBoolean.   .get)
(defprint-read-value AtomicInteger   atomic-int     AtomicInteger.   .get)
(defprint-read-value AtomicLong      atomic-long    AtomicLong.      .get)
(defprint-read-value AtomicReference atomic-ref     AtomicReference. .get)

(defprint AtomicIntegerArray [a w]
  (.write w "#atomic-ints [")
  (dotimes [i (.length a)]
    (.write w (str (.get a i)))
    (when (< i (dec (.length a)))
      (.write w " ")))
  (.write w "]"))

(defn read-atomic-ints [xs]
  (AtomicIntegerArray. (int-array xs)))

(alter-var-root #'*data-readers* assoc 'atomic-ints #'read-atomic-ints)


(defprint AtomicLongArray [a w]
  (.write w "#atomic-longs [")
  (dotimes [i (.length a)]
    (.write w (str (.get a i)))
    (when (< i (dec (.length a)))
      (.write w " ")))
  (.write w "]"))

(defn read-atomic-longs [xs]
  (AtomicLongArray. (long-array xs)))

(alter-var-root #'*data-readers* assoc 'atomic-longs #'read-atomic-longs)


(defprint AtomicReferenceArray [a w]
  (.write w "#atomic-refs [")
  (dotimes [i (.length a)]
    (pr-on (.get a i) w)
    (when (< i (dec (.length a)))
      (.write w " ")))
  (.write w "]"))

(defn read-atomic-refs [xs]
  (AtomicReferenceArray. ^objects (into-array Object xs)))

(alter-var-root #'*data-readers* assoc 'atomic-refs #'read-atomic-refs)

;; Thread, Executors?
;; java.lang.ref
;; TimeUnit
;; Charset?
;; java.text *Format

(when (thread-bound? #'*data-readers*)
  (set! *data-readers* (.getRawRoot #'*data-readers*)))
