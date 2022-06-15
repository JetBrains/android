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

import com.android.tools.idea.logcat.PackageNamesProvider
import com.android.tools.idea.logcat.SYSTEM_HEADER
import com.android.tools.idea.logcat.message.LogLevel
import com.android.tools.idea.logcat.message.LogLevel.ASSERT
import com.android.tools.idea.logcat.message.LogLevel.ERROR
import com.android.tools.idea.logcat.message.LogcatMessage
import com.intellij.psi.impl.source.tree.PsiErrorElementImpl
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import java.util.regex.PatternSyntaxException

/**
 * The top level filter that prepares and executes a [LogcatFilter]
 */
internal class LogcatMasterFilter(private val logcatFilter: LogcatFilter?) {

  fun filter(messages: List<LogcatMessage>, zoneId: ZoneId = ZoneId.systemDefault()): List<LogcatMessage> {
    if (logcatFilter == null) {
      return messages
    }
    logcatFilter.prepare()
    return messages.filter { it.header === SYSTEM_HEADER || logcatFilter.matches(LogcatMessageWrapper(it, zoneId)) }
  }
}

/**
 * Matches a [LogcatMessage]
 */
internal interface LogcatFilter {
  /**
   * Prepare the filter.
   *
   * Some filters need to perform some initial setup before running. To avoid doing the setup for each message, the [LogcatMasterFilter]
   * wil call [#prepare] once for each batch of messages.
   */
  fun prepare() {}

  fun matches(message: LogcatMessageWrapper): Boolean

  companion object {
    const val MY_PACKAGE = "package:mine"
  }
}

internal data class AndLogcatFilter(val filters: List<LogcatFilter>) : LogcatFilter {
  constructor(vararg filters: LogcatFilter) : this(filters.asList())

  override fun prepare() {
    filters.forEach(LogcatFilter::prepare)
  }

  override fun matches(message: LogcatMessageWrapper) = filters.all { it.matches(message) }
}

internal data class OrLogcatFilter(val filters: List<LogcatFilter>) : LogcatFilter {
  constructor(vararg filters: LogcatFilter) : this(filters.asList())

  override fun prepare() {
    filters.forEach(LogcatFilter::prepare)
  }

  override fun matches(message: LogcatMessageWrapper) = filters.any { it.matches(message) }
}

internal enum class LogcatFilterField {
  TAG {
    override fun getValue(message: LogcatMessageWrapper) = message.logCatMessage.header.tag
  },
  APP {
    override fun getValue(message: LogcatMessageWrapper) = message.logCatMessage.header.getAppName()
  },
  MESSAGE {
    override fun getValue(message: LogcatMessageWrapper) = message.logCatMessage.message
  },
  LINE {
    override fun getValue(message: LogcatMessageWrapper) = message.logLine
  },
  IMPLICIT_LINE {
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

internal data class ExactStringFilter(val string: String, val field: LogcatFilterField) : LogcatFilter {
  override fun matches(message: LogcatMessageWrapper) = field.getValue(message) == string
}

internal data class NegatedExactStringFilter(val string: String, val field: LogcatFilterField) : LogcatFilter {
  override fun matches(message: LogcatMessageWrapper) = field.getValue(message) != string
}

internal data class RegexFilter(val string: String, val field: LogcatFilterField) : LogcatFilter {
  private val regex = try {
    string.toRegex()
  }
  catch (e: PatternSyntaxException) {
    throw LogcatFilterParseException(PsiErrorElementImpl("Invalid regular expression: $string"))
  }

  override fun matches(message: LogcatMessageWrapper) = regex.containsMatchIn(field.getValue(message))
}

internal data class NegatedRegexFilter(val string: String, val field: LogcatFilterField) : LogcatFilter {
  private val regex = try {
    string.toRegex()
  }
  catch (e: PatternSyntaxException) {
    throw LogcatFilterParseException(PsiErrorElementImpl("Invalid regular expression: $string"))
  }

  override fun matches(message: LogcatMessageWrapper) = !regex.containsMatchIn(field.getValue(message))
}

internal data class LevelFilter(val level: LogLevel) : LogcatFilter {
  override fun matches(message: LogcatMessageWrapper) = message.logCatMessage.header.logLevel >= level
}

internal data class AgeFilter(val age: Duration, private val clock: Clock) : LogcatFilter {
  override fun matches(message: LogcatMessageWrapper) =
    clock.millis() - message.logCatMessage.header.timestamp.toEpochMilli() <= age.toMillis()
}

/**
 * A special filter that matches the appName field in a [LogcatMessage] against a list of package names from the project.
 */
internal class ProjectAppFilter(private val packageNamesProvider: PackageNamesProvider) : LogcatFilter {
  private var packageNames: Set<String> = emptySet()
  private lateinit var packageNamesRegex: Regex

  override fun prepare() {
    packageNames = packageNamesProvider.getPackageNames()
    packageNamesRegex = packageNames.joinToString("|") { it.replace(".", "\\.") }.toRegex()
  }

  override fun matches(message: LogcatMessageWrapper): Boolean {
    val header = message.logCatMessage.header
    return packageNames.contains(header.getAppName())
           || (header.logLevel >= ERROR && packageNamesRegex.containsMatchIn(message.logCatMessage.message))
  }

  override fun equals(other: Any?) = other is ProjectAppFilter && packageNamesProvider == other.packageNamesProvider

  override fun hashCode() = packageNamesProvider.hashCode()
}

/*
  A JVM crash looks like:

    2022-04-19 10:20:30.892 13253-13253/com.example.nativeapplication E/AndroidRuntime: FATAL EXCEPTION: main
      Process: com.example.nativeapplication, PID: 13253
      java.lang.RuntimeException: ...
        at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:3449)
        at android.app.ActivityThread.handleLaunchActivity(ActivityThread.java:3601)
    etc

  A native crash looks like:

  2022-04-19 10:24:34.051 13445-13445/com.example.nativeapplication A/libc: Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0 in tid 13445 (tiveapplication), pid 13445 (tiveapplication)
  2022-04-19 10:24:34.092 13474-13474/? A/DEBUG: *** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***
  2022-04-19 10:24:34.092 13474-13474/? A/DEBUG: Build fingerprint: 'google/sdk_gphone_x86_64/generic_x86_64_arm64:11/RSR1.201211.001/7027799:user/release-keys'
  2022-04-19 10:24:34.092 13474-13474/? A/DEBUG: Revision: '0'
  2022-04-19 10:24:34.092 13474-13474/? A/DEBUG: ABI: 'x86_64'
  2022-04-19 10:24:34.095 13474-13474/? A/DEBUG: Timestamp: 2022-04-19 10:24:34-0700
  etc
*/
internal object CrashFilter : LogcatFilter {
  override fun matches(message: LogcatMessageWrapper): Boolean {
    val header = message.logCatMessage.header
    val level = header.logLevel
    val tag = header.tag
    return (level == ERROR && tag == "AndroidRuntime" && message.logCatMessage.message.startsWith("FATAL EXCEPTION"))
           || (level == ASSERT && (tag == "DEBUG" || tag == "libc"))
  }
}

private val EXCEPTION_LINE_PATTERN = Regex("\n\\s*at .+\\(.+\\)\n")

internal object StackTraceFilter : LogcatFilter {
  override fun matches(message: LogcatMessageWrapper): Boolean = EXCEPTION_LINE_PATTERN.find(message.logCatMessage.message) != null
}
