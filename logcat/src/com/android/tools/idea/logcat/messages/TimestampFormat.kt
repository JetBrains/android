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

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

/**
 * Provides formatting for the timestamp
 */
internal enum class TimestampFormat(val format: (Instant, ZoneId) -> String) {
  NO_TIMESTAMP({ _, _ -> "" }),

  /**
   * 1970-01-01 04:00:01.000
   */
  DATETIME({ instant, zoneId -> DATE_TIME_FORMATTER.format(java.time.LocalDateTime.ofInstant(instant, zoneId)) }),

  /**
   * 04:00:01.000
   */
  TIME({ instant, zoneId -> TIME_FORMATTER.format(java.time.LocalDateTime.ofInstant(instant, zoneId)) }),
}

private val DATE_TIME_FORMATTER = DateTimeFormatterBuilder()
  .append(DateTimeFormatter.ISO_LOCAL_DATE)
  .appendLiteral(' ')
  .appendTime()
  .toFormatter(Locale.ROOT)

private val TIME_FORMATTER = DateTimeFormatterBuilder().appendTime().toFormatter(Locale.ROOT)

private fun DateTimeFormatterBuilder.appendTime(): DateTimeFormatterBuilder = this
  .appendValue(ChronoField.HOUR_OF_DAY, 2)
  .appendLiteral(':')
  .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
  .appendLiteral(':')
  .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
  .appendFraction(ChronoField.MILLI_OF_SECOND, 3, 3, true)
  .appendLiteral(' ')
