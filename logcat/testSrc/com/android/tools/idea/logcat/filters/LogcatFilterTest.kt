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

import com.android.flags.junit.FlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.logcat.FakePackageNamesProvider
import com.android.tools.idea.logcat.SYSTEM_HEADER
import com.android.tools.idea.logcat.filters.LogcatFilterField.APP
import com.android.tools.idea.logcat.filters.LogcatFilterField.IMPLICIT_LINE
import com.android.tools.idea.logcat.filters.LogcatFilterField.LINE
import com.android.tools.idea.logcat.filters.LogcatFilterField.MESSAGE
import com.android.tools.idea.logcat.filters.LogcatFilterField.PROCESS
import com.android.tools.idea.logcat.filters.LogcatFilterField.TAG
import com.android.tools.idea.logcat.message.LogLevel
import com.android.tools.idea.logcat.message.LogLevel.ASSERT
import com.android.tools.idea.logcat.message.LogLevel.DEBUG
import com.android.tools.idea.logcat.message.LogLevel.ERROR
import com.android.tools.idea.logcat.message.LogLevel.INFO
import com.android.tools.idea.logcat.message.LogLevel.VERBOSE
import com.android.tools.idea.logcat.message.LogLevel.WARN
import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.logcat.settings.AndroidLogcatSettings
import com.android.tools.idea.logcat.util.logcatMessage
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.TextRange.EMPTY_RANGE
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.UsefulTestCase.assertThrows
import com.intellij.testFramework.replaceService
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

private val TIMESTAMP = Instant.ofEpochMilli(1000)
private val ZONE_ID = ZoneId.of("UTC")
private val MESSAGE1 = logcatMessage(WARN, pid = 1, tid = 1, "app1", "Tag1", TIMESTAMP, "message1")
private val MESSAGE2 = logcatMessage(WARN, pid = 2, tid = 2, "app2", "Tag2", TIMESTAMP, "message2")

/**
 * Tests for [LogcatFilter] implementations.
 */
class LogcatFilterTest {
  private val disposableRule = DisposableRule()

  @get:Rule
  val rule = RuleChain(ApplicationRule(), disposableRule, FlagRule(StudioFlags.LOGCAT_IGNORE_STUDIO_SPAM_TAGS))

  private val logcatSettings = AndroidLogcatSettings()

  @Before
  fun setUp() {
    ApplicationManager.getApplication().replaceService(AndroidLogcatSettings::class.java, logcatSettings, disposableRule.disposable)
  }

  @Test
  fun logcatMasterFilter() {
    val filter = object : LogcatFilter(EMPTY_RANGE) {
      override val displayText: String = ""

      override fun matches(message: LogcatMessageWrapper) = message.logcatMessage == MESSAGE1
    }
    assertThat(LogcatMasterFilter(filter).filter(listOf(MESSAGE1, MESSAGE2))).containsExactly(MESSAGE1)
  }

  @Test
  fun logcatMasterFilter_systemMessages() {
    val filter = object : LogcatFilter(EMPTY_RANGE) {
      override val displayText: String = ""
      override fun matches(message: LogcatMessageWrapper) = false
    }
    val systemMessage = LogcatMessage(SYSTEM_HEADER, "message")

    assertThat(LogcatMasterFilter(filter).filter(listOf(systemMessage))).containsExactly(systemMessage)
  }

  @Test
  fun logcatMasterFilter_nullFilter() {
    val messages = listOf(MESSAGE1, MESSAGE2)

    assertThat(LogcatMasterFilter(null).filter(messages)).isEqualTo(messages)
  }

  @Test
  fun logcatMasterFilter_ignoreTags() {
    val messages = listOf(MESSAGE1, MESSAGE2)
    logcatSettings.ignoredTags = setOf(MESSAGE1.header.tag)
    val filter = object : LogcatFilter(EMPTY_RANGE) {
      override val displayText: String = ""

      override fun matches(message: LogcatMessageWrapper) = true
    }

    assertThat(LogcatMasterFilter(filter).filter(messages)).isEqualTo(listOf(MESSAGE2))
  }

  @Test
  fun logcatMasterFilter_studioSpam() {
    StudioFlags.LOGCAT_IGNORE_STUDIO_SPAM_TAGS.override(false)
    val spam = logcatMessage(tag = "studio.ignore")
    val messages = listOf(MESSAGE1, spam)
    val filter = object : LogcatFilter(EMPTY_RANGE) {
      override val displayText: String = ""

      override fun matches(message: LogcatMessageWrapper) = true
    }

    assertThat(LogcatMasterFilter(filter).filter(messages)).isEqualTo(listOf(MESSAGE1, spam))
  }

  @Test
  fun logcatMasterFilter_studioSpam_withoutFlag() {
    val messages = listOf(MESSAGE1, logcatMessage(tag = "studio.ignore"))
    val filter = object : LogcatFilter(EMPTY_RANGE) {
      override val displayText: String = ""

      override fun matches(message: LogcatMessageWrapper) = true
    }

    assertThat(LogcatMasterFilter(filter).filter(messages)).isEqualTo(listOf(MESSAGE1))
  }

  @Test
  fun logcatMasterFilter_ignoreTags_nullFilter() {
    val messages = listOf(MESSAGE1, MESSAGE2)
    logcatSettings.ignoredTags = setOf(MESSAGE1.header.tag)

    assertThat(LogcatMasterFilter(null).filter(messages)).isEqualTo(listOf(MESSAGE2))
  }

  @Test
  fun andLogcatFilter_allTrue() {
    assertThat(
      AndLogcatFilter(
        TrueFilter(),
        TrueFilter(),
        TrueFilter(),
        TrueFilter(),
        TrueFilter(),
      ).matches(MESSAGE1)).isTrue()
  }

  @Test
  fun andLogcatFilter_oneFalse() {
    assertThat(
      AndLogcatFilter(
        TrueFilter(),
        TrueFilter(),
        FalseFilter(),
        TrueFilter(),
        TrueFilter(),
      ).matches(MESSAGE1)).isFalse()
  }

  @Test
  fun orLogcatFilter_allFalse() {
    assertThat(
      OrLogcatFilter(
        FalseFilter(),
        FalseFilter(),
        FalseFilter(),
        FalseFilter(),
        FalseFilter(),
      ).matches(MESSAGE1)).isFalse()
  }

  @Test
  fun orLogcatFilter_oneTrue() {
    assertThat(
      OrLogcatFilter(
        FalseFilter(),
        FalseFilter(),
        TrueFilter(),
        FalseFilter(),
        FalseFilter(),
      ).matches(MESSAGE1)).isTrue()
  }

  @Test
  fun logcatFilterField() {
    assertThat(TAG.getValue(LogcatMessageWrapper(MESSAGE1))).isEqualTo("Tag1")
    assertThat(APP.getValue(LogcatMessageWrapper(MESSAGE1))).isEqualTo("app1")
    assertThat(MESSAGE.getValue(LogcatMessageWrapper(MESSAGE1))).isEqualTo("message1")
    assertThat(LINE.getValue(LogcatMessageWrapper(MESSAGE1, ZONE_ID)))
      .isEqualTo("1970-01-01 00:00:01.000 1-1 Tag1 app1 W: message1")
  }

  @Test
  fun stringFilter() {
    assertThat(StringFilter("tag1", TAG, EMPTY_RANGE).matches(MESSAGE1)).isTrue()
    assertThat(StringFilter("tag2", TAG, EMPTY_RANGE).matches(MESSAGE1)).isFalse()
  }

  @Test
  fun negatedStringFilter() {
    assertThat(NegatedStringFilter("tag1", TAG, EMPTY_RANGE).matches(MESSAGE1)).isFalse()
    assertThat(NegatedStringFilter("tag2", TAG, EMPTY_RANGE).matches(MESSAGE1)).isTrue()
  }

  @Test
  fun regexFilter() {
    assertThat(RegexFilter("Tag1.*message", LINE, EMPTY_RANGE).matches(MESSAGE1)).isTrue()
    assertThat(RegexFilter("Tag2.*message", LINE, EMPTY_RANGE).matches(MESSAGE1)).isFalse()
  }

  @Test
  fun regexFilter_invalidRegex() {
    assertThrows(LogcatFilterParseException::class.java) { RegexFilter("""\""", LINE, EMPTY_RANGE) }
  }

  @Test
  fun negatedRegexFilter() {
    assertThat(NegatedRegexFilter("Tag1.*message", LINE, EMPTY_RANGE).matches(MESSAGE1)).isFalse()
    assertThat(NegatedRegexFilter("Tag2.*message", LINE, EMPTY_RANGE).matches(MESSAGE1)).isTrue()
  }

  @Test
  fun negatedRegexFilter_invalidRegex() {
    assertThrows(LogcatFilterParseException::class.java) { NegatedRegexFilter("""\""", LINE, EMPTY_RANGE) }
  }

  @Test
  fun exactFilter() {
    val message = logcatMessage(tag = "MyTag1")

    assertThat(ExactStringFilter("Tag", TAG, EMPTY_RANGE).matches(message)).isFalse()
    assertThat(ExactStringFilter("Tag1", TAG, EMPTY_RANGE).matches(message)).isFalse()
    assertThat(ExactStringFilter("MyTag", TAG, EMPTY_RANGE).matches(message)).isFalse()
    assertThat(ExactStringFilter("MyTag1", TAG, EMPTY_RANGE).matches(message)).isTrue()
  }

  @Test
  fun negatedExactFilter() {
    val message = logcatMessage(tag = "MyTag1")

    assertThat(NegatedExactStringFilter("Tag", TAG, EMPTY_RANGE).matches(message)).isTrue()
    assertThat(NegatedExactStringFilter("Tag1", TAG, EMPTY_RANGE).matches(message)).isTrue()
    assertThat(NegatedExactStringFilter("MyTag", TAG, EMPTY_RANGE).matches(message)).isTrue()
    assertThat(NegatedExactStringFilter("MyTag1", TAG, EMPTY_RANGE).matches(message)).isFalse()
  }

  @Test
  fun levelFilter() {
    val levelFilter = LevelFilter(INFO, EMPTY_RANGE)
    for (logLevel in LogLevel.values()) {
      assertThat(levelFilter.matches(logcatMessage(logLevel))).named(logLevel.name).isEqualTo(logLevel.ordinal >= INFO.ordinal)
    }
  }

  @Test
  fun ageFilter_parsing() {
    val clock = Clock.fixed(Instant.EPOCH, ZONE_ID)
    assertThat(AgeFilter("10s", clock, EMPTY_RANGE).age).isEqualTo(Duration.ofSeconds(10))
    assertThat(AgeFilter("5m", clock, EMPTY_RANGE).age).isEqualTo(Duration.ofMinutes(5))
    assertThat(AgeFilter("3h", clock, EMPTY_RANGE).age).isEqualTo(Duration.ofHours(3))
    assertThat(AgeFilter("1d", clock, EMPTY_RANGE).age).isEqualTo(Duration.ofDays(1))
  }

  @Test
  fun ageFilter_matches() {
    val clock = Clock.fixed(Instant.EPOCH, ZONE_ID)
    val message = logcatMessage(timestamp = clock.instant())

    assertThat(AgeFilter("10s", Clock.offset(clock, Duration.ofSeconds(5)), EMPTY_RANGE).matches(message)).isTrue()
    assertThat(AgeFilter("10s", Clock.offset(clock, Duration.ofSeconds(15)), EMPTY_RANGE).matches(message)).isFalse()
  }

  @Test
  fun appFilter_matches() {
    val message1 = logcatMessage(appId = "foo")
    val message2 = logcatMessage(appId = "bar")
    val message3 = logcatMessage(appId = "foobar")

    assertThat(ProjectAppFilter(FakePackageNamesProvider("foo", "bar"), EMPTY_RANGE).filter(listOf(message1, message2, message3)))
      .containsExactly(
        message1,
        message2
      ).inOrder()
  }

  @Test
  fun appFilter_emptyMatchesNone() {
    val message1 = logcatMessage(appId = "foo")
    val message2 = logcatMessage(appId = "bar")
    val message3 = logcatMessage(appId = "error", logLevel = ERROR)

    assertThat(ProjectAppFilter(FakePackageNamesProvider(), EMPTY_RANGE).filter(listOf(message1, message2, message3))).isEmpty()
  }

  @Test
  fun appFilter_matchedMessageText() {
    val message1 = logcatMessage(logLevel = ASSERT, message = "Assert message from com.app1")
    val message2 = logcatMessage(logLevel = ERROR, message = "Error message from com.app2")
    val message3 = logcatMessage(logLevel = WARN, message = "Warning message from com.app2")
    val message4 = logcatMessage(logLevel = ERROR, message = "Error message from com.app3")

    assertThat(
      ProjectAppFilter(FakePackageNamesProvider("app1", "app2"), EMPTY_RANGE).filter(listOf(message1, message2, message3, message4)))
      .containsExactly(
        message1,
        message2,
      ).inOrder()
  }

  @Test
  fun stackTraceFilter() {
    val message = """
      Failed metering RPC
        io.grpc.StatusRuntimeException: UNAVAILABLE
          at io.grpc.stub.ClientCalls.toStatusRuntimeException(ClientCalls.java:262)
          at io.grpc.stub.ClientCalls.getUnchecked(ClientCalls.java:243)
    """.trimIndent()
    val message1 = logcatMessage(logLevel = ERROR, message = message)
    val message2 = logcatMessage(logLevel = VERBOSE, message = message)
    val message3 = logcatMessage(logLevel = INFO, message = "Not a stacktrace")

    assertThat(StackTraceFilter(EMPTY_RANGE).filter(listOf(message1, message2, message3)))
      .containsExactly(
        message1,
        message2,
      ).inOrder()
  }

  @Test
  fun crashFilter_jvm() {
    val message1 = logcatMessage(tag = "AndroidRuntime", logLevel = ERROR, message = "FATAL EXCEPTION")
    val message2 = logcatMessage(tag = "Foo", logLevel = ERROR, message = "FATAL EXCEPTION")
    val message3 = logcatMessage(tag = "AndroidRuntime", logLevel = ASSERT, message = "FATAL EXCEPTION")
    val message4 = logcatMessage(tag = "AndroidRuntime", logLevel = ERROR, message = "Not a FATAL EXCEPTION")

    assertThat(CrashFilter(EMPTY_RANGE).filter(listOf(message1, message2, message3, message4))).containsExactly(message1)
  }

  @Test
  fun crashFilter_native() {
    val message1 = logcatMessage(tag = "libc", logLevel = ASSERT, message = "Native crash")
    val message2 = logcatMessage(tag = "DEBUG", logLevel = ASSERT, message = "Native crash")
    val message3 = logcatMessage(tag = "libc", logLevel = ERROR, message = "Not a native crash")
    val message4 = logcatMessage(tag = "DEBUG", logLevel = ERROR, message = "Not a native crash")

    assertThat(CrashFilter(EMPTY_RANGE).filter(listOf(message1, message2, message3, message4)))
      .containsExactly(
        message1,
        message2,
      ).inOrder()
  }

  @Test
  fun nameFilter_matches() {
    assertThat(NameFilter("name", EMPTY_RANGE).matches(logcatMessage(message = "whatever"))).isTrue()
  }

  @Test
  fun getFilterName_nameFilter() {
    assertThat(NameFilter("name", EMPTY_RANGE).filterName).isEqualTo("name")
  }

  @Test
  fun getFilterName_simpleFilters() {
    assertThat(StringFilter("string", TAG, EMPTY_RANGE).filterName).isNull()
    assertThat(NegatedStringFilter("string", TAG, EMPTY_RANGE).filterName).isNull()
    assertThat(ExactStringFilter("string", TAG, EMPTY_RANGE).filterName).isNull()
    assertThat(NegatedExactStringFilter("string", TAG, EMPTY_RANGE).filterName).isNull()
    assertThat(RegexFilter("string", TAG, EMPTY_RANGE).filterName).isNull()
    assertThat(NegatedRegexFilter("string", TAG, EMPTY_RANGE).filterName).isNull()
    assertThat(LevelFilter(INFO, EMPTY_RANGE).filterName).isNull()
    assertThat(AgeFilter("60s", Clock.systemDefaultZone(), EMPTY_RANGE).filterName).isNull()
    assertThat(CrashFilter(EMPTY_RANGE).filterName).isNull()
    assertThat(StackTraceFilter(EMPTY_RANGE).filterName).isNull()
  }

  @Test
  fun getFilterName_compoundFilter() {
    assertThat(AndLogcatFilter(StringFilter("string", TAG, EMPTY_RANGE), LevelFilter(INFO, EMPTY_RANGE)).filterName).isNull()
    assertThat(OrLogcatFilter(StringFilter("string", TAG, EMPTY_RANGE), LevelFilter(INFO, EMPTY_RANGE)).filterName).isNull()
    assertThat(AndLogcatFilter(
      NameFilter("name1", EMPTY_RANGE),
      StringFilter("string", TAG, EMPTY_RANGE),
      LevelFilter(INFO, EMPTY_RANGE),
      NameFilter("name2", EMPTY_RANGE),
    ).filterName).isEqualTo("name2")
    assertThat(OrLogcatFilter(
      NameFilter("name1", EMPTY_RANGE),
      StringFilter("string", TAG, EMPTY_RANGE),
      LevelFilter(INFO, EMPTY_RANGE),
      NameFilter("name2", EMPTY_RANGE),
    ).filterName).isEqualTo("name2")
  }

  @Test
  fun displayText_stringFilter() {
    assertThat(StringFilter("foo", APP, EMPTY_RANGE).displayText).isEqualTo("Package name contains 'foo'")
    assertThat(StringFilter("foo", IMPLICIT_LINE, EMPTY_RANGE).displayText).isEqualTo("Log line contains 'foo'")
    assertThat(StringFilter("foo", LINE, EMPTY_RANGE).displayText).isEqualTo("Log line contains 'foo'")
    assertThat(StringFilter("foo", MESSAGE, EMPTY_RANGE).displayText).isEqualTo("Log message contains 'foo'")
    assertThat(StringFilter("foo", PROCESS, EMPTY_RANGE).displayText).isEqualTo("Process name contains 'foo'")
    assertThat(StringFilter("foo", TAG, EMPTY_RANGE).displayText).isEqualTo("Log tag contains 'foo'")
  }

  @Test
  fun displayText_negatedStringFilter() {
    assertThat(NegatedStringFilter("foo", APP, EMPTY_RANGE).displayText).isEqualTo("Package name does not contain 'foo'")
    assertThat(NegatedStringFilter("foo", IMPLICIT_LINE, EMPTY_RANGE).displayText).isEqualTo("Log line does not contain 'foo'")
    assertThat(NegatedStringFilter("foo", LINE, EMPTY_RANGE).displayText).isEqualTo("Log line does not contain 'foo'")
    assertThat(NegatedStringFilter("foo", MESSAGE, EMPTY_RANGE).displayText).isEqualTo("Log message does not contain 'foo'")
    assertThat(NegatedStringFilter("foo", PROCESS, EMPTY_RANGE).displayText).isEqualTo("Process name does not contain 'foo'")
    assertThat(NegatedStringFilter("foo", TAG, EMPTY_RANGE).displayText).isEqualTo("Log tag does not contain 'foo'")
  }

  @Test
  fun exactStringFilter() {
    assertThat(ExactStringFilter("foo", APP, EMPTY_RANGE).displayText).isEqualTo("Package name is exactly 'foo'")
    assertThat(ExactStringFilter("foo", IMPLICIT_LINE, EMPTY_RANGE).displayText).isEqualTo("Log line is exactly 'foo'")
    assertThat(ExactStringFilter("foo", LINE, EMPTY_RANGE).displayText).isEqualTo("Log line is exactly 'foo'")
    assertThat(ExactStringFilter("foo", MESSAGE, EMPTY_RANGE).displayText).isEqualTo("Log message is exactly 'foo'")
    assertThat(ExactStringFilter("foo", PROCESS, EMPTY_RANGE).displayText).isEqualTo("Process name is exactly 'foo'")
    assertThat(ExactStringFilter("foo", TAG, EMPTY_RANGE).displayText).isEqualTo("Log tag is exactly 'foo'")
  }

  @Test
  fun displayText_negatedExactStringFilter() {
    assertThat(NegatedExactStringFilter("foo", APP, EMPTY_RANGE).displayText).isEqualTo("Package name is not exactly 'foo'")
    assertThat(NegatedExactStringFilter("foo", IMPLICIT_LINE, EMPTY_RANGE).displayText).isEqualTo("Log line is not exactly 'foo'")
    assertThat(NegatedExactStringFilter("foo", LINE, EMPTY_RANGE).displayText).isEqualTo("Log line is not exactly 'foo'")
    assertThat(NegatedExactStringFilter("foo", MESSAGE, EMPTY_RANGE).displayText).isEqualTo("Log message is not exactly 'foo'")
    assertThat(NegatedExactStringFilter("foo", PROCESS, EMPTY_RANGE).displayText).isEqualTo("Process name is not exactly 'foo'")
    assertThat(NegatedExactStringFilter("foo", TAG, EMPTY_RANGE).displayText).isEqualTo("Log tag is not exactly 'foo'")
  }

  @Test
  fun displayText_regexFilter() {
    assertThat(RegexFilter("foo", APP, EMPTY_RANGE).displayText).isEqualTo("Package name matches 'foo'")
    assertThat(RegexFilter("foo", IMPLICIT_LINE, EMPTY_RANGE).displayText).isEqualTo("Log line matches 'foo'")
    assertThat(RegexFilter("foo", LINE, EMPTY_RANGE).displayText).isEqualTo("Log line matches 'foo'")
    assertThat(RegexFilter("foo", MESSAGE, EMPTY_RANGE).displayText).isEqualTo("Log message matches 'foo'")
    assertThat(RegexFilter("foo", PROCESS, EMPTY_RANGE).displayText).isEqualTo("Process name matches 'foo'")
    assertThat(RegexFilter("foo", TAG, EMPTY_RANGE).displayText).isEqualTo("Log tag matches 'foo'")
  }

  @Test
  fun displayText_negatedRegexFilter() {
    assertThat(NegatedRegexFilter("foo", APP, EMPTY_RANGE).displayText).isEqualTo("Package name does not match 'foo'")
    assertThat(NegatedRegexFilter("foo", IMPLICIT_LINE, EMPTY_RANGE).displayText).isEqualTo("Log line does not match 'foo'")
    assertThat(NegatedRegexFilter("foo", LINE, EMPTY_RANGE).displayText).isEqualTo("Log line does not match 'foo'")
    assertThat(NegatedRegexFilter("foo", MESSAGE, EMPTY_RANGE).displayText).isEqualTo("Log message does not match 'foo'")
    assertThat(NegatedRegexFilter("foo", PROCESS, EMPTY_RANGE).displayText).isEqualTo("Process name does not match 'foo'")
    assertThat(NegatedRegexFilter("foo", TAG, EMPTY_RANGE).displayText).isEqualTo("Log tag does not match 'foo'")
  }

  @Test
  fun displayText_levelFilter() {
    assertThat(LevelFilter(VERBOSE, EMPTY_RANGE).displayText).isEqualTo("Filter by VERBOSE or higher")
    assertThat(LevelFilter(DEBUG, EMPTY_RANGE).displayText).isEqualTo("Filter by DEBUG or higher")
    assertThat(LevelFilter(INFO, EMPTY_RANGE).displayText).isEqualTo("Filter by INFO or higher")
    assertThat(LevelFilter(WARN, EMPTY_RANGE).displayText).isEqualTo("Filter by WARN or higher")
    assertThat(LevelFilter(ERROR, EMPTY_RANGE).displayText).isEqualTo("Filter by ERROR or higher")
    assertThat(LevelFilter(ASSERT, EMPTY_RANGE).displayText).isEqualTo("Filter by ASSERT or higher")
  }

  @Test
  fun displayText_ageFilter() {
    assertThat(AgeFilter("1s", Clock.systemDefaultZone(), EMPTY_RANGE).displayText).isEqualTo("Filter logs from past 1 second")
    assertThat(AgeFilter("5s", Clock.systemDefaultZone(), EMPTY_RANGE).displayText).isEqualTo("Filter logs from past 5 seconds")
    assertThat(AgeFilter("1m", Clock.systemDefaultZone(), EMPTY_RANGE).displayText).isEqualTo("Filter logs from past 1 minute")
    assertThat(AgeFilter("5m", Clock.systemDefaultZone(), EMPTY_RANGE).displayText).isEqualTo("Filter logs from past 5 minutes")
    assertThat(AgeFilter("1h", Clock.systemDefaultZone(), EMPTY_RANGE).displayText).isEqualTo("Filter logs from past 1 hour")
    assertThat(AgeFilter("5h", Clock.systemDefaultZone(), EMPTY_RANGE).displayText).isEqualTo("Filter logs from past 5 hours")
    assertThat(AgeFilter("1d", Clock.systemDefaultZone(), EMPTY_RANGE).displayText).isEqualTo("Filter logs from past 1 day")
    assertThat(AgeFilter("5d", Clock.systemDefaultZone(), EMPTY_RANGE).displayText).isEqualTo("Filter logs from past 5 days")
  }

  @Test
  fun displayText_projectAppFilter() {
    val packageNamesProvider = FakePackageNamesProvider()
    val projectAppFilter = ProjectAppFilter(packageNamesProvider, EMPTY_RANGE)
    assertThat(projectAppFilter.displayText)
      .isEqualTo("No project ids detected. Is the project synced?")

    packageNamesProvider.getPackageNames().add("app1")
    packageNamesProvider.getPackageNames().add("app2")
    assertThat(projectAppFilter.displayText).isEqualTo(
      "<html>Filter logs from current project id(s):<br/>&nbsp;&nbsp;app1<br/>&nbsp;&nbsp;app2<html>")
  }

  @Test
  fun displayText_crashFilter() {
    assertThat(CrashFilter(EMPTY_RANGE).displayText).isEqualTo("Filter crashes")
  }

  @Test
  fun displayText_stackTraceFilter() {
    assertThat(StackTraceFilter(EMPTY_RANGE).displayText).isEqualTo("Filter stack traces")
  }

  @Test
  fun displayText_nameFilter() {
    assertThat(NameFilter("name", EMPTY_RANGE).displayText).isEqualTo("This filter's name is 'name'")
  }
}

private class TrueFilter : LogcatFilter(EMPTY_RANGE) {
  override val displayText: String = ""
  override fun matches(message: LogcatMessageWrapper) = true
}

private class FalseFilter : LogcatFilter(EMPTY_RANGE) {
  override val displayText: String = ""
  override fun matches(message: LogcatMessageWrapper) = false
}

private fun LogcatFilter.filter(messages: List<LogcatMessage>) = LogcatMasterFilter(this).filter(messages)

private fun LogcatFilter.matches(logcatMessage: LogcatMessage) = matches(LogcatMessageWrapper(logcatMessage))