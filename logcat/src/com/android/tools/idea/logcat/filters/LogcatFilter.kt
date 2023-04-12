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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.logcat.BUNDLE_NAME
import com.android.tools.idea.logcat.LogcatBundle.message
import com.android.tools.idea.logcat.PackageNamesProvider
import com.android.tools.idea.logcat.SYSTEM_HEADER
import com.android.tools.idea.logcat.message.LogLevel
import com.android.tools.idea.logcat.message.LogLevel.ASSERT
import com.android.tools.idea.logcat.message.LogLevel.ERROR
import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.logcat.settings.AndroidLogcatSettings
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.TextRange.EMPTY_RANGE
import com.intellij.openapi.util.text.Strings
import com.intellij.psi.impl.source.tree.PsiErrorElementImpl
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.annotations.VisibleForTesting
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import java.util.regex.PatternSyntaxException

private const val STUDIO_SPAM_PREFIX = "studio."

/**
 * The top level filter that prepares and executes a [LogcatFilter]
 */
internal class LogcatMasterFilter(private val logcatFilter: LogcatFilter?) {
  private val settings = AndroidLogcatSettings.getInstance()
  private val ignoreSpam = StudioFlags.LOGCAT_IGNORE_STUDIO_SPAM_TAGS.get()

  fun filter(messages: List<LogcatMessage>, zoneId: ZoneId = ZoneId.systemDefault()): List<LogcatMessage> {
    if (logcatFilter == null) {
      return messages.filter { !it.isSpam() }
    }
    logcatFilter.prepare()
    return messages.filter {
      it.header === SYSTEM_HEADER || (logcatFilter.matches(LogcatMessageWrapper(it, zoneId)) && !it.isSpam())
    }
  }

  private fun LogcatMessage.isSpam() =
    settings.ignoredTags.contains(header.tag) || (ignoreSpam && header.tag.startsWith(STUDIO_SPAM_PREFIX))
}
/**
 * Matches a [LogcatMessage]
 */
internal abstract class LogcatFilter(open val textRange: TextRange) {
  open val filterName: String? = null

  abstract val displayText: String

  /**
   * Prepare the filter.
   *
   * Some filters need to perform some initial setup before running. To avoid doing the setup for each message, the [LogcatMasterFilter]
   * wil call [#prepare] once for each batch of messages.
   */
  open fun prepare() {}

  abstract fun matches(message: LogcatMessageWrapper): Boolean

  open fun findFilterForOffset(offset: Int): LogcatFilter? {
    return if (textRange.contains(offset)) this else null
  }

  companion object {
    const val MY_PACKAGE = "package:mine"
  }
}

internal abstract class ParentFilter(children: List<LogcatFilter>)
  : LogcatFilter(TextRange(children.first().textRange.startOffset, children.last().textRange.endOffset)) {
  open val filters: List<LogcatFilter> = children

  override val filterName: String? = children.mapNotNull { it.filterName }.lastOrNull()

  override val displayText: String = ""

  override fun prepare() {
    filters.forEach(LogcatFilter::prepare)
  }

  override fun findFilterForOffset(offset: Int): LogcatFilter? {
    return if (textRange.contains(offset)) filters.firstNotNullOfOrNull { it.findFilterForOffset(offset) } else null
  }
}

internal data class AndLogcatFilter(override val filters: List<LogcatFilter>) : ParentFilter(filters) {
  constructor(vararg filters: LogcatFilter) : this(filters.asList())

  override fun matches(message: LogcatMessageWrapper) = filters.all { it.matches(message) }
}

internal data class OrLogcatFilter(override val filters: List<LogcatFilter>) : ParentFilter(filters) {
  constructor(vararg filters: LogcatFilter) : this(filters.asList())

  override fun matches(message: LogcatMessageWrapper) = filters.any { it.matches(message) }
}

internal enum class LogcatFilterField(val displayName: String) {
  TAG(message("logcat.filter.completion.hint.key.tag")) {
    override fun getValue(message: LogcatMessageWrapper) = message.logcatMessage.header.tag
  },
  APP(message("logcat.filter.completion.hint.key.package")) {
    override fun getValue(message: LogcatMessageWrapper) = message.logcatMessage.header.applicationId
  },
  MESSAGE(message("logcat.filter.completion.hint.key.message")) {
    override fun getValue(message: LogcatMessageWrapper) = message.logcatMessage.message
  },
  LINE(message("logcat.filter.completion.hint.key.line")) {
    override fun getValue(message: LogcatMessageWrapper) = message.logLine
  },
  IMPLICIT_LINE(message("logcat.filter.completion.hint.key.line")) {
    override fun getValue(message: LogcatMessageWrapper) = message.logLine
  },
  PROCESS(message("logcat.filter.completion.hint.key.process")) {
    override fun getValue(message: LogcatMessageWrapper) = message.logcatMessage.header.processName
  },
  ;

  abstract fun getValue(message: LogcatMessageWrapper): String
}

internal abstract class FieldFilter(
  string: String,
  field: LogcatFilterField,
  override val textRange: TextRange,
  @PropertyKey(resourceBundle = BUNDLE_NAME) stringResource: String,
) : LogcatFilter(textRange) {
  override val displayText: String = message(stringResource, field.displayName, "'${string}'")

}

internal data class StringFilter(
  val string: String,
  val field: LogcatFilterField,
  override val textRange: TextRange,
) : FieldFilter(string, field, textRange, "logcat.filter.completion.hint.key") {
  override fun matches(message: LogcatMessageWrapper) = field.getValue(message).contains(string, ignoreCase = true)
}

internal data class NegatedStringFilter(
  val string: String,
  val field: LogcatFilterField,
  override val textRange: TextRange,
) : FieldFilter(string, field, textRange, "logcat.filter.completion.hint.key.negated") {
  override fun matches(message: LogcatMessageWrapper) = !field.getValue(message).contains(string, ignoreCase = true)
}

internal data class ExactStringFilter(
  val string: String,
  val field: LogcatFilterField,
  override val textRange: TextRange,
) : FieldFilter(string, field, textRange, "logcat.filter.completion.hint.key.exact") {
  override fun matches(message: LogcatMessageWrapper) = field.getValue(message) == string
}

internal data class NegatedExactStringFilter(
  val string: String,
  val field: LogcatFilterField,
  override val textRange: TextRange,
) : FieldFilter(string, field, textRange, "logcat.filter.completion.hint.key.exact.negated") {
  override fun matches(message: LogcatMessageWrapper) = field.getValue(message) != string
}

internal data class RegexFilter(
  val string: String,
  val field: LogcatFilterField,
  override val textRange: TextRange,
) : FieldFilter(string, field, textRange, "logcat.filter.completion.hint.key.regex") {
  private val regex = try {
    string.toRegex()
  }
  catch (e: PatternSyntaxException) {
    throw LogcatFilterParseException(PsiErrorElementImpl("Invalid regular expression: $string"))
  }

  override fun matches(message: LogcatMessageWrapper) = regex.containsMatchIn(field.getValue(message))
}

internal data class NegatedRegexFilter(
  val string: String,
  val field: LogcatFilterField,
  override val textRange: TextRange,
) : FieldFilter(string, field, textRange, "logcat.filter.completion.hint.key.regex.negated") {
  private val regex = try {
    string.toRegex()
  }
  catch (e: PatternSyntaxException) {
    throw LogcatFilterParseException(PsiErrorElementImpl("Invalid regular expression: $string"))
  }

  override fun matches(message: LogcatMessageWrapper) = !regex.containsMatchIn(field.getValue(message))
}

internal data class LevelFilter(
  val level: LogLevel,
  override val textRange: TextRange,
) : LogcatFilter(textRange) {
  override val displayText: String = message("logcat.filter.completion.hint.level.value", level.name)
  override fun matches(message: LogcatMessageWrapper) = message.logcatMessage.header.logLevel >= level
}

internal data class AgeFilter(
  private val text: String,
  private val clock: Clock,
  override val textRange: TextRange,
) : LogcatFilter(textRange) {
  @VisibleForTesting
  val age: Duration

  override val displayText: String

  init {
    if (!text.isValidLogAge()) {
      throw IllegalArgumentException("Invalid age: $text")
    }
    val count = try {
      text.substring(0, text.length - 1).toLong()
    }
    catch (e: NumberFormatException) {
      throw LogcatFilterParseException(PsiErrorElementImpl("Invalid duration: $text"))
    }

    fun pluralize(word: String, count: Long): String = if (count == 1L) word else Strings.pluralize(word)

    val (seconds, display) = when (text.last()) {
      's' -> Pair(count, pluralize(message("logcat.filter.completion.hint.age.second"), count))
      'm' -> Pair(TimeUnit.MINUTES.toSeconds(count), pluralize(message("logcat.filter.completion.hint.age.minute"), count))
      'h' -> Pair(TimeUnit.HOURS.toSeconds(count), pluralize(message("logcat.filter.completion.hint.age.hour"), count))
      'd' -> Pair(TimeUnit.DAYS.toSeconds(count), pluralize(message("logcat.filter.completion.hint.age.day"), count))
      else -> throw LogcatFilterParseException(PsiErrorElementImpl("Invalid duration: $text")) // should not happen
    }
    age = Duration.ofSeconds(seconds)
    displayText = message("logcat.filter.completion.hint.age.value", count, display)
  }

  override fun matches(message: LogcatMessageWrapper) =
    clock.millis() - message.logcatMessage.header.timestamp.toEpochMilli() <= age.toMillis()
}

/**
 * A special filter that matches the appName field in a [LogcatMessage] against a list of package names from the project.
 */
internal class ProjectAppFilter(
  private val packageNamesProvider: PackageNamesProvider,
  override val textRange: TextRange,
) : LogcatFilter(textRange) {
  private var packageNames: Set<String> = emptySet()
  private var packageNamesRegex: Regex? = null

  override val displayText: String
    get() = when (packageNamesProvider.getPackageNames().size) {
      0 -> message("logcat.filter.completion.hint.package.mine.empty")
      else -> message(
        "logcat.filter.completion.hint.package.mine.items",
        packageNamesProvider.getPackageNames().joinToString("<br/>&nbsp;&nbsp;"))
    }

  override fun prepare() {
    packageNames = packageNamesProvider.getPackageNames()
    packageNamesRegex = if (packageNames.isNotEmpty()) packageNames.joinToString("|") { it.replace(".", "\\.") }.toRegex() else null
  }

  override fun matches(message: LogcatMessageWrapper): Boolean {
    val header = message.logcatMessage.header
    return packageNames.contains(header.getAppName())
           || (header.logLevel >= ERROR && packageNamesRegex?.containsMatchIn(message.logcatMessage.message) == true)
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
internal data class CrashFilter(override val textRange: TextRange) : LogcatFilter(textRange) {
  override val displayText: String = message("logcat.filter.completion.hint.is.crash")

  override fun matches(message: LogcatMessageWrapper): Boolean {
    val header = message.logcatMessage.header
    val level = header.logLevel
    val tag = header.tag
    return (level == ERROR && tag == "AndroidRuntime" && message.logcatMessage.message.startsWith("FATAL EXCEPTION"))
           || (level == ASSERT && (tag == "DEBUG" || tag == "libc"))
  }
}

internal data class NameFilter(
  val name: String,
  override val textRange: TextRange,
) : LogcatFilter(textRange) {
  override val filterName: String = name

  override val displayText: String = message("logcat.filter.completion.hint.name.value", name)

  override fun matches(message: LogcatMessageWrapper): Boolean = true
}

private val exceptionLinePattern = Regex("\n\\s*at .+\\(.+\\)\n")

internal data class StackTraceFilter(override val textRange: TextRange) : LogcatFilter(textRange) {
  override val displayText: String = message("logcat.filter.completion.hint.is.stacktrace")

  override fun matches(message: LogcatMessageWrapper): Boolean = exceptionLinePattern.find(message.logcatMessage.message) != null
}

internal object EmptyFilter: LogcatFilter(EMPTY_RANGE) {
  override val displayText: String = ""

  override fun matches(message: LogcatMessageWrapper): Boolean = true
}