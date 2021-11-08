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
package com.android.tools.idea.logcat.filters

import com.android.ddmlib.logcat.LogCatMessage
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

private val DATE_TIME_FORMATTER = DateTimeFormatterBuilder()
  .append(DateTimeFormatter.ISO_LOCAL_DATE)
  .appendLiteral(' ')
  .appendValue(ChronoField.HOUR_OF_DAY, 2)
  .appendLiteral(':')
  .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
  .appendLiteral(':')
  .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
  .appendFraction(ChronoField.MILLI_OF_SECOND, 3, 3, true)
  .appendLiteral(' ')
  .toFormatter(Locale.ROOT)

/**
 * Todo
 */
internal class LogcatMessageWrapper(val logCatMessage: LogCatMessage, zoneId: ZoneId = ZoneId.systemDefault()) {
  val logLine by lazy { toLine(zoneId) }

  /**
   * Canonical formatting of a [LogCatMessage] used when filtering without a specific field scope.
   *
   * See [Never use toString() for behaviour](https://java.christmas/2019/4)
   */
  private fun toLine(zoneId: ZoneId): String {
    val (logLevel, pid, tid, appName, tag, timestamp) = logCatMessage.header
    val datetime = DATE_TIME_FORMATTER.format(LocalDateTime.ofInstant(timestamp, zoneId))
    return "$datetime$pid-$tid $tag $appName ${logLevel.priorityLetter}: ${logCatMessage.message}"
  }
}