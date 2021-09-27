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
package com.android.tools.idea.logcat.messages

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
  .toFormatter(Locale.ROOT)

/**
 * Formats [LogCatMessage]'s into a [TextAccumulator]
 */
internal class MessageFormatter(private val logcatColors: LogcatColors, private val zoneId: ZoneId) {
  // Keeps track of the previous tag, so we can omit on consecutive lines
  // TODO(aalbert): This was borrowed from Pidcat. Should we do it too? Should we also do it for app?
  private var previousTag: String? = null

  // Keeps track of the max app name & tag, so we can align the log level & message. Pidcat allows 23 chars for the tag and doesn't show the
  // app name but the alignment makes it look much prettier.
  private var maxAppNameLength = 1
  private var maxTagNameLength = 1

  fun formatMessages(textAccumulator: TextAccumulator, messages: List<LogCatMessage>) {
    for (message in messages) {
      val header = message.header
      textAccumulator.accumulate(DATE_TIME_FORMATTER.format(LocalDateTime.ofInstant(header.timestamp, zoneId)))
      // According to /proc/sys/kernel/[pid_max/threads-max], the max values of pid/tid are 32768/57136
      textAccumulator.accumulate(" %6d-%-6d".format(header.pid, header.tid))

      val tag = if (previousTag == header.tag) "" else header.tag.also { previousTag = header.tag }
      if (maxTagNameLength < header.tag.length) {
        maxTagNameLength = header.tag.length
      }
      if (maxAppNameLength < header.appName.length) {
        maxAppNameLength = header.appName.length
      }

      textAccumulator.accumulate(" %-${maxTagNameLength}s".format(tag), logcatColors.getTagColor(header.tag), header.tag)
      textAccumulator.accumulate(" %-${maxAppNameLength}s".format(header.appName), hint = header.appName)
      textAccumulator.accumulate(" ${header.logLevel.priorityLetter} ", logcatColors.getLogLevelColor(header.logLevel))
      textAccumulator.accumulate("${message.message}\n")
    }
  }
}