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

import com.android.ddmlib.Log.LogLevel
import com.android.ddmlib.Log.LogLevel.INFO
import com.android.ddmlib.Log.LogLevel.WARN
import com.android.ddmlib.logcat.LogCatMessage
import com.android.tools.idea.logcat.filters.LogcatFilterField.APP
import com.android.tools.idea.logcat.filters.LogcatFilterField.LINE
import com.android.tools.idea.logcat.filters.LogcatFilterField.MESSAGE
import com.android.tools.idea.logcat.filters.LogcatFilterField.TAG
import com.android.tools.idea.logcat.logCatMessage
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
private val MESSAGES = listOf(MESSAGE1, MESSAGE2)

/**
 * Tests for [LogcatFilter] implementations.
 */
class LogcatFilterTest {

  @Test
  fun logcatFilter_matches() {
    val filter = object : LogcatFilter {
      override fun matches(message: LogcatMessageWrapper) = message.logCatMessage == MESSAGE1
    }
    assertThat(filter.filter(MESSAGES)).containsExactly(MESSAGE1)
  }

  @Test
  fun emptyFilter() {
    assertThat(EmptyFilter().filter(MESSAGES)).isSameAs(MESSAGES)
  }

  @Test
  fun emptyFilter_matches_notSupported() {
    assertThrows(UnsupportedOperationException::class.java) { EmptyFilter().matches(MESSAGE1) }
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
    assertThat(RegexFilter("tag1.*message", LINE).matches(MESSAGE1)).isTrue()
    assertThat(RegexFilter("tag2.*message", LINE).matches(MESSAGE1)).isFalse()
  }

  @Test
  fun negatedRegexFilter() {
    assertThat(NegatedRegexFilter("tag1.*message", LINE).matches(MESSAGE1)).isFalse()
    assertThat(NegatedRegexFilter("tag2.*message", LINE).matches(MESSAGE1)).isTrue()
  }

  @Test
  fun levelFilter() {
    val levelFilter = LevelFilter(INFO)
    for (logLevel in LogLevel.values()) {
      assertThat(levelFilter.matches(logCatMessage(logLevel))).named(logLevel.name).isEqualTo(logLevel == INFO)
    }
  }

  @Test
  fun fromLevelFilter() {
    val levelFilter = FromLevelFilter(INFO)
    for (logLevel in LogLevel.values()) {
      assertThat(levelFilter.matches(logCatMessage(logLevel))).named(logLevel.name).isEqualTo(logLevel.ordinal >= INFO.ordinal)
    }
  }

  @Test
  fun toLevelFilter() {
    val levelFilter = ToLevelFilter(INFO)
    for (logLevel in LogLevel.values()) {
      assertThat(levelFilter.matches(logCatMessage(logLevel))).named(logLevel.name).isEqualTo(logLevel.ordinal <= INFO.ordinal)
    }
  }

  @Test
  fun ageFilter() {
    val clock = Clock.fixed(Instant.EPOCH, ZONE_ID)
    val message = logCatMessage(timestamp = clock.instant())

    assertThat(AgeFilter(Duration.ofSeconds(10), Clock.offset(clock, Duration.ofSeconds(5))).matches(message)).isTrue()
    assertThat(AgeFilter(Duration.ofSeconds(10), Clock.offset(clock, Duration.ofSeconds(15))).matches(message)).isFalse()
  }
}

private class TrueFilter : LogcatFilter {
  override fun matches(message: LogcatMessageWrapper) = true
}

private class FalseFilter : LogcatFilter {
  override fun matches(message: LogcatMessageWrapper) = false
}

private fun LogcatFilter.matches(logCatMessage: LogCatMessage) = matches(LogcatMessageWrapper(logCatMessage))