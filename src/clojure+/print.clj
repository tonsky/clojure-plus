(ns clojure+.print
  (:require
   [clojure.java.io :as io]
   [clojure.pprint :as pprint]
   [clojure.string :as str]
   [clojure+.util :as util])
  (:import
   [clojure.lang AFunction Agent Atom ATransientSet Compiler Delay IDeref IPending ISeq MultiFn Namespace PersistentQueue  PersistentArrayMap$TransientArrayMap PersistentHashMap PersistentHashMap$TransientHashMap PersistentVector$TransientVector Reduced Ref Var Volatile]
   [java.io File StringWriter Writer]
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

(def ^:private *catalogue
  (atom #{}))

(defn- pr-on [w x]
  (if *print-dup*
    (print-dup x w)
    (print-method x w))
  nil)

(defn- print-dup-call
  "Reader-independent representation used for print-dup: #=(f x). Compiler uses
   print-dup to embed constants into bytecode, and this form can be read back
   even if our readers are not installed"
  [f x ^Writer w]
  (.write w "#=(")
  (.write w (str f))
  (.write w " ")
  (print-dup x w)
  (.write w ")"))

(defmacro defliteral
  "dup-fmt, if provided, is a template for print-dup, e.g. \"#=(java.io.File. %s)\""
  [cls getter quoted-tag ctor & [dup-fmt]]
  (let [tag       (second quoted-tag)
        print-sym (symbol (str "print-" tag))
        dup-sym   (symbol (str "print-dup-" tag))
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

       ~(when dup-fmt
          (let [[before after] (str/split dup-fmt #"%s" 2)]
            `(defn ~dup-sym [~val-sym ^Writer w#]
               (.write w# ~before)
               (let [rep# (~getter ~val-sym)]
                 (if (string? rep#)
                   (.write w# (pr-str rep#))
                   (print-dup rep# w#)))
               (.write w# ~after))))

       (defn ~read-sym [s#]
         (~ctor s#))
     
       (swap! *catalogue conj {:class ~cls :tag ~quoted-tag :print (var ~print-sym) :dup ~(when dup-fmt `(var ~dup-sym)) :read (var ~read-sym)}))))

(defmacro defenum [cls quoted-tag values]
  (let [tag       (second quoted-tag)
        print-sym (symbol (str "print-" tag))
        dup-sym   (symbol (str "print-dup-" tag))
        read-sym  (symbol (str "read-" tag))]
    `(do
       (defn ~print-sym [v# ^Writer w#]
         (.write w# ~(str "#" tag " :"))
         (.write w# (str/lower-case (str v#))))

       (defn ~dup-sym [^Enum v# ^Writer w#]
         (.write w# ~(str "#=(" (.getName ^Class (resolve cls)) "/valueOf \""))
         (.write w# (.name v#))
         (.write w# "\")"))

       (defn ~read-sym [kw#]
         (case kw#
           ~@(for [sym values
                   kv  [(-> sym str str/lower-case keyword)
                        (symbol (str cls) (str sym))]]
               kv)))
     
       (swap! *catalogue conj {:class ~cls :tag ~quoted-tag :print (var ~print-sym) :dup (var ~dup-sym) :read (var ~read-sym)}))))

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

(defn make-print-dup-array [ctor f]
  (fn [arr ^Writer w]
    (print-dup-call ctor (f arr) w)))

(swap! *catalogue conj {:class (Class/forName "[B") :print #'print-bytes :dup (make-print-dup-array 'clojure.core/byte-array #(mapv long %)) :tag 'bytes :read #'read-bytes})


(defn make-print-array [tag]
  (fn [arr ^Writer w]
    (.write w "#")
    (.write w (str tag))
    (.write w " ")
    (pr-on w (vec arr))))

(swap! *catalogue conj {:class (Class/forName "[Z") :print (make-print-array 'booleans) :dup (make-print-dup-array 'clojure.core/boolean-array vec)                 :tag 'booleans :read #'boolean-array})
(swap! *catalogue conj {:class (Class/forName "[C") :print (make-print-array 'chars)    :dup (make-print-dup-array 'clojure.core/char-array #(String. ^chars %))    :tag 'chars    :read #'char-array})
(swap! *catalogue conj {:class (Class/forName "[S") :print (make-print-array 'shorts)   :dup (make-print-dup-array 'clojure.core/short-array #(mapv long %))        :tag 'shorts   :read #'short-array})
(swap! *catalogue conj {:class (Class/forName "[I") :print (make-print-array 'ints)     :dup (make-print-dup-array 'clojure.core/int-array #(mapv long %))          :tag 'ints     :read #'int-array})
(swap! *catalogue conj {:class (Class/forName "[J") :print (make-print-array 'longs)    :dup (make-print-dup-array 'clojure.core/long-array vec)                    :tag 'longs    :read #'long-array})
(swap! *catalogue conj {:class (Class/forName "[F") :print (make-print-array 'floats)   :dup (make-print-dup-array 'clojure.core/float-array #(mapv double %))      :tag 'floats   :read #'float-array})
(swap! *catalogue conj {:class (Class/forName "[D") :print (make-print-array 'doubles)  :dup (make-print-dup-array 'clojure.core/double-array vec)                  :tag 'doubles  :read #'double-array})

(defn read-strings [xs]
  (into-array String xs))

(defn print-dup-objects [arr ^Writer w]
  (.write w "#=(clojure.core/into-array #=(clojure.lang.RT/classForName ")
  (.write w (pr-str (.getName (.getComponentType (class arr)))))
  (.write w ") ")
  (print-dup (vec arr) w)
  (.write w ")"))

(swap! *catalogue conj {:class (Class/forName "[Ljava.lang.String;") :print (make-print-array 'strings) :dup #'print-dup-objects :tag 'strings :read #'read-strings})


(defn- print-array [arr w]
  (let [cls (class arr)]
    (if (and cls (.isArray cls))
      (@#'clojure.core/print-sequential "[" print-array " " "]" arr w)
      (pr-on w arr))))

(defn array-type-str ^String [^Class cls]
  (loop [cls cls
         dim 0]
    (if (.isArray cls)
      (recur (.getComponentType cls) (inc dim))
      (let [name (pr-str cls)
            name (if (and
                       (str/starts-with? name "java.lang.")
                       (= 9 (.lastIndexOf name ".")))
                   (subs name 10)
                   name)]
        (str name "/" dim)))))

(defn print-objects [arr ^Writer w]
  (let [cls  (class arr)
        name (array-type-str cls)]
    (cond
      (= "Object/1" name)
      (do
        (.write w "#objects ")
        (pr-on w (vec arr)))
      
      :else
      (do
        (.write w "#array ^")
        (.write w name)
        (.write w " ")
        (print-array arr w)))))

(swap! *catalogue conj {:class (Class/forName "[Ljava.lang.Object;") :print #'print-objects :dup #'print-dup-objects :tag 'objects :read #'object-array})


(util/if-clojure-version-gte "1.12.0"
  (defn read-array [vals]
    (let [class (:tag (meta vals))
          class (cond-> class
                  (symbol? class) resolve)
          base  (.getComponentType ^Class class)
          arr   ^{:tag "[Ljava.lang.Object;"} (make-array base (count vals))]
      (doseq [i (range (count vals))
              :let [x (nth vals i)]]
        (aset arr i
          (if (.isArray base)
            (read-array (vary-meta x assoc :tag base))
            x)))
      arr)))

(util/if-clojure-version-gte "1.12.0"
  (swap! *catalogue conj {:class (Class/forName "[Ljava.lang.Object;") :print #'print-objects :dup #'print-dup-objects :tag 'array :read #'read-array}))


;; refs

(defn make-print-ref [tag]
  (fn [ref ^Writer w]
    (.write w "#")
    (.write w (str tag))
    (.write w " ")
    (pr-on w @ref)))

(defn make-print-dup-ref [ctor]
  (fn [ref ^Writer w]
    (print-dup-call ctor @ref w)))

(swap! *catalogue conj {:class Atom     :print (make-print-ref 'atom)     :dup (make-print-dup-ref 'clojure.core/atom)      :tag 'atom     :read #'atom})
(swap! *catalogue conj {:class Agent    :print (make-print-ref 'agent)    :dup (make-print-dup-ref 'clojure.core/agent)     :tag 'agent    :read #'agent})
(swap! *catalogue conj {:class Ref      :print (make-print-ref 'ref)      :dup (make-print-dup-ref 'clojure.core/ref)       :tag 'ref      :read #'ref})
(swap! *catalogue conj {:class Volatile :print (make-print-ref 'volatile) :dup (make-print-dup-ref 'clojure.core/volatile!) :tag 'volatile :read #'volatile!})
(swap! *catalogue conj {:class Reduced  :print (make-print-ref 'reduced)  :dup (make-print-dup-ref 'clojure.core/reduced)   :tag 'reduced  :read #'reduced})


(defn print-promise [^IPending ref ^Writer w]
  (.write w "#promise ")
  (if (realized? ref)
    (pr-on w @ref)
    (.write w "<pending...>")))

(defn print-dup-promise [^IPending ref ^Writer w]
  (if (realized? ref)
    (do
      (.write w "#=(clojure.core/deliver #=(clojure.core/promise) ")
      (print-dup @ref w)
      (.write w ")"))
    (.write w "#=(clojure.core/promise)")))

(defn- read-promise [val]
  (if (= '<pending...> val)
    (promise)
    (deliver (promise) val)))

(prefer IPending IDeref)
(prefer ISeq IPending)
(prefer List IPending)
(swap! *catalogue conj {:class IPending :print print-promise :dup #'print-dup-promise :tag 'promise :read #'read-promise})


(defn print-delay [^Delay ref ^Writer w]
  (.write w "#delay ")
  (if (realized? ref)
    (pr-on w @ref)
    (.write w "<pending...>")))

(defn print-dup-delay [^Delay ref ^Writer w]
  (when-not (realized? ref)
    (throw (ex-info "Can’t print-dup <pending...> delay" {})))
  (.write w "#=(clojure.lang.Delay. ")
  (print-dup-call 'clojure.core/constantly @ref w)
  (.write w ")"))

(defn- read-delay [val]
  (if (= '<pending...> val)
    (throw (ex-info "Can’t read back <pending...> delay" {}))
    (doto (delay val)
      (deref))))

(swap! *catalogue conj {:class Delay :print print-delay :dup #'print-dup-delay :tag 'delay :read #'read-delay})


(defn print-future [^Future ref ^Writer w]
  (.write w "#future ")
  (if (.isDone ref)
    (pr-on w (.get ref))
    (.write w "<pending...>")))

(defn print-dup-future [^Future ref ^Writer w]
  (when-not (.isDone ref)
    (throw (ex-info "Can’t print-dup <pending...> future" {})))
  (.write w "#=(clojure.core/future-call ")
  (print-dup-call 'clojure.core/constantly (.get ref) w)
  (.write w ")"))

(defn- read-future [val]
  (if (= '<pending...> val)
    (throw (ex-info "Can’t read back <pending...> future" {}))
    (doto (future val)
      (deref))))

(prefer Future IPending)
(prefer Future IDeref)
(swap! *catalogue conj {:class Future :print print-future :dup #'print-dup-future :tag 'future :read #'read-future})


;; #function

(defn print-fn [^AFunction f ^Writer w]
  (.write w "#fn ")
  (.write w (-> f class .getName Compiler/demunge)))

(defn read-fn [sym]
  (let [cls  (-> sym str (str/split #"/") (->> (map #(Compiler/munge %)) (str/join "$") Class/forName))
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

(defn make-print-dup-transient
  "Same as print, but #=(clojure.core/transient ...) instead of #transient ..."
  [print]
  (fn [t ^Writer w]
    (let [sw (StringWriter.)]
      (binding [*print-dup* true]
        (print t sw))
      (.write w "#=(clojure.core/transient ")
      (.write w (subs (str sw) (count "#transient ")))
      (.write w ")"))))

(defn print-transient-vector [^PersistentVector$TransientVector v ^Writer w]
  (let [cnt (count v)]
    (.write w "#transient [")
    (dotimes [i cnt]
      (pr-on w (nth v i))
      (when (< i (dec cnt))
        (.write w " ")))
    (.write w "]")))

(swap! *catalogue conj {:class PersistentVector$TransientVector :print #'print-transient-vector :dup (make-print-dup-transient print-transient-vector) :tag 'transient :read #'transient})


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

(swap! *catalogue conj {:class PersistentArrayMap$TransientArrayMap :print #'print-transient-array-map :dup (make-print-dup-transient print-transient-array-map) :tag 'transient :read #'transient})


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

(swap! *catalogue conj {:class PersistentHashMap$TransientHashMap :print #'print-transient-hash-map :dup (make-print-dup-transient print-transient-hash-map) :tag 'transient :read #'transient})


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

(swap! *catalogue conj {:class ATransientSet :print #'print-transient-set :dup (make-print-dup-transient print-transient-set) :tag 'transient :read #'transient})


;; #queue

(defliteral PersistentQueue vec 'queue #(into PersistentQueue/EMPTY %) "#=(clojure.core/into #=(clojure.lang.Reflector/getStaticField \"clojure.lang.PersistentQueue\" \"EMPTY\") %s)")


;; java.io

(defliteral File .getPath 'file #(File. ^String %) "#=(java.io.File. %s)")


;; java.lang

(defn print-thread [^Thread t ^Writer w]
  (util/if-java-version-gte 21
    (when (.isVirtual t)
      (.write w "^:virtual ")))
  (.write w "#thread [")
  (pr-on w (util/if-java-version-gte 19 (.threadId t) (.getId t)))
  (.write w " ")
  (pr-on w (.getName t))
  (let [g (.getThreadGroup t)]
    (when (and g (not= g (.getThreadGroup (Thread/currentThread))))
      (.write w " ")
      (pr-on w (.getName g))))
  (.write w "]"))

(swap! *catalogue conj {:class Thread :print #'print-thread :tag 'thread})


;; java.lang.ref

(defliteral SoftReference .get 'soft-ref SoftReference. "#=(java.lang.ref.SoftReference. %s)")
(defliteral WeakReference .get 'weak-ref WeakReference. "#=(java.lang.ref.WeakReference. %s)")


;; java.net

(defliteral InetAddress .getHostAddress 'inet-address InetAddress/getByName "#=(java.net.InetAddress/getByName %s)")
(defliteral URI         str             'uri          URI.                  "#=(java.net.URI. %s)")
(defliteral URL         str             'url          URL.                  "#=(java.net.URL. %s)")



;; java.nio.charset

(defliteral Charset .name 'charset Charset/forName "#=(java.nio.charset.Charset/forName %s)")


;; java.nio.file

(defliteral Path str 'path #(.toPath (io/file %)) "#=(java.nio.file.Paths/get %s #=(clojure.core/make-array #=java.lang.String 0))")


;; java.time

(defliteral Duration       str 'duration         Duration/parse             "#=(java.time.Duration/parse %s)")
(defliteral Instant        str 'instant          Instant/parse              "#=(java.time.Instant/parse %s)")
(defliteral LocalDate      str 'local-date       LocalDate/parse            "#=(java.time.LocalDate/parse %s)")
(defliteral LocalDateTime  str 'local-date-time  LocalDateTime/parse        "#=(java.time.LocalDateTime/parse %s)")
(defliteral LocalTime      str 'local-time       LocalTime/parse            "#=(java.time.LocalTime/parse %s)")
(defliteral MonthDay       str 'month-day        MonthDay/parse             "#=(java.time.MonthDay/parse %s)")
(defliteral OffsetDateTime str 'offset-date-time OffsetDateTime/parse       "#=(java.time.OffsetDateTime/parse %s)")
(defliteral OffsetTime     str 'offset-time      OffsetTime/parse           "#=(java.time.OffsetTime/parse %s)")
(defliteral Period         str 'period           Period/parse               "#=(java.time.Period/parse %s)")
(defliteral Year           str 'year             Year/parse                 "#=(java.time.Year/parse %s)")
(defliteral YearMonth      str 'year-month       YearMonth/parse            "#=(java.time.YearMonth/parse %s)")
(defliteral ZonedDateTime  str 'zoned-date-time  ZonedDateTime/parse        "#=(java.time.ZonedDateTime/parse %s)")
(defliteral ZoneId         str 'zone-id          ZoneId/of                  "#=(java.time.ZoneId/of %s)")
(defliteral ZoneOffset     str 'zone-offset      #(ZoneOffset/of ^String %) "#=(java.time.ZoneOffset/of %s)")

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

(defliteral AtomicBoolean   .get 'atomic-boolean AtomicBoolean.   "#=(java.util.concurrent.atomic.AtomicBoolean. %s)")
(defliteral AtomicInteger   .get 'atomic-int     AtomicInteger.   "#=(java.util.concurrent.atomic.AtomicInteger. %s)")
(defliteral AtomicLong      .get 'atomic-long    AtomicLong.      "#=(java.util.concurrent.atomic.AtomicLong. %s)")
(defliteral AtomicReference .get 'atomic-ref     AtomicReference. "#=(java.util.concurrent.atomic.AtomicReference. %s)")

(defn print-atomic-ints [^AtomicIntegerArray a ^Writer w]
  (.write w "#atomic-ints [")
  (dotimes [i (.length a)]
    (.write w (str (.get a i)))
    (when (< i (dec (.length a)))
      (.write w " ")))
  (.write w "]"))

(defn print-dup-atomic-ints [^AtomicIntegerArray a ^Writer w]
  (.write w "#=(java.util.concurrent.atomic.AtomicIntegerArray. ")
  (print-dup-call 'clojure.core/int-array (mapv #(long (.get a (int %))) (range (.length a))) w)
  (.write w ")"))

(defn read-atomic-ints [xs]
  (AtomicIntegerArray. (int-array xs)))

(swap! *catalogue conj {:class AtomicIntegerArray :print #'print-atomic-ints :dup #'print-dup-atomic-ints :tag 'atomic-ints :read #'read-atomic-ints})


(defn print-atomic-longs [^AtomicLongArray a ^Writer w]
  (.write w "#atomic-longs [")
  (dotimes [i (.length a)]
    (.write w (str (.get a i)))
    (when (< i (dec (.length a)))
      (.write w " ")))
  (.write w "]"))

(defn print-dup-atomic-longs [^AtomicLongArray a ^Writer w]
  (.write w "#=(java.util.concurrent.atomic.AtomicLongArray. ")
  (print-dup-call 'clojure.core/long-array (mapv #(.get a (int %)) (range (.length a))) w)
  (.write w ")"))

(defn read-atomic-longs [xs]
  (AtomicLongArray. (long-array xs)))

(swap! *catalogue conj {:class AtomicLongArray :print #'print-atomic-longs :dup #'print-dup-atomic-longs :tag 'atomic-longs :read #'read-atomic-longs})


(defn print-atomic-refs [^AtomicReferenceArray a ^Writer w]
  (.write w "#atomic-refs [")
  (dotimes [i (.length a)]
    (pr-on w (.get a i))
    (when (< i (dec (.length a)))
      (.write w " ")))
  (.write w "]"))

(defn print-dup-atomic-refs [^AtomicReferenceArray a ^Writer w]
  (.write w "#=(java.util.concurrent.atomic.AtomicReferenceArray. ")
  (print-dup-call 'clojure.core/object-array (mapv #(.get a (int %)) (range (.length a))) w)
  (.write w ")"))

(defn read-atomic-refs [xs]
  (AtomicReferenceArray. ^objects (into-array Object xs)))

(swap! *catalogue conj {:class AtomicReferenceArray :print #'print-atomic-refs :dup #'print-dup-atomic-refs :tag 'atomic-refs :read #'read-atomic-refs})


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
     (doseq [{:keys [class print dup]} catalogue]
       (.addMethod ^MultiFn print-method class print)
       ;; Compiler uses print-dup to embed constants into bytecode, so it has
       ;; to be readable without our readers. No :dup means either system
       ;; print-dup is fine (ns, fn) or value can't be read back (multifn, thread)
       (when dup
         (.addMethod ^MultiFn print-dup class dup))
       (.addMethod ^MultiFn pprint/simple-dispatch class #(print % *out*))))))

(defn data-readers
  "List of all readers this library can install. Returns map of {symbol -> var}
   
   Possible opts:
   
     :include :: [sym ...] - list of tags to include (white list)
     :exclude :: [sym ...] - list of tags to exclude (black list)"
  ([]
   (data-readers {}))
  ([opts]
   (into {} (keep (fn [{:keys [tag read]}] (when read [tag read])) (catalogue opts)))))

(defn install-readers!
  "Install readers for most of Clojure built-in data structures.
   
   After running this, things like atoms and transients will readable by reader:
   
     (read-string \"#atom 123\")
     (read-string \"#transient [1 2 3]\")
   
   Readers are installed to both *data-readers* and default-data-readers.
   The latter makes them visible in all threads and binding frames, and to
   clojure.edn readers, too.
   
   Possible opts:
   
     :include :: [sym ...] - list of tags to include (white list)
     :exclude :: [sym ...] - list of tags to exclude (black list)"
  ([]
   (install-readers! {}))
  ([opts]
   (let [readers (data-readers opts)]
     (alter-var-root #'default-data-readers merge readers)
     (util/rebind-dynamic *data-readers* merge readers))))

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
