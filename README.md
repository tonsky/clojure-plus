# Clojure Plus

A collection of utilities that improve Clojure experience.

## Using

Add this to deps.edn:

```clojure
io.github.tonsky/clojure-plus {:mvn/version "1.0.0"}
```

## Functions

### if+

Allows sharing local variables between condition and then clause.

Use `:let [...]` form (not nested!) inside `and` condition and its bindings will be visible in later `and` clauses and inside `then` branch:

```clojure
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
  6)
```

### when+

Same as `if+`, but wraps body in implicit `do`:


```clojure
(when+ (and
         (= 1 2)
         ;; same :let syntax as in doseq/for
         :let [x 3
               y (+ x 4)]
         ;; x and y visible downstream
         (> y x))
  
  ;; body: x and y visible here!
  (+ x y 5))
```

### cond+

Cond on steroids.
   
Define new variables between conditions:

```clojure
(cond+
  false   :false
  :let    [x 1]
  (= 1 x) (str x)) ; => \"1\"
```

Insert imperative code:

```clojure
(cond+
  (= 1 a) :false
  :do     (println a) ; will print 1 before continuing evaluating
  :else   :true)
```

Declare variables inside conditions, just like if+:

```clojure
(cond+
  (and
    (= 1 1)
    ;; declare new vars inside `and` condition
    :let [x 2
          y (+ x 1)]
    ;; use them downstream in the same condition
    (> y x))
  ;; matching branch sees them too
  [x y]) ;; => [2 3]
```

### clojure+.walk

A drop-in replacement for `clojure.walk` that does not recreate data structures if they didn’t change (result of transform funcion is `identical?`)

Normally, `clojure.walk` will create new map from scratch and copy `:a 1, :b 2` to it:

```clojure
(let [m {:a 1, :b 2}]
  (identical? m (clojure.walk/postwalk identity m)))

;; (into (empty m) (map identity m))
; => false
```

When the structure is deep and everything is recreated, it can be very taxing on GC.

Compare it to:

```clojure
(let [m {:a 1, :b 2}]
  (identical? m (clojure+.walk/postwalk identity m)))

; => true
```

`clojure+.walk` reuses existing maps and `update`s keys that has changed. This works significantly better for transforms that don’t touch some parts of the tree at all, but also utilizes structural sharing for cases when you only update few keys.

## License

Copyright © 2025 Nikita Prokopov

Licensed under [MIT](LICENSE).
