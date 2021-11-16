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

import com.android.ddmlib.Log
import com.android.ddmlib.logcat.LogCatMessage
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import kotlin.text.RegexOption.IGNORE_CASE

/**
 * Filters a list of [LogCatMessage]s
 */
internal interface LogcatFilter {
  fun filter(messages: List<LogCatMessage>, zoneId: ZoneId = ZoneId.systemDefault()) =
    messages.filter { matches(LogcatMessageWrapper(it, zoneId)) }

  fun matches(message: LogcatMessageWrapper): Boolean
}

internal class EmptyFilter : LogcatFilter {
  override fun filter(messages: List<LogCatMessage>, zoneId: ZoneId) = messages

  override fun matches(message: LogcatMessageWrapper) = true
}

internal data class AndLogcatFilter(val filters: List<LogcatFilter>) : LogcatFilter {
  constructor(vararg filters: LogcatFilter) : this(filters.asList())

  override fun matches(message: LogcatMessageWrapper) = filters.all { it.matches(message) }
}

internal data class OrLogcatFilter(val filters: List<LogcatFilter>) : LogcatFilter {
  constructor(vararg filters: LogcatFilter) : this(filters.asList())

  override fun matches(message: LogcatMessageWrapper) = filters.any { it.matches(message) }
}

internal enum class LogcatFilterField {
  TAG {
    override fun getValue(message: LogcatMessageWrapper) = message.logCatMessage.header.tag
  },
  APP {
    override fun getValue(message: LogcatMessageWrapper) = message.logCatMessage.header.appName
  },
  MESSAGE {
    override fun getValue(message: LogcatMessageWrapper) = message.logCatMessage.message
  },
  LINE {
    override fun getValue(message: LogcatMessageWrapper) = message.logLine
  };

  abstract fun getValue(message: LogcatMessageWrapper): String
}

internal data class StringFilter(val string: String, val field: LogcatFilterField) : LogcatFilter {
  override fun matches(message: LogcatMessageWrapper) = field.getValue(message).contains(string, ignoreCase = true)
}

internal data class NegatedStringFilter(val string: String, val field: LogcatFilterField) : LogcatFilter {
  override fun matches(message: LogcatMessageWrapper) = !field.getValue(message).contains(string, ignoreCase = true)
}

internal data class RegexFilter(val string: String, val field: LogcatFilterField) : LogcatFilter {
  private val regex = string.toRegex(IGNORE_CASE)

  override fun matches(message: LogcatMessageWrapper) = regex.containsMatchIn(field.getValue(message))
}

internal data class NegatedRegexFilter(val string: String, val field: LogcatFilterField) : LogcatFilter {
  private val regex = string.toRegex(IGNORE_CASE)

  override fun matches(message: LogcatMessageWrapper) = !regex.containsMatchIn(field.getValue(message))
}

internal data class LevelFilter(val level: Log.LogLevel) : LogcatFilter {
  override fun matches(message: LogcatMessageWrapper) = message.logCatMessage.header.logLevel == level
}

internal data class FromLevelFilter(val level: Log.LogLevel) : LogcatFilter {
  override fun matches(message: LogcatMessageWrapper) = message.logCatMessage.header.logLevel.ordinal >= level.ordinal
}

internal data class ToLevelFilter(val level: Log.LogLevel) : LogcatFilter {
  override fun matches(message: LogcatMessageWrapper) = message.logCatMessage.header.logLevel.ordinal <= level.ordinal
}

internal data class AgeFilter(val age: Duration, private val clock: Clock) : LogcatFilter {
  override fun matches(message: LogcatMessageWrapper) =
    clock.millis() - message.logCatMessage.header.timestamp.toEpochMilli() <= age.toMillis()
}

/**
 * A special filter that matches the appName field in a [LogCatMessage] against a list of package names from the project.
 */
internal data class AppFilter(val packageNames: Set<String>) : LogcatFilter {
  override fun matches(message: LogcatMessageWrapper) =
    packageNames.isEmpty() || packageNames.contains(message.logCatMessage.header.appName)
}