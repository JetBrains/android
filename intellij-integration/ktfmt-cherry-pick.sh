#!/usr/bin/env bash
set -eu

# This script is like git-cherry-pick but it uses a custom ktfmt-based
# merge driver to avoid formatting-related merge conflicts in Kotlin.

PROG_DIR="$(dirname "$0")"

KTFMT="$PROG_DIR/../../../../prebuilts/tools/common/ktfmt/ktfmt"
DRIVER_CMD="$PROG_DIR/ktfmt-merge-driver.sh %O %A %B %P $KTFMT"
ATTR_FILE="$PROG_DIR/ktfmt-merge-gitattributes.txt"

echo "Cherry-picking using ktfmt-based merge driver"
git \
  -c core.attributesFile="$ATTR_FILE" \
  -c merge.ktfmt.name="ktfmt merge driver" \
  -c merge.ktfmt.driver="$DRIVER_CMD" \
  cherry-pick "$@"
