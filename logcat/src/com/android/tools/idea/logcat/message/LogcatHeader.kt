/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.logcat.message

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

private val EPOCH_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatterBuilder()
  .appendValue(ChronoField.INSTANT_SECONDS)
  .appendFraction(ChronoField.MILLI_OF_SECOND, 3, 3, true)
  .toFormatter(Locale.ROOT)

/**
 * The header part of a Logcat message
 */
data class LogcatHeader(
  val logLevel: LogLevel,
  val pid: Int,
  val tid: Int,
  val applicationId: String,
  val processName: String,
  val tag: String,
  val timestamp: Instant,
) {
  override fun toString(): String {
    val epoch = EPOCH_TIME_FORMATTER.format(timestamp)
    val priority = logLevel.priorityLetter
    return "$epoch: $priority/$tag($pid:$tid) $applicationId/$processName"
  }

  fun getAppName() = applicationId.ifEmpty { processName }
}

