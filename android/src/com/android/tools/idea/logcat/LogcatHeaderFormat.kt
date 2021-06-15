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
package com.android.tools.idea.logcat

import com.android.ddmlib.logcat.LogCatMessage
import com.android.tools.idea.logcat.LogcatHeaderFormat.TimestampFormat.DATETIME
import com.android.tools.idea.logcat.LogcatHeaderFormat.TimestampFormat.NONE
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
  .toFormatter(Locale.ROOT)
private val EPOCH_TIME_FORMATTER = DateTimeFormatterBuilder()
  .appendValue(ChronoField.INSTANT_SECONDS)
  .appendFraction(ChronoField.MILLI_OF_SECOND, 3, 3, true)
  .toFormatter(Locale.ROOT)
private const val CONTINUATION_LINE = "\n    "

data class LogcatHeaderFormat(
  val timestampFormat: TimestampFormat,
  val showProcessId: Boolean,
  val showPackageName: Boolean,
  val showTag: Boolean,
) {
  constructor() : this(DATETIME, true, true, true)

  private val format: String = createFormatString()

  fun formatMessage(logCatMessage: LogCatMessage, zoneId: ZoneId): String {
    val header = logCatMessage.header
    val timestamp = header.timestamp
    val timestampString: String? = when (timestampFormat) {
      TimestampFormat.EPOCH -> EPOCH_TIME_FORMATTER.format(timestamp)
      DATETIME -> DATE_TIME_FORMATTER.format(LocalDateTime.ofInstant(timestamp, zoneId))
      NONE -> null
    }
    val processIdThreadId = header.pid.toString() + "-" + header.tid
    val priority = header.logLevel.priorityLetter

    // Replacing spaces with non breaking spaces makes parsing easier later
    val tag = header.tag.replace(' ', '\u00A0')
    return format.format(
      timestampString,
      processIdThreadId,
      header.appName,
      priority,
      tag,
      logCatMessage.message.replace("\n".toRegex(), CONTINUATION_LINE))
  }

  private fun createFormatString(): String {
    val builder = StringBuilder()
    if (timestampFormat != NONE) {
      builder.append("%1\$s ")
    }
    if (showProcessId) {
      // Slightly different formatting if we show BOTH PID and package instead of one or the other
      builder.append("%2\$s").append(if (showPackageName) '/' else ' ')
    }
    if (showPackageName) {
      builder.append("%3\$s ")
    }
    builder.append("%4\$c")
    if (showTag) {
      builder.append("/%5\$s")
    }
    builder.append(": %6\$s")
    return builder.toString()
  }

  enum class TimestampFormat {
    NONE,
    EPOCH,
    DATETIME,
  }
}