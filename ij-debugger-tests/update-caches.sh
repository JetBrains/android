#!/bin/bash

ROOT=$(pwd | grep -o '.*/studio-main\+')
CACHE_DIR=$ROOT/prebuilts/tools/jps-build-caches

cd "$CACHE_DIR" || exit

# There are 2 files names "jps-bootstrap.classes.jar" that seem to be created on the fly and contain
# timestamps in them. If we delete these files, new copies will be created and we will have a
# phantom git diff.
find "kotlin.jvm-debugger.test_lib" -not -name "jps-bootstrap.classes.jar" -delete 2> /dev/null
rm -rf "kotlin.jvm-debugger.test_tests"

bazel run //tools/adt/idea/ij-debugger-tests:kotlin.jvm-debugger.test_lib_update_cache
bazel run //tools/adt/idea/ij-debugger-tests:kotlin.jvm-debugger.test.k2_lib_update_cache
bazel run --test_sharding_strategy=disabled //tools/adt/idea/ij-debugger-tests:stepping-k1-k2-jvm
bazel run --test_sharding_strategy=disabled //tools/adt/idea/ij-debugger-tests:evaluate-expression-k1-k2-jvm

find . -name "_remote.repositories" -delete
find . -name ".last.cleanup.marker" -delete
find . -name "*lastUpdated" -delete
find . -name "*.state.txt" -delete

git add -A
