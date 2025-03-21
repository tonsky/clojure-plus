# 1.3.0 - Mar 21, 2025

`clojure+.error`:

- Added `:trace-transform` option to config
- Added `*trace-transform*` dynamic variable
- Removed '\n' in front of non-reversed human-readable exceptions
- Added 'Caused by: ' to all causes in human-readable exceptions
- Removed grey color from file extensions (separates file name and line better)
- Added output color detection and control via `NO_COLOR` env var, `clojure-plus.color=true`/`false` java property

Introducing `clojure+.test`:

- capture output
- print time taken
- asserts for `=` and `not=`
- print test namespace
- drop `clojure.test` trace elements from exceptions
- universal `run` fn
- `:capture-output?` option

# 1.2.0 - Mar 9, 2025

- `clojure+.error` adds printing for Throwable
- Ensure that `clojure+.walk` converts lists to lists #2 #3

# 1.1.0 - Feb 26, 2025

- `clojure+.print` adds printing and reader support for many common Java and Clojure types

# 1.0.1 - Feb 10, 2025

- clj-kondo hooks

# 1.0.0 - Jan 28, 2025

Initial version:

- if+
- when+
- cond+
- clojure+.walk