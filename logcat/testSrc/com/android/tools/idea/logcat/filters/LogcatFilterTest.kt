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

import com.android.tools.idea.logcat.FakePackageNamesProvider
import com.android.tools.idea.logcat.SYSTEM_HEADER
import com.android.tools.idea.logcat.filters.LogcatFilterField.APP
import com.android.tools.idea.logcat.filters.LogcatFilterField.LINE
import com.android.tools.idea.logcat.filters.LogcatFilterField.MESSAGE
import com.android.tools.idea.logcat.filters.LogcatFilterField.TAG
import com.android.tools.idea.logcat.logCatMessage
import com.android.tools.idea.logcat.message.LogLevel
import com.android.tools.idea.logcat.message.LogLevel.ASSERT
import com.android.tools.idea.logcat.message.LogLevel.ERROR
import com.android.tools.idea.logcat.message.LogLevel.INFO
import com.android.tools.idea.logcat.message.LogLevel.VERBOSE
import com.android.tools.idea.logcat.message.LogLevel.WARN
import com.android.tools.idea.logcat.message.LogcatMessage
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.UsefulTestCase.assertThrows
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

private val TIMESTAMP = Instant.ofEpochMilli(1000)
private val ZONE_ID = ZoneId.of("UTC")
private val MESSAGE1 = logCatMessage(WARN, pid = 1, tid = 1, "app1", "Tag1", TIMESTAMP, "message1")
private val MESSAGE2 = logCatMessage(WARN, pid = 2, tid = 2, "app2", "Tag2", TIMESTAMP, "message2")

/**
 * Tests for [LogcatFilter] implementations.
 */
class LogcatFilterTest {

  @Test
  fun logcatMasterFilter() {
    val filter = object : LogcatFilter {
      override fun matches(message: LogcatMessageWrapper) = message.logCatMessage == MESSAGE1
    }
    assertThat(LogcatMasterFilter(filter).filter(listOf(MESSAGE1, MESSAGE2))).containsExactly(MESSAGE1)
  }

  @Test
  fun logcatMasterFilter_systemMessages() {
    val filter = object : LogcatFilter {
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
    assertThat(StringFilter("tag1", TAG).matches(MESSAGE1)).isTrue()
    assertThat(StringFilter("tag2", TAG).matches(MESSAGE1)).isFalse()
  }

  @Test
  fun negatedStringFilter() {
    assertThat(NegatedStringFilter("tag1", TAG).matches(MESSAGE1)).isFalse()
    assertThat(NegatedStringFilter("tag2", TAG).matches(MESSAGE1)).isTrue()
  }

  @Test
  fun regexFilter() {
    assertThat(RegexFilter("Tag1.*message", LINE).matches(MESSAGE1)).isTrue()
    assertThat(RegexFilter("Tag2.*message", LINE).matches(MESSAGE1)).isFalse()
  }

  @Test
  fun regexFilter_invalidRegex() {
    assertThrows(LogcatFilterParseException::class.java) { RegexFilter("""\""", LINE) }
  }

  @Test
  fun negatedRegexFilter() {
    assertThat(NegatedRegexFilter("Tag1.*message", LINE).matches(MESSAGE1)).isFalse()
    assertThat(NegatedRegexFilter("Tag2.*message", LINE).matches(MESSAGE1)).isTrue()
  }

  @Test
  fun negatedRegexFilter_invalidRegex() {
    assertThrows(LogcatFilterParseException::class.java) { NegatedRegexFilter("""\""", LINE) }
  }

  @Test
  fun exactFilter() {
    val message = logCatMessage(tag = "MyTag1")

    assertThat(ExactStringFilter("Tag", TAG).matches(message)).isFalse()
    assertThat(ExactStringFilter("Tag1", TAG).matches(message)).isFalse()
    assertThat(ExactStringFilter("MyTag", TAG).matches(message)).isFalse()
    assertThat(ExactStringFilter("MyTag1", TAG).matches(message)).isTrue()
  }

  @Test
  fun negatedExactFilter() {
    val message = logCatMessage(tag = "MyTag1")

    assertThat(NegatedExactStringFilter("Tag", TAG).matches(message)).isTrue()
    assertThat(NegatedExactStringFilter("Tag1", TAG).matches(message)).isTrue()
    assertThat(NegatedExactStringFilter("MyTag", TAG).matches(message)).isTrue()
    assertThat(NegatedExactStringFilter("MyTag1", TAG).matches(message)).isFalse()
  }

  @Test
  fun levelFilter() {
    val levelFilter = LevelFilter(INFO)
    for (logLevel in LogLevel.values()) {
      assertThat(levelFilter.matches(logCatMessage(logLevel))).named(logLevel.name).isEqualTo(logLevel.ordinal >= INFO.ordinal)
    }
  }

  @Test
  fun ageFilter() {
    val clock = Clock.fixed(Instant.EPOCH, ZONE_ID)
    val message = logCatMessage(timestamp = clock.instant())

    assertThat(AgeFilter(Duration.ofSeconds(10), Clock.offset(clock, Duration.ofSeconds(5))).matches(message)).isTrue()
    assertThat(AgeFilter(Duration.ofSeconds(10), Clock.offset(clock, Duration.ofSeconds(15))).matches(message)).isFalse()
  }

  @Test
  fun appFilter_matches() {
    val message1 = logCatMessage(appId = "foo")
    val message2 = logCatMessage(appId = "bar")
    val message3 = logCatMessage(appId = "foobar")

    assertThat(ProjectAppFilter(FakePackageNamesProvider("foo", "bar")).filter(listOf(message1, message2, message3)))
      .containsExactly(
        message1,
        message2
      ).inOrder()
  }

  @Test
  fun appFilter_emptyMatchesNone() {
    val message1 = logCatMessage(appId = "foo")
    val message2 = logCatMessage(appId = "bar")
    val message3 = logCatMessage(appId = "foobar")

    assertThat(ProjectAppFilter(FakePackageNamesProvider()).filter(listOf(message1, message2, message3))).isEmpty()
  }

  @Test
  fun appFilter_matchedMessageText() {
    val message1 = logCatMessage(logLevel = ASSERT, message = "Assert message from com.app1")
    val message2 = logCatMessage(logLevel = ERROR, message = "Error message from com.app2")
    val message3 = logCatMessage(logLevel = WARN, message = "Warning message from com.app2")
    val message4 = logCatMessage(logLevel = ERROR, message = "Error message from com.app3")

    assertThat(ProjectAppFilter(FakePackageNamesProvider("app1", "app2")).filter(listOf(message1, message2, message3, message4)))
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
    val message1 = logCatMessage(logLevel = ERROR, message = message)
    val message2 = logCatMessage(logLevel = VERBOSE, message = message)
    val message3 = logCatMessage(logLevel = INFO, message = "Not a stacktrace")

    assertThat(StackTraceFilter.filter(listOf(message1, message2, message3)))
      .containsExactly(
        message1,
        message2,
      ).inOrder()
  }

  @Test
  fun crashFilter_jvm() {
    val message1 = logCatMessage(tag = "AndroidRuntime", logLevel = ERROR, message = "FATAL EXCEPTION")
    val message2 = logCatMessage(tag = "Foo", logLevel = ERROR, message = "FATAL EXCEPTION")
    val message3 = logCatMessage(tag = "AndroidRuntime", logLevel = ASSERT, message = "FATAL EXCEPTION")
    val message4 = logCatMessage(tag = "AndroidRuntime", logLevel = ERROR, message = "Not a FATAL EXCEPTION")

    assertThat(CrashFilter.filter(listOf(message1, message2, message3, message4))).containsExactly(message1)
  }

  @Test
  fun crashFilter_native() {
    val message1 = logCatMessage(tag = "libc", logLevel = ASSERT, message = "Native crash")
    val message2 = logCatMessage(tag = "DEBUG", logLevel = ASSERT, message = "Native crash")
    val message3 = logCatMessage(tag = "libc", logLevel = ERROR, message = "Not a native crash")
    val message4 = logCatMessage(tag = "DEBUG", logLevel = ERROR, message = "Not a native crash")

    assertThat(CrashFilter.filter(listOf(message1, message2, message3, message4)))
      .containsExactly(
        message1,
        message2,
      ).inOrder()
  }
}

private class TrueFilter : LogcatFilter {
  override fun matches(message: LogcatMessageWrapper) = true
}

private class FalseFilter : LogcatFilter {
  override fun matches(message: LogcatMessageWrapper) = false
}

private fun LogcatFilter.filter(messages: List<LogcatMessage>) = LogcatMasterFilter(this).filter(messages)

private fun LogcatFilter.matches(logCatMessage: LogcatMessage) = matches(LogcatMessageWrapper(logCatMessage))