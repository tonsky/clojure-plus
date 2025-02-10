(ns clojure+.core)

(declare ^:private ^:dynamic *if+-syms)
  
(defn- if+-rewrite-cond-impl [cond]
  (clojure.core/cond
    (empty? cond)
    true
    
    (and
      (= :let (first cond))
      (empty? (second cond)))
    (if+-rewrite-cond-impl (nnext cond))
    
    (= :let (first cond))
    (let [[var val & rest] (second cond)
          sym                (gensym)]
      (vswap! *if+-syms conj [var sym])
      (list 'let [var (list 'clojure.core/vreset! sym val)]
        (if+-rewrite-cond-impl
          (cons 
            :let
            (cons rest
              (nnext cond))))))
    
    :else
    (list 'and
      (first cond)
      (if+-rewrite-cond-impl (next cond)))))

(defn- if+-rewrite-cond [cond]
  (binding [*if+-syms (volatile! [])]
    [(if+-rewrite-cond-impl cond) @*if+-syms]))

(defn- flatten-1 [xs]
  (vec
    (mapcat identity xs)))

(defmacro if+
  "Allows sharing local variables between condition and then clause.
      
   Use `:let [...]` form (not nested!) inside `and` condition and its bindings
   will be visible in later `and` clauses and inside `then` branch:
   
     (if+ (and
            (= 1 2)
            ;; same :let syntax as in doseq/for
            :let [x 3
                  y (+ x 4)]
            ;; x and y visible downstream
            (> y x))
       
       ;; “then” branch: x and y visible here!
       (+ x y 5)
       
       ;; “else” branch: no x nor y
       6)"
  ([cond then]
   `(if+ ~cond
      ~then
      nil))
  ([cond then else]
   (if (and
         (seq? cond)
         (or
           (= 'and (first cond))
           (= 'clojure.core/and (first cond))))
     (let [[cond' syms] (if+-rewrite-cond (next cond))]
       `(let ~(flatten-1
                (for [[_ sym] syms]
                  [sym '(volatile! nil)]))
          (if ~cond'
            (let ~(flatten-1
                    (for [[binding sym] syms]
                      [binding (list 'deref sym)]))
              ~then)
            ~else)))
     (list 'if cond then else))))

(defmacro when+
  "Allows sharing local variables between condition and body clause.
      
   Use `:let [...]` form (not nested!) inside `and` condition and its bindings
   will be visible in later `and` clauses and inside body:
   
     (when+ (and
              (= 1 2)
              ;; same :let syntax as in doseq/for
              :let [x 3
                    y (+ x 4)]
              ;; x and y visible downstream
              (> y x))
       
       ;; “then” branch: x and y visible here!
       (+ x y 5))"
  [cond & body]
  `(if+ ~cond
     (do
       ~@body)
     nil))

(defmacro cond+
  "Cond on steroids.
   
   Define new variables between conditions:
   
     (cond+
       false   :false
       :let    [x 1]
       (= 1 x) (str x)) ; => \"1\"
   
   Insert imperative code:
   
     (cond+
       (= 1 a) :false
       :do     (println a) ; will print 1
       :else   :true)
   
   Declare variables inside conditions, just like if+:
   
     (cond+
       (and
         (= 1 1)
         :let [x 2, y (+ x 1)]
         (> y x))
       [x y]) ;; => [2 3]"
  [& clauses]
  (when-some [[test expr & rest :as clause] clauses]
    (cond
      (= :do test)         `(do  ~expr (cond+ ~@rest))
      (= :let test)        `(let ~expr (cond+ ~@rest))
      (= 1 (count clause)) test
      :else                `(if+ ~test ~expr (cond+ ~@rest)))))
