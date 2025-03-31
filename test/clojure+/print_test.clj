(ns clojure+.print-test
  (:require
   [clojure.java.io :as io]
   [clojure.pprint :as pprint]
   [clojure.string :as str]
   [clojure.test :as test :refer [are deftest is testing use-fixtures]]
   [clojure+.print :as print])
  (:import
   [clojure.lang Atom Agent ATransientSet Delay ExceptionInfo IDeref IPending ISeq Namespace PersistentQueue PersistentArrayMap$TransientArrayMap PersistentHashMap PersistentHashMap$TransientHashMap PersistentVector$TransientVector Reduced Ref Volatile]
   [java.io File]
   [java.lang.ref SoftReference WeakReference]
   [java.net InetAddress URI URL]
   [java.nio.charset Charset]
   [java.nio.file Path]
   [java.time DayOfWeek Duration Instant LocalDate LocalDateTime LocalTime Month MonthDay OffsetDateTime OffsetTime Period Year YearMonth ZonedDateTime ZoneId ZoneOffset]
   [java.time.temporal ChronoUnit]
   [java.util ArrayDeque ArrayList]
   [java.util.concurrent Future TimeUnit]
   [java.util.concurrent.atomic AtomicBoolean AtomicInteger AtomicIntegerArray AtomicLong AtomicLongArray AtomicReference AtomicReferenceArray]))

(use-fixtures :once
  (fn [f]
    (print/install!)
    (f)))

(deftest basics-test
  (are [x s] (= s (pr-str x))
    [1 2 3]                "[1 2 3]"
    (list 1 2 3)           "(1 2 3)"
    (map inc [1 2 3])      "(2 3 4)"
    (concat [1 2] [3])     "(1 2 3)"
    (cons 1 [2 3])         "(1 2 3)"
    (range 1 4)            "(1 2 3)"
    (doto (ArrayList.)
      (.add 1)
      (.add 2)
      (.add 3))            "[1 2 3]"
    (first {:a 1})         "[:a 1]"
    {:a 1 :b 2}            "{:a 1, :b 2}"
    #{:a :b :c}            "#{:c :b :a}"
    (sorted-map :a 1 :b 2) "{:a 1, :b 2}"
    (sorted-set :a :b :c)  "#{:a :b :c}"
    #'+                    "#'clojure.core/+"))

(deftest booleans-test
  (is (= "#booleans [true false true]"
        (pr-str (boolean-array [true false true]))))

  (is (= "#booleans []"
        (pr-str (boolean-array 0))))

  (let [arr (read-string "#booleans [true false true]")]
    (is (= boolean/1 (class arr)))
    (is (= [true false true] (vec arr)))))

(deftest bytes-test
  (let [_   (is (= "#bytes \"00010203649C7F80FF\"" (pr-str (byte-array [0 1 2 3 100 -100 127 -128 -1]))))
        arr (read-string "#bytes \"00010203649C7F80FF\"")
        _   (is (= byte/1 (class arr)))
        _   (is (= [0 1 2 3 100 -100 127 -128 -1] (vec arr)))
  
        _   (is (= "#bytes \"\"" (pr-str (byte-array 0))))
        arr (read-string "#bytes \"\"")
        _   (is (= byte/1 (class arr)))
        _   (is (= 0 (alength ^byte/1 arr)))]))

(deftest chars-test
  (is (= "#chars [\\a \\b \\c]"
        (pr-str (char-array [\a \b \c]))))

  (is (= "#chars []"
        (pr-str (char-array 0))))

  (let [arr (read-string "#chars [\\a \\b \\c]")]
    (is (= char/1 (class arr)))
    (is (= [\a \b \c] (vec arr)))))

(deftest shorts-test
  (is (= "#shorts [1 2 3]"
        (pr-str (short-array [1 2 3]))))

  (is (= "#shorts []"
        (pr-str (short-array 0))))

  (let [arr (read-string "#shorts [1 2 3]")]
    (is (= short/1 (class arr)))
    (is (= [1 2 3] (vec arr)))))

(deftest ints-test
  (is (= "#ints [1 2 3]"
        (pr-str (int-array [1 2 3]))))

  (is (= "#ints []"
        (pr-str (int-array 0))))

  (let [arr (read-string "#ints [1 2 3]")]
    (is (= int/1 (class arr)))
    (is (= [1 2 3] (vec arr)))))

(deftest longs-test
  (is (= "#longs [1 2 3]"
        (pr-str (long-array [1 2 3]))))

  (is (= "#longs []"
        (pr-str (long-array 0))))

  (let [arr (read-string "#longs [1 2 3]")]
    (is (= long/1 (class arr)))
    (is (= [1 2 3] (vec arr)))))

(deftest floats-test
  (is (= "#floats [1.0 2.0 3.5]"
        (pr-str (float-array [1.0 2.0 3.5]))))

  (is (= "#floats []"
        (pr-str (float-array 0))))

  (let [arr (read-string "#floats [1.0 2.0 3.5]")]
    (is (= float/1 (class arr)))
    (is (= [1.0 2.0 3.5] (vec arr)))))

(deftest doubles-test
  (is (= "#doubles [1.0 2.0 3.5]"
        (pr-str (double-array [1.0 2.0 3.5]))))

  (is (= "#doubles []"
        (pr-str (double-array 0))))

  (let [arr (read-string "#doubles [1.0 2.0 3.5]")]
    (is (= double/1 (class arr)))
    (is (= [1.0 2.0 3.5] (vec arr)))))

(deftest strings-test
  (is (= "#strings [\"a\" \"b\" \"c\"]"
        (pr-str (into-array String ["a" "b" "c"]))))

  (is (= "#strings []"
        (pr-str (make-array String 0))))

  (is (= "#strings [nil nil]"
        (pr-str (make-array String 2))))

  (let [arr (read-string "#strings [\"a\" \"b\" \"c\"]")]
    (is (= String/1 (class arr)))
    (is (= ["a" "b" "c"] (vec arr)))))

(deftest objects-test
  (is (= "#objects [\"a\" \"b\" \"c\"]"
        (pr-str (into-array Object ["a" "b" "c"]))))

  (is (= "#objects []"
        (pr-str (make-array Object 0))))

  (is (= "#objects [nil nil]"
        (pr-str (make-array Object 2))))

  (let [arr (read-string "#objects [\"a\" \"b\" \"c\"]")]
    (is (= Object/1 (class arr)))
    (is (= ["a" "b" "c"] (vec arr)))))

(deftest array-test
  (is (= "#array ^java.io.File/1 [#file \"a\" #file \"b\" #file \"c\"]"
        (pr-str (into-array File [(io/file "a") (io/file "b") (io/file "c")]))))

  (let [arr (read-string "#array ^java.io.File/1 [#file \"a\" #file \"b\" #file \"c\"]")]
    (is (= File/1 (class arr)))
    (is (= [(io/file "a") (io/file "b") (io/file "c")] (vec arr)))))

(deftest multi-array-test
  (is (= "#array ^String/2 [[\"a\"] [\"b\" \"c\"]]"
        (pr-str (into-array String/1 [(into-array String ["a"])
                                      (into-array String ["b" "c"])]))))

  (let [arr (read-string "#array ^String/2 [[\"a\"] [\"b\" \"c\"]]")]
    (is (= String/2 (class arr)))
    (is (= [["a"] ["b" "c"]] (mapv vec arr)))))

(deftest atom-test
  (is (= "#atom 123" (pr-str (atom 123))))
  (let [atom (read-string "#atom 123")]
    (is (instance? Atom atom))
    (is (= 123 @atom))))

(deftest agent-test
  (is (= "#agent 123" (pr-str (agent 123))))
  (let [agent (read-string "#agent 123")]
    (is (instance? Agent agent))
    (is (= 123 @agent))))

(deftest ref-test
  (is (= "#ref 123" (pr-str (ref 123))))
  (let [ref (read-string "#ref 123")]
    (is (instance? Ref ref))
    (is (= 123 @ref))))

(deftest volatile-test
  (is (= "#volatile 123" (pr-str (volatile! 123))))
  (let [volatile (read-string "#volatile 123")]
    (is (instance? Volatile volatile))
    (is (= 123 @volatile))))

(deftest reduced-test
  (is (= "#reduced 123" (pr-str (reduced 123))))
  (let [reduced (read-string "#reduced 123")]
    (is (instance? Reduced reduced))
    (is (= 123 @reduced))))

(deftest promise-test
  (is (= "#promise <pending...>" (pr-str (promise))))
  (let [promise (read-string "#promise <pending...>")]
    (is (instance? IPending promise))
    (is (not (realized? promise))))

  (is (= "#promise 123" (pr-str (doto (promise) (deliver 123)))))
  (let [promise (read-string "#promise 123")]
    (is (instance? IPending promise))
    (is (realized? promise))
    (is (= 123 @promise))))

(deftest delay-test
  (is (= "#delay <pending...>" (pr-str (delay))))
  (is (thrown-with-cause-msg? ExceptionInfo #"Can’t read back <pending\.\.\.> delay"
        (read-string "#delay <pending...>")))

  (is (= "#delay 123" (pr-str (doto (delay 123) (deref)))))
  (let [delay (read-string "#delay 123")]
    (is (instance? Delay delay))
    (is (realized? delay))
    (is (= 123 @delay))))

(deftest future-test
  (is (= "#future <pending...>" (pr-str (future (Thread/sleep 100) 123))))
  (is (thrown-with-cause-msg? ExceptionInfo #"Can’t read back <pending\.\.\.> future"
        (read-string "#future <pending...>")))

  (is (= "#future 123" (pr-str (doto (future 123) (deref)))))
  (let [future (read-string "#future 123")]
    (is (instance? Future future))
    (is (realized? future))
    (is (= 123 @future))))

(deftest fn-test
  (let [f1 (fn [x y] (+ x y))
        _  (is (re-matches #"#fn clojure\+\.print-test/fn--\d+/f1--\d+" (pr-str f1)))

        f2 (fn abc [x y] (+ x y))
        _  (is (re-matches #"#fn clojure\+\.print-test/fn--\d+/abc--\d+" (pr-str f2)))

        _  (is (= "#fn clojure.core/+" (pr-str +)))
        f3 (read-string "#fn clojure.core/+")
        _  (is (= 3 (f3 1 2)))]))

(deftest multifn-test
  (is (= "#multifn print-method" (pr-str print-method))))

(deftest ns-test
  (let [ns  (find-ns 'clojure+.print-test)
        _   (is (= "#ns clojure+.print-test" (pr-str ns)))
        ns' (read-string "#ns clojure+.print-test")
        _   (is (instance? Namespace ns'))
        _   (is (= ns ns'))]))

(deftest transient-vector-test
  (let [v  (transient [])
        _  (is (= "#transient []" (pr-str v)))
        v  (conj! v 1)
        _  (is (= "#transient [1]" (pr-str v)))
        v  (conj! v 2)
        v  (conj! v 3)
        _  (is (= "#transient [1 2 3]" (pr-str v)))
        v' (read-string "#transient [1 2 3]")
        _  (is (instance? PersistentVector$TransientVector v'))
        _  (is (= (persistent! v) (persistent! v')))]))

(deftest transient-array-map-test
  (let [m  (transient {})
        _  (is (= "#transient {}" (pr-str m)))
        m  (assoc! m :a 1)
        _  (is (= "#transient {:a 1}" (pr-str m)))
        m  (assoc! m :b 2)
        _  (is (= "#transient {:a 1, :b 2}" (pr-str m)))
        m' (read-string "#transient {:a 1, :b 2}")
        _  (is (instance? PersistentArrayMap$TransientArrayMap m'))
        _  (is (= (persistent! m) (persistent! m')))]))

(deftest transient-hash-map-test
  (let [m  (transient (into {} (map #(vector (keyword (str %1)) %2) "abcdefghi" (range))))
        _  (is (= "#transient {:e 4, :g 6, :c 2, :h 7, :b 1, :d 3, :f 5, :i 8, :a 0}" (pr-str m)))
        m  (assoc! m :j 9)
        _  (is (= "#transient {:e 4, :g 6, :c 2, :j 9, :h 7, :b 1, :d 3, :f 5, :i 8, :a 0}" (pr-str m)))
        m' (read-string "#transient {:a 0 :b 1 :c 2 :d 3 :e 4 :f 5 :g 6 :h 7 :i 8 :j 9}")
        _  (is (instance? PersistentHashMap$TransientHashMap m'))
        _  (is (= (persistent! m) (persistent! m')))]))

(deftest transient-hash-set-test
  (let [s  (transient #{})
        _  (is (= "#transient #{}" (pr-str s)))
        s  (conj! s 1)
        _  (is (= "#transient #{1}" (pr-str s)))
        s  (conj! s 2)
        s  (conj! s 3)
        _  (is (= "#transient #{1 3 2}" (pr-str s)))
        s' (read-string "#transient #{1 2 3}")
        _  (is (instance? ATransientSet s'))
        _  (is (= (persistent! s) (persistent! s')))]))

(deftest queue-test
  (let [q  (into PersistentQueue/EMPTY [1 2 3])
        _  (is (= "#queue [1 2 3]" (pr-str q)))
        q' (read-string "#queue [1 2 3]")
        _  (is (instance? PersistentQueue q'))
        _  (is (= q q'))
        _  (is (= [1 2 3] (vec q')))]))

(deftest file-test
  (is (= "#file \"/abc/x\\\"y\"" (pr-str (io/file "/abc/x\"y"))))
  (let [file (read-string "#file \"x\\\"y\"")]
    (is (instance? File file))
    (is (= "x\"y" (File/.getPath file)))))

(deftest thread-test
  (let [t (Thread/currentThread)
        _ (is (re-matches #"\#thread \[\d+ \"[^\"]+\"\]" (pr-str t)))

        t (Thread. "the \"thread\"")
        _ (is (= (str "#thread [" (print/if-version-gte 19 (.threadId t) (.getId t)) " \"the \\\"thread\\\"\"]") (pr-str t)))]
    (print/if-version-gte 21
      (let [t (-> (Thread/ofVirtual)
                (.name "abc")
                (.start ^Runnable #(+ 1 2)))
            _ (is (str/starts-with? (pr-str t) (str "^:virtual #thread [" (.threadId t) " \"abc\"")))

            _ (is (thrown-with-cause-msg? Exception #"No reader function for tag thread"
                    (read-string "#thread [123 \"name\"]")))]))))

(deftest soft-ref-test
  (let [o  (atom 123)
        a  (SoftReference. o)
        _  (is (= "#soft-ref #atom 123" (pr-str a)))
        a' (read-string "#soft-ref #atom 123")
        _  (is (instance? SoftReference a'))
        _  (is (= 123 @(SoftReference/.get a')))]))

(deftest weak-ref-test
  (let [o  (atom 123)
        a  (WeakReference. o)
        _  (is (= "#weak-ref #atom 123" (pr-str a)))
        a' (read-string "#weak-ref #atom 123")
        _  (is (instance? WeakReference a'))
        _  (is (= 123 @(WeakReference/.get a')))]))

(deftest inet-address-test
  (let [a  (InetAddress/getByName "127.0.0.1")
        _  (is (= "#inet-address \"127.0.0.1\"" (pr-str a)))
        a' (read-string "#inet-address \"127.0.0.1\"")
        _  (is (instance? InetAddress a'))
        _  (is (= a a'))

        b  (InetAddress/getByName "1080:0:0:0:8:800:200C:417A")
        _  (is (= "#inet-address \"1080:0:0:0:8:800:200c:417a\"" (pr-str b)))
        b' (read-string "#inet-address \"1080:0:0:0:8:800:200c:417a\"")
        _  (is (instance? InetAddress b'))
        _  (is (= b b'))]))

(deftest url-test
  (let [a  (URL. "https://www.example.com:1080/docs/resource1.html?q=\"escape\"")
        _  (is (= "#url \"https://www.example.com:1080/docs/resource1.html?q=\\\"escape\\\"\"" (pr-str a)))
        a' (read-string "#url \"https://www.example.com:1080/docs/resource1.html?q=\\\"escape\\\"\"")
        _  (is (instance? URL a'))
        _  (is (= a a'))]))

(deftest uri-test
  (let [a  (URI. "https://www.example.com:1080/docs/resource1.html")
        _  (is (= "#uri \"https://www.example.com:1080/docs/resource1.html\"" (pr-str a)))
        a' (read-string "#uri \"https://www.example.com:1080/docs/resource1.html\"")
        _  (is (instance? URI a'))
        _  (is (= a a'))]))

(deftest charset-test
  (let [a  (Charset/forName "UTF-8")
        _  (is (= "#charset \"UTF-8\"" (pr-str a)))
        a' (read-string "#charset \"UTF-8\"")
        _  (is (instance? Charset a'))
        _  (is (= a a'))]))

(deftest path-test
  (is (= "#path \"/abc/x\\\"y\"" (pr-str (.toPath (io/file "/abc/x\"y")))))
  (let [path (read-string "#path \"x\\\"y\"")]
    (is (instance? Path path))
    (is (= "x\"y" (str path)))))

(deftest duration-test
  (let [t  (-> (Duration/ofHours 12) (.plusMinutes 30) (.plusSeconds 59))
        _  (is (= "#duration \"PT12H30M59S\"" (pr-str t)))
        t' (read-string "#duration \"PT12H30M59S\"")
        _  (is (instance? Duration t'))
        _  (is (= t t'))]))

(deftest instant-test
  (let [t  (Instant/ofEpochMilli 1740020287703)
        _  (is (= "#instant \"2025-02-20T02:58:07.703Z\"" (pr-str t)))
        t' (read-string "#instant \"2025-02-20T02:58:07.703Z\"")
        _  (is (instance? Instant t'))
        _  (is (= t t'))]))

(deftest local-date-test
  (let [t  (LocalDate/parse "2025-02-20")
        _  (is (= "#local-date \"2025-02-20\"" (pr-str t)))
        t' (read-string "#local-date \"2025-02-20\"")
        _  (is (instance? LocalDate t'))
        _  (is (= t t'))]))

(deftest local-date-time-test
  (let [t  (LocalDateTime/parse "2025-02-20T02:58:07.703")
        _  (is (= "#local-date-time \"2025-02-20T02:58:07.703\"" (pr-str t)))
        t' (read-string "#local-date-time \"2025-02-20T02:58:07.703\"")
        _  (is (instance? LocalDateTime t'))
        _  (is (= t t'))]))

(deftest local-time-test
  (let [t  (LocalTime/parse "02:58:07.703")
        _  (is (= "#local-time \"02:58:07.703\"" (pr-str t)))
        t' (read-string "#local-time \"02:58:07.703\"")
        _  (is (instance? LocalTime t'))
        _  (is (= t t'))]))

(deftest month-day-test
  (let [t  (MonthDay/of 2 29)
        _  (is (= "#month-day \"--02-29\"" (pr-str t)))
        t' (read-string "#month-day \"--02-29\"")
        _  (is (instance? MonthDay t'))
        _  (is (= t t'))]))

(deftest offset-date-time-test
  (let [t  (OffsetDateTime/parse "2025-02-20T02:58:07.703Z")
        _  (is (= "#offset-date-time \"2025-02-20T02:58:07.703Z\"" (pr-str t)))
        t' (read-string "#offset-date-time \"2025-02-20T02:58:07.703Z\"")
        _  (is (instance? OffsetDateTime t'))
        _  (is (= t t'))]))

(deftest offset-time-test
  (let [t  (OffsetTime/parse "02:58:07.703Z")
        _  (is (= "#offset-time \"02:58:07.703Z\"" (pr-str t)))
        t' (read-string "#offset-time \"02:58:07.703Z\"")
        _  (is (instance? OffsetTime t'))
        _  (is (= t t'))]))

(deftest period-test
  (let [t  (-> (Period/ofYears 2) (.plusMonths 3) (.plusDays 4))
        _  (is (= "#period \"P2Y3M4D\"" (pr-str t)))
        t' (read-string "#period \"P2Y3M4D\"")
        _  (is (instance? Period t'))
        _  (is (= t t'))]))

(deftest year-test
  (let [t  (Year/of 2025)
        _  (is (= "#year \"2025\"" (pr-str t)))
        t' (read-string "#year \"2025\"")
        _  (is (instance? Year t'))
        _  (is (= t t'))]))

(deftest year-month-test
  (let [t  (YearMonth/of 2025 2)
        _  (is (= "#year-month \"2025-02\"" (pr-str t)))
        t' (read-string "#year-month \"2025-02\"")
        _  (is (instance? YearMonth t'))
        _  (is (= t t'))]))

(deftest zoned-date-time-test
  (let [t  (ZonedDateTime/parse "2025-02-20T02:58:07.703+01:00[Europe/Berlin]")
        _  (is (= "#zoned-date-time \"2025-02-20T02:58:07.703+01:00[Europe/Berlin]\"" (pr-str t)))
        t' (read-string "#zoned-date-time \"2025-02-20T02:58:07.703+01:00[Europe/Berlin]\"")
        _  (is (instance? ZonedDateTime t'))
        _  (is (= t t'))]))

(deftest zone-id-test
  (let [t  (ZoneId/of "Europe/Berlin")
        _  (is (= "#zone-id \"Europe/Berlin\"" (pr-str t)))
        t' (read-string "#zone-id \"Europe/Berlin\"")
        _  (is (instance? ZoneId t'))
        _  (is (= t t'))]))

(deftest zone-offset-test
  (let [t  (ZoneOffset/ofHoursMinutes 3 45)
        _  (is (= "#zone-offset \"+03:45\"" (pr-str t)))
        t' (read-string "#zone-offset \"+03:45\"")
        _  (is (instance? ZoneOffset t'))
        _  (is (= t t'))]))

(deftest day-of-week-test
  (let [t  DayOfWeek/WEDNESDAY
        _  (is (= "#day-of-week :wednesday" (pr-str t)))
        t' (read-string "#day-of-week :wednesday")
        _  (is (instance? DayOfWeek t'))
        _  (is (= t t'))]))

(deftest month-test
  (let [t  Month/FEBRUARY
        _  (is (= "#month :february" (pr-str t)))
        t' (read-string "#month :february")
        _  (is (instance? Month t'))
        _  (is (= t t'))]))

(deftest chrono-unit-test
  (let [t  ChronoUnit/SECONDS
        _  (is (= "#chrono-unit :seconds" (pr-str t)))
        t' (read-string "#chrono-unit :seconds")
        _  (is (instance? ChronoUnit t'))
        _  (is (= t t'))]))

(deftest time-unit-test
  (let [t  TimeUnit/SECONDS
        _  (is (= "#time-unit :seconds" (pr-str t)))
        t' (read-string "#time-unit :seconds")
        _  (is (instance? TimeUnit t'))
        _  (is (= t t'))]))

(deftest atomic-boolean-test
  (is (= "#atomic-boolean true" (pr-str (AtomicBoolean. true))))
  (is (= "#atomic-boolean false" (pr-str (AtomicBoolean. false))))
  (let [a' (read-string "#atomic-boolean false")]
    (is (instance? AtomicBoolean a'))
    (is (= false (AtomicBoolean/.get a')))))

(deftest atomic-int-test
  (let [a  (AtomicInteger. 123)
        _  (is (= "#atomic-int 123" (pr-str a)))
        a' (read-string "#atomic-int 123")
        _  (is (instance? AtomicInteger a'))
        _  (is (= 123 (AtomicInteger/.get a')))]))

(deftest atomic-long-test
  (let [a  (AtomicLong. 123)
        _  (is (= "#atomic-long 123" (pr-str a)))
        a' (read-string "#atomic-long 123")
        _  (is (instance? AtomicLong a'))
        _  (is (= 123 (AtomicLong/.get a')))]))

(deftest atomic-ref-test
  (let [o  (atom 123)
        a  (AtomicReference. o)
        _  (is (= "#atomic-ref #atom 123" (pr-str a)))
        a' (read-string "#atomic-ref #atom 123")
        _  (is (instance? AtomicReference a'))
        _  (is (= 123 @(AtomicReference/.get a')))]))

(deftest atomic-ints-test
  (let [a  (AtomicIntegerArray. (int-array []))
        _  (is (= "#atomic-ints []" (pr-str a)))
        a' (read-string "#atomic-ints []")
        _  (is (instance? AtomicIntegerArray a'))
        _  (is (= 0 (AtomicIntegerArray/.length a')))

        a  (AtomicIntegerArray. (int-array [1 2 3]))
        _  (is (= "#atomic-ints [1 2 3]" (pr-str a)))
        a' (read-string "#atomic-ints [1 2 3]")
        _  (is (instance? AtomicIntegerArray a'))
        _  (is (= 3 (AtomicIntegerArray/.length a')))
        _  (is (= 1 (AtomicIntegerArray/.get a' 0)))
        _  (is (= 2 (AtomicIntegerArray/.get a' 1)))
        _  (is (= 3 (AtomicIntegerArray/.get a' 2)))]))

(deftest atomic-longs-test
  (let [a  (AtomicLongArray. (long-array []))
        _  (is (= "#atomic-longs []" (pr-str a)))
        a' (read-string "#atomic-longs []")
        _  (is (instance? AtomicLongArray a'))
        _  (is (= 0 (AtomicLongArray/.length a')))

        a  (AtomicLongArray. (long-array [1 2 3]))
        _  (is (= "#atomic-longs [1 2 3]" (pr-str a)))
        a' (read-string "#atomic-longs [1 2 3]")
        _  (is (instance? AtomicLongArray a'))
        _  (is (= 3 (AtomicLongArray/.length a')))
        _  (is (= 1 (AtomicLongArray/.get a' 0)))
        _  (is (= 2 (AtomicLongArray/.get a' 1)))
        _  (is (= 3 (AtomicLongArray/.get a' 2)))]))

(deftest atomic-refs-test
  (let [a  (AtomicReferenceArray. ^objects (into-array Object []))
        _  (is (= "#atomic-refs []" (pr-str a)))
        a' (read-string "#atomic-refs []")
        _  (is (instance? AtomicReferenceArray a'))
        _  (is (= 0 (AtomicReferenceArray/.length a')))

        o1 (atom 1)
        o2 (atom 2)
        o3 (atom 3)
        a  (AtomicReferenceArray. ^objects (into-array Object [o1 o2 o3]))
        _  (is (= "#atomic-refs [#atom 1 #atom 2 #atom 3]" (pr-str a)))
        a' (read-string "#atomic-refs [#atom 1 #atom 2 #atom 3]")
        _  (is (instance? AtomicReferenceArray a'))
        _  (is (= 3 (AtomicReferenceArray/.length a')))
        _  (is (= 1 @(AtomicReferenceArray/.get a' 0)))
        _  (is (= 2 @(AtomicReferenceArray/.get a' 1)))
        _  (is (= 3 @(AtomicReferenceArray/.get a' 2)))]))

(deftest pprint-test
  (let [a [(atom 42) (io/file "/") (int-array [1 2 3])]
        _ (is (= "[#atom 42 #file \"/\" #ints [1 2 3]]\n"
                (with-out-str
                  (pprint/pprint a))))]))
