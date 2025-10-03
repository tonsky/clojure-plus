#!/bin/bash
set -o errexit -o nounset -o pipefail
cd "`dirname $0`/.."

# bb -x user/-test-main # doesn't work https://github.com/babashka/babashka/issues/1867, workaround below
bb -e "(require 'user :reload) (user/-test-main nil)"
