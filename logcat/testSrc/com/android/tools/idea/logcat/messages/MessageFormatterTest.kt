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

import com.android.ddmlib.Log
import com.android.ddmlib.logcat.LogCatHeader
import com.android.ddmlib.logcat.LogCatMessage
import com.google.common.truth.Truth
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

private val timestamp = Instant.ofEpochMilli(1000)

/**
 * Tests for [MessageFormatter]
 */
class MessageFormatterTest {
  private val logcatColors = LogcatColors()
  private val messageFormatter = MessageFormatter(logcatColors, ZoneId.of("Asia/Yerevan"))

  @Test
  fun formatMessages_alignment() {
    val textAccumulator = TextAccumulator()

    messageFormatter.formatMessages(
      textAccumulator,
      listOf(
        LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 1, 2, "app", "tag", timestamp), "message"),
        LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 12345, 12345, "app", "tag", timestamp), "message"),
        LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 12345, 12345, "long app", "tag", timestamp), "message"),
        LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 12345, 12345, "app", "long tag", timestamp), "message"),
        LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 12345, 12345, "long app", "long tag", timestamp), "message"),
        LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 1, 2, "app", "tag", timestamp), "message"),
      ))

    Truth.assertThat(textAccumulator.text).isEqualTo("""
      1970-01-01 04:00:01.000      1-2      tag app W message
      1970-01-01 04:00:01.000  12345-12345      app W message
      1970-01-01 04:00:01.000  12345-12345      long app W message
      1970-01-01 04:00:01.000  12345-12345  long tag app      W message
      1970-01-01 04:00:01.000  12345-12345           long app W message
      1970-01-01 04:00:01.000      1-2      tag      app      W message

    """.trimIndent())
  }

  @Test
  fun formatMessages_omitSameTag() {
    val textAccumulator = TextAccumulator()

    messageFormatter.formatMessages(
      textAccumulator,
      listOf(
        LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 1, 2, "app", "tag1", timestamp), "message1"),
        LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 1, 2, "app", "tag1", timestamp), "message2"),
        LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 1, 2, "app", "tag1", timestamp), "message3"),
        LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 1, 2, "app", "tag2", timestamp), "message4"),
      ))

    Truth.assertThat(textAccumulator.text).isEqualTo("""
      1970-01-01 04:00:01.000      1-2      tag1 app W message1
      1970-01-01 04:00:01.000      1-2           app W message2
      1970-01-01 04:00:01.000      1-2           app W message3
      1970-01-01 04:00:01.000      1-2      tag2 app W message4

    """.trimIndent())
  }

  @Test
  fun formatMessages_levelColors() {
    val messages = mutableListOf<LogCatMessage>()
    for (level in Log.LogLevel.values()) {
      messages.add(LogCatMessage(LogCatHeader(level, 1, 2, "app", "tag", timestamp), "message"))
    }
    val textAccumulator = TextAccumulator()

    messageFormatter.formatMessages(textAccumulator, messages)

    // Filter the ranges corresponding to a LogLevel and build a map level -> color.
    val textAttributes = textAccumulator.ranges.filter { getRangeText(textAccumulator.text, it).matches(" [VDIWEA] ".toRegex()) }
      .associate { getRangeText(textAccumulator.text, it).trim() to it.textAttributes }

    Truth.assertThat(textAttributes).containsExactly(
      "V", logcatColors.getLogLevelColor(Log.LogLevel.VERBOSE),
      "D", logcatColors.getLogLevelColor(Log.LogLevel.DEBUG),
      "I", logcatColors.getLogLevelColor(Log.LogLevel.INFO),
      "W", logcatColors.getLogLevelColor(Log.LogLevel.WARN),
      "E", logcatColors.getLogLevelColor(Log.LogLevel.ERROR),
      "A", logcatColors.getLogLevelColor(Log.LogLevel.ASSERT),
    )
  }

  @Test
  fun formatMessages_tagColors() {
    // Print with 10 different tags and then assert that there are 10 highlight ranges corresponding to the tags with the proper color.
    val messages = mutableListOf<LogCatMessage>()
    val numTags = 10
    for (t in 1..numTags) {
      messages.add(LogCatMessage(LogCatHeader(Log.LogLevel.INFO, 1, 2, "app", "tag$t", timestamp), "message"))
    }
    val textAccumulator = TextAccumulator()

    messageFormatter.formatMessages(textAccumulator, messages)

    // Filter the ranges corresponding to a tag and build a map tag -> color.
    val tagColors = textAccumulator.ranges.filter { getRangeText(textAccumulator.text, it).matches(" tag\\d+ *".toRegex()) }
      .associate { getRangeText(textAccumulator.text, it).trim() to it.textAttributes }
    Truth.assertThat(tagColors).hasSize(numTags)
    tagColors.forEach { (tag, color) ->
      Truth.assertThat(color).isEqualTo(logcatColors.getTagColor(tag))
    }
  }

  @Test
  fun formatMessages_emptyTag() {
    val textAccumulator = TextAccumulator()

    messageFormatter.formatMessages(
      textAccumulator, listOf(LogCatMessage(LogCatHeader(Log.LogLevel.INFO, 1, 2, "app", "", timestamp), "message")))

    Truth.assertThat(textAccumulator.text).isEqualTo("""
        1970-01-01 04:00:01.000      1-2        app I message

      """.trimIndent())
  }

  @Test
  fun formatMessages_emptyApp() = runBlocking {
    val textAccumulator = TextAccumulator()

    messageFormatter.formatMessages(
      textAccumulator, listOf(LogCatMessage(LogCatHeader(Log.LogLevel.INFO, 1, 2, "", "tag", timestamp), "message")))

    Truth.assertThat(textAccumulator.text).isEqualTo("""
        1970-01-01 04:00:01.000      1-2      tag   I message

      """.trimIndent())
  }
}

private fun getRangeText(text: String, range: HighlighterRange) = text.substring(range.start, range.end)
