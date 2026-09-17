#!/usr/bin/env bash

# This file is used by ktfmt-cherry-pick.sh.
# This is a merge driver that avoids conflicts caused by Kotlin formatting differences.
# It works by running ktfmt on the intermediate file content during a 3-way merge.
# Prerequisite knowledge: https://git-scm.com/book/ms/v2/Git-Tools-Advanced-Merging

if [ $# -ne 5 ]; then
  echo "Wrong number of params received. This script should only be called from ktfmt-cherry-pick.sh." > /dev/stderr
  exit 1
fi

# These params are passed from ktfmt-cherry-pick.sh
BASE="$1"
OURS="$2"
THEIRS="$3"
PATH_NAME="$4"
KTFMT="$5"

FILE_NAME="${PATH_NAME##*/}"

TMP_BASE="tmp.base.$FILE_NAME"
TMP_THEIRS="tmp.theirs.$FILE_NAME"

cp "$BASE" "$TMP_BASE"
cp "$THEIRS" "$TMP_THEIRS"

# Reformat "base" and "theirs" so we can get a post-formatting diff betweeen the two.
"$KTFMT" "$TMP_BASE" "$TMP_THEIRS"

git merge-file "$OURS" "$TMP_BASE" "$TMP_THEIRS"
MERGE_RESULT=$?

rm "$TMP_BASE" "$TMP_THEIRS"

exit $MERGE_RESULT
