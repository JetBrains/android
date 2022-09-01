#!/bin/bash

readonly SCRIPT_DIR="$(dirname "$0")"

if [ -z "$1" ]; then
  echo "Script for diffing new AGP model file drops"
  echo ""
  echo "usage: $0 SEARCH_SUFFIX [COMPARE_SUFFIX]"
  echo "e.g. to compare against current [suffix _V2.txt]: $0 _Agp_7_4_Gradle_7_5_V2.txt"
  echo "e.g. to compare two versions:: $0 _Agp_7_4_Gradle_7_5_V2.txt _Agp_7_3_Gradle_7_4_V2.txt"
  exit 1
fi

readonly SUFFIX="$1"
if [ -z "$2" ]; then
  readonly COMPARE_SUFFIX=_V2.txt
else
  readonly COMPARE_SUFFIX="$2"
fi
set -u

for file in $SCRIPT_DIR/*$SUFFIX; do
  git --no-pager diff --no-index $file ${file%$SUFFIX}$COMPARE_SUFFIX
done
