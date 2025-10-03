#!/bin/bash
set -o errexit -o nounset -o pipefail
cd "`dirname $0`"

./test_clj.sh
./test_bb.sh