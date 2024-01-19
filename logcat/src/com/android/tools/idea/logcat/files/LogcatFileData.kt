/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.logcat.files

import com.android.tools.idea.logcat.devices.Device
import com.android.tools.idea.logcat.filters.LogcatFilter.Companion.MY_PACKAGE
import com.android.tools.idea.logcat.message.LogcatMessage

/**
 * Logcat data loaded from file with optional metadata
 *
 * If the file is loaded from an Android Studio Save Logcat action, it will contain metadata. If it
 * was loaded from a device Logcat, metadata is null.
 */
internal class LogcatFileData(val metadata: Metadata?, val logcatMessages: List<LogcatMessage>) {
  class Metadata(val device: Device, val filter: String, val projectApplicationIds: Set<String>)
}

/**
 * Creates a filter string for a [LogcatFileData]
 *
 * If the saved filter contains `package:mine`, it will be replaced with a `package` clause that
 * will contain the explicit package(s) from the project where the Logcat was exported from.
 */
internal fun LogcatFileData?.safeGetFilter(): String {
  val filter = this?.metadata?.filter ?: return ""
  if (!filter.contains(MY_PACKAGE)) {
    return filter
  }
  return when {
    metadata.projectApplicationIds.isEmpty() -> filter.replace(MY_PACKAGE, "")
    metadata.projectApplicationIds.size == 1 ->
      filter.replace(MY_PACKAGE, "package:${metadata.projectApplicationIds.first()}")
    filter == MY_PACKAGE -> metadata.projectApplicationIds.joinToString(" ") { "package:$it" }
    else ->
      filter.replace(
        MY_PACKAGE,
        metadata.projectApplicationIds.joinToString(" | ", prefix = "(", postfix = ")") {
          "package:$it"
        },
      )
  }
}
