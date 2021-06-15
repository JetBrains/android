/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.util.xmlb

import com.android.tools.idea.logcat.LogcatHeaderFormat
import com.android.tools.idea.logcat.LogcatHeaderFormat.TimestampFormat
import com.intellij.util.xmlb.Converter
import java.lang.StringBuilder

private const val SHOW = "show-"
private const val NO_SHOW = "no-"
private const val INDEX_TIMESTAMP = 0
private const val INDEX_PID = 1
private const val INDEX_PACKAGE_NAME = 2
private const val INDEX_TAG = 3

class LogcatHeaderFormatConverter : Converter<LogcatHeaderFormat>() {
  override fun toString(value: LogcatHeaderFormat): String {
    val sb = StringBuilder(value.timestampFormat.name)
    append(sb, value.showProcessId, "pid")
    append(sb, value.showPackageName, "packageName")
    append(sb, value.showTag, "tag")
    return sb.toString()
  }

  override fun fromString(value: String): LogcatHeaderFormat {
    val split = value.split('|')
    return LogcatHeaderFormat(
      TimestampFormat.valueOf(split[INDEX_TIMESTAMP]),
      split[INDEX_PID].contains(SHOW),
      split[INDEX_PACKAGE_NAME].contains(SHOW),
      split[INDEX_TAG].contains(SHOW),
    )
  }

  private fun append(sb: StringBuilder, isShown: Boolean, name: String) {
    sb.append('|')
    if (isShown) {
      sb.append(SHOW)
    }
    else {
      sb.append(NO_SHOW)
    }
    sb.append(name)
  }
}