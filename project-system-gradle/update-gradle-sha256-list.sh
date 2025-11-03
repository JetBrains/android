#!/bin/bash

#
# Copyright (C) 2025 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
PROG_DIR=$(realpath "`dirname "$0"`")
WORKSPACE_DIR=$(realpath ${PROG_DIR}/../../../../)
URL="https://services.gradle.org/versions/all"
OUTPUT_DIR=$WORKSPACE_DIR/tools/adt/idea/project-system-gradle/resources/templates/project
OUTPUT_FILE="$OUTPUT_DIR/gradle-sha256-list.txt"

mkdir -p "$OUTPUT_DIR"
TEMP_FILE=$(mktemp)

if [ -f "$OUTPUT_FILE" ]; then
    OLD_DEFINITION_COUNT=$(grep -c ';' "$OUTPUT_FILE")
else
    OLD_DEFINITION_COUNT=0
fi

echo "Fetching and processing latest data..."
curl -s "$URL" | \
jq -r '.[] |
  .checksum
  + ";" +
  (
    .downloadUrl | split("/") | last
  )' \
> "$TEMP_FILE"

if [ -f "$OUTPUT_FILE" ] && \
   diff -q "$OUTPUT_FILE" "$TEMP_FILE" >/dev/null; then

    echo "Status: File is already up to date ($OUTPUT_FILE)."
    echo "No changes were made to the file or its timestamp."
else
    mv "$TEMP_FILE" "$OUTPUT_FILE"
    NEW_DEFINITION_COUNT=$(grep -c ';' "$OUTPUT_FILE")
    DEFINITIONS_ADDED=$((NEW_DEFINITION_COUNT - OLD_DEFINITION_COUNT))
    echo "Status: Data changed or file missing. File updated."
    echo "Total definitions in file: $NEW_DEFINITION_COUNT"

    if [ "$DEFINITIONS_ADDED" -gt 0 ]; then
        echo "**$DEFINITIONS_ADDED new definitions (hashes) were added.**"
    elif [ "$DEFINITIONS_ADDED" -lt 0 ]; then
        echo "$((-DEFINITIONS_ADDED)) definitions were removed/replaced."
    else
        echo "The content changed, but the total number of definitions remained the same."
    fi
fi
rm -f "$TEMP_FILE"