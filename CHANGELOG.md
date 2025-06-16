# 1.6.0 - Jun 16, 2025

`clojure+.test`:

- Tests run with `clojure+.test/run` can be interrupted
- With uncaught exception, print line in test that caused it

`clojure+.hashp`:

- Lazy `#p` evaluation, should fix all the compilation errors of invalid forms

# 1.5.1 - Jun 4, 2025

`clojure+.hashp`:

- fix error printing dynamic fn invocations

# 1.5.0 - May 10, 2025

`clojure+.core.server`:

- Added `start-server`: start socket server with better defaults. Supports random port, port file, and log message when server is started.

`clojure+.test`:

- Add `:randomize?` option to `run`, true by default.
- Optionally override `:capture-output?` through `run` options
- Focus tests by setting `^:only` metadata to true

# 1.4.1 - May 8, 2025

- Fix special forms/macros handling in `#p` #11 #12
- Support Clojure 1.10.1+ #13 #14

# 1.4.0 - Apr 30, 2025

Introducing `clojure+.hashp`:

- Adds support for quick debugging with `#p` reader tag.

`clojure+.error`:

- Improved gray color used in exceptions

# 1.3.3 - Mar 31, 2025

- Add compatibility with Java 8 #7

# 1.3.2 - Mar 30, 2025

`clojure+.test`:

- Improved capture-output
- Improved reporting for `(is (not`, `(is (=` and `(is (not=` for 3+ arguments
- Show `clojure.data/diff` for `(is (=` and collections

`clojure+.core`:

- `print-class-tree` #6

# 1.3.1 - Mar 21, 2025

- Removed accidental `nil` output

# 1.3.0 - Mar 21, 2025

`clojure+.error`:

- Added `:trace-transform` option to config #4
- Added `*trace-transform*` dynamic variable #4
- Removed '\n' in front of non-reversed human-readable exceptions
- Added 'Caused by: ' to all causes in human-readable exceptions
- Removed grey color from file extensions (separates file name and line better)
- Added output color detection and control via `NO_COLOR` env var, `clojure-plus.color=true`/`false` java property #5

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

Introducing `clojure+.core`:

- `if+`
- `when+`
- `cond+`

Introducing `clojure+.walk`:

- A drop-in replacement for `clojure.walk` that does not recreate data structures if they didn’t change