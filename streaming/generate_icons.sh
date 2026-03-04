#!/bin/bash -ex

readonly PROG_DIR="$(cd "$(dirname "$0")" && pwd)"
readonly IDEA_DIR="$(cd "${PROG_DIR}/../../../idea" && pwd)"
readonly ADT_DIR="$(cd "${PROG_DIR}/.." && pwd)"

ln -sf "${ADT_DIR}" "${IDEA_DIR}/android"

export IntellijIconClassGeneratorConfig_MODULES="intellij.android.streaming"
"${IDEA_DIR}/platform/jps-bootstrap/jps-bootstrap.sh" "-Duser.dir=${IDEA_DIR}/android" "${IDEA_DIR}" intellij.platform.buildScripts.studioIcons org.jetbrains.intellij.build.images.GenerateIconClassesKt

unlink "${IDEA_DIR}/android" || true

# Extract icon definitions from the generated Java file and rewrite TemporaryIcons.kt
readonly JAVA_FILE="${PROG_DIR}/src/com/android/tools/idea/streaming/TemporaryIcons.java"
readonly KT_FILE="${PROG_DIR}/src/com/android/tools/idea/streaming/TemporaryIcons.kt"

if [ -f "$JAVA_FILE" ]; then
    cat << 'EOF' > "$KT_FILE"
/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.streaming

import com.intellij.ui.IconManager
import javax.swing.Icon

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "tools/adt/idea/streaming/generate_icons.sh" to update
 */
object TemporaryIcons {
  private fun load(path: String, cacheKey: Int, flags: Int): Icon {
    return IconManager.getInstance().loadRasterizedIcon(path, TemporaryIcons::class.java.classLoader, cacheKey, flags)
  }
EOF

    # Parse and convert lines like: public static final @NotNull Icon FitView = load("icons/fit-view.svg", -842529787, 0);
    # To:
    #   @JvmField
    #   val FIT_VIEW: Icon = load("icons/fit-view.svg", -842529787, 0)
    grep 'public static final @NotNull Icon' "$JAVA_FILE" | while read -r line; do
        # Extract the icon name (e.g. FitView)
        icon_name=$(echo "$line" | sed -E 's/.*Icon ([A-Za-z0-9_]+) =.*/\1/')

        # Convert CamelCase to CONSTANT_CASE
        kt_name=$(echo "$icon_name" | sed -r 's/([a-z0-9])([A-Z])/\1_\2/g' | tr '[:lower:]' '[:upper:]')

        # Extract the load expression (e.g. load("icons/fit-view.svg", -842529787, 0))
        load_expr=$(echo "$line" | sed -E 's/.*(load\(.*\));/\1/')

        echo "" >> "$KT_FILE"
        echo "  @JvmField" >> "$KT_FILE"
        echo "  val $kt_name: Icon = $load_expr" >> "$KT_FILE"
    done

    echo "}" >> "$KT_FILE"

    # Clean up generated Java source
    rm -rf "${PROG_DIR}/src/com/intellij"

    # Also clean up accidental platform changes
    git -C "${ADT_DIR}/artwork" checkout gen/icons/StudioIcons.java gen/icons/StudioIllustrations.java || true
fi
