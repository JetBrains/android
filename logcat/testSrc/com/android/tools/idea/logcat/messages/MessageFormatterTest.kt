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
import com.android.tools.idea.logcat.messages.ProcessThreadFormat.NO_IDS
import com.android.tools.idea.logcat.messages.ProcessThreadFormat.PID
import com.android.tools.idea.logcat.messages.TimestampFormat.NO_TIMESTAMP
import com.android.tools.idea.logcat.messages.TimestampFormat.TIME
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

private val timestamp = Instant.ofEpochMilli(1000)

/**
 * Tests for [MessageFormatter]
 */
class MessageFormatterTest {
  private val logcatColors = LogcatColors()
  private val formattingOptions = FormattingOptions()
  private val messageFormatter = MessageFormatter(formattingOptions, logcatColors, ZoneId.of("Asia/Yerevan"))

  @Test
  fun formatMessages_defaultFormat() {
    val textAccumulator = TextAccumulator()

    messageFormatter.formatMessages(
      textAccumulator,
      listOf(
        LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 1, 2, "com.example.app1", "Tag1", timestamp), "message"),
        LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 1, 20001, "com.example.app1", "Tag1", timestamp), "message"),
        LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 1, 20001, "com.example.app1", "Tag2", timestamp), "message"),
        LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 10001, 2, "com.long.company.name.app2", "LongCompanyNameTag1", timestamp), "message"),
        LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 10001, 2, "com.long.company.name.app2", "LongCompanyNameTag1", timestamp), "message"),
        LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 10001, 2, "com.long.company.name.app2", "LongCompanyNameTag2", timestamp), "message"),
      ))

    assertThat(textAccumulator.text).isEqualTo("""
      1970-01-01 04:00:01.000     1-2     Tag1                    com.example.app1                     W  message
      1970-01-01 04:00:01.000     1-20001 Tag1                    com.example.app1                     W  message
      1970-01-01 04:00:01.000     1-20001 Tag2                    com.example.app1                     W  message
      1970-01-01 04:00:01.000 10001-2     LongCompanyNameTag1     com.long.company.name.app2           W  message
      1970-01-01 04:00:01.000 10001-2     LongCompanyNameTag1     com.long.company.name.app2           W  message
      1970-01-01 04:00:01.000 10001-2     LongCompanyNameTag2     com.long.company.name.app2           W  message

    """.trimIndent())
  }

  @Test
  fun formatMessages_timeOnly() {
    val textAccumulator = TextAccumulator()
    formattingOptions.timestampFormat = TIME

    messageFormatter.formatMessages(
      textAccumulator,
      listOf(LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 1, 2, "com.example.app1", "Tag1", timestamp), "message")))

    assertThat(textAccumulator.text).isEqualTo("""
      04:00:01.000     1-2     Tag1                    com.example.app1                     W  message

    """.trimIndent())
  }

  @Test
  fun formatMessages_noTimestamp() {
    val textAccumulator = TextAccumulator()
    formattingOptions.timestampFormat = NO_TIMESTAMP

    messageFormatter.formatMessages(
      textAccumulator,
      listOf(LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 1, 2, "com.example.app1", "Tag1", timestamp), "message")))

    assertThat(textAccumulator.text).isEqualTo("    1-2     Tag1                    com.example.app1                     W  message\n")
  }

  @Test
  fun formatMessages_pidOnly() {
    val textAccumulator = TextAccumulator()
    formattingOptions.processThreadFormat = PID

    messageFormatter.formatMessages(
      textAccumulator,
      listOf(
        LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 1, 2, "com.example.app1", "Tag1", timestamp), "message"),
        LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 10001, 20001, "com.example.app2", "Tag1", timestamp), "message"),
      ))

    assertThat(textAccumulator.text).isEqualTo("""
      1970-01-01 04:00:01.000 1     Tag1                    com.example.app1                     W  message
      1970-01-01 04:00:01.000 10001 Tag1                    com.example.app2                     W  message

    """.trimIndent())
  }

  @Test
  fun formatMessages_noIds() {
    val textAccumulator = TextAccumulator()
    formattingOptions.processThreadFormat = NO_IDS

    messageFormatter.formatMessages(
      textAccumulator,
      listOf(
        LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 1, 2, "com.example.app1", "Tag1", timestamp), "message"),
        LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 10001, 20001, "com.example.app2", "Tag1", timestamp), "message"),
      ))

    assertThat(textAccumulator.text).isEqualTo("""
      1970-01-01 04:00:01.000 Tag1                    com.example.app1                     W  message
      1970-01-01 04:00:01.000 Tag1                    com.example.app2                     W  message

    """.trimIndent())
  }

  @Test
  fun formatMessages_longTags() {
    val textAccumulator = TextAccumulator()
    formattingOptions.tagFormat = TagFormat(maxLength = 15)

    messageFormatter.formatMessages(
      textAccumulator,
      listOf(
        LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 1, 2, "com.example.app1", "Tag1", timestamp), "message"),
        LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 2, 2, "com.example.app1", "LongCompanyNameTag1", timestamp), "message"),
      ))

    assertThat(textAccumulator.text).isEqualTo("""
      1970-01-01 04:00:01.000     1-2     Tag1            com.example.app1                     W  message
      1970-01-01 04:00:01.000     2-2     Lon...yNameTag1 com.example.app1                     W  message

    """.trimIndent())
  }

  @Test
  fun formatMessages_missingTag() {
    val textAccumulator = TextAccumulator()

    messageFormatter.formatMessages(
      textAccumulator,
      listOf(
        LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 1, 2, "com.example.app1", "", timestamp), "message"),
      ))

    assertThat(textAccumulator.text).isEqualTo("""
      1970-01-01 04:00:01.000     1-2     <no-tag>                com.example.app1                     W  message

    """.trimIndent())
  }

  @Test
  fun formatMessages_hideDuplicateTags() {
    val textAccumulator = TextAccumulator()
    formattingOptions.tagFormat = TagFormat(hideDuplicates = true)

    messageFormatter.formatMessages(
      textAccumulator,
      listOf(
        LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 1, 2, "com.example.app1", "Tag1", timestamp), "message"),
        LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 1, 2, "com.example.app1", "Tag1", timestamp), "message"),
        LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 1, 2, "com.example.app1", "Tag2", timestamp), "message"),
      ))

    assertThat(textAccumulator.text).isEqualTo("""
      1970-01-01 04:00:01.000     1-2     Tag1                    com.example.app1                     W  message
      1970-01-01 04:00:01.000     1-2                             com.example.app1                     W  message
      1970-01-01 04:00:01.000     1-2     Tag2                    com.example.app1                     W  message

    """.trimIndent())
  }

  @Test
  fun formatMessages_noTags() {
    val textAccumulator = TextAccumulator()
    formattingOptions.tagFormat = TagFormat(enabled = false)

    messageFormatter.formatMessages(
      textAccumulator,
      listOf(
        LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 1, 2, "com.example.app1", "Tag1", timestamp), "message"),
      ))

    assertThat(textAccumulator.text).isEqualTo("""
      1970-01-01 04:00:01.000     1-2     com.example.app1                     W  message

    """.trimIndent())
  }

  @Test
  fun formatMessages_longAppName() {
    val textAccumulator = TextAccumulator()
    formattingOptions.appNameFormat = AppNameFormat(maxLength = 20)

    messageFormatter.formatMessages(
      textAccumulator,
      listOf(
        LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 1, 2, "com.example.app1", "Tag1", timestamp), "message"),
        LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 2, 2, "com.long.company.name.app2", "Tag1", timestamp), "message"),
      ))

    assertThat(textAccumulator.text).isEqualTo("""
      1970-01-01 04:00:01.000     1-2     Tag1                    com.example.app1      W  message
      1970-01-01 04:00:01.000     2-2     Tag1                    com...pany.name.app2  W  message

    """.trimIndent())
  }

  @Test
  fun formatMessages_missingAppName() {
    val textAccumulator = TextAccumulator()

    messageFormatter.formatMessages(
      textAccumulator,
      listOf(
        LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 1, 2, "?", "Tag", timestamp), "message"),
        LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 1, 2, "", "Tag", timestamp), "message"),
      ))

    assertThat(textAccumulator.text).isEqualTo("""
      1970-01-01 04:00:01.000     1-2     Tag                     pid-1                                W  message
      1970-01-01 04:00:01.000     1-2     Tag                     pid-1                                W  message

    """.trimIndent())
  }

  @Test
  fun formatMessages_hideDuplicateAppNames() {
    val textAccumulator = TextAccumulator()
    formattingOptions.appNameFormat = AppNameFormat(hideDuplicates = true)

    messageFormatter.formatMessages(
      textAccumulator,
      listOf(
        LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 1, 2, "com.example.app1", "Tag1", timestamp), "message"),
        LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 1, 2, "com.example.app1", "Tag1", timestamp), "message"),
        LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 2, 2, "com.example.app2", "Tag2", timestamp), "message"),
      ))

    assertThat(textAccumulator.text).isEqualTo("""
      1970-01-01 04:00:01.000     1-2     Tag1                    com.example.app1                     W  message
      1970-01-01 04:00:01.000     1-2     Tag1                                                         W  message
      1970-01-01 04:00:01.000     2-2     Tag2                    com.example.app2                     W  message

    """.trimIndent())
  }

  @Test
  fun formatMessages_noAppNames() {
    val textAccumulator = TextAccumulator()
    formattingOptions.appNameFormat = AppNameFormat(enabled = false)

    messageFormatter.formatMessages(
      textAccumulator,
      listOf(
        LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 1, 2, "com.example.app1", "Tag1", timestamp), "message"),
      ))

    assertThat(textAccumulator.text).isEqualTo("""
      1970-01-01 04:00:01.000     1-2     Tag1                     W  message

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
    val textAttributes = textAccumulator.highlightRanges.filter { it.getText(textAccumulator.text).matches(" [VDIWEA] ".toRegex()) }
      .associate { it.getText(textAccumulator.text).trim() to it.data }

    assertThat(textAttributes).containsExactly(
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
    val tagColors = textAccumulator.highlightRanges.filter { it.getText(textAccumulator.text).matches("tag\\d+ *".toRegex()) }
      .associate { it.getText(textAccumulator.text).trim() to it.data }
    assertThat(tagColors).hasSize(numTags)
    tagColors.forEach { (tag, color) ->
      assertThat(color).isEqualTo(logcatColors.getTagColor(tag))
    }
  }

  @Test
  fun formatMessages_hints() {
    val textAccumulator = TextAccumulator()

    messageFormatter.formatMessages(
      textAccumulator,
      listOf(
        LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 1, 2, "app1", "tag1", timestamp), "message1"),
        LogCatMessage(LogCatHeader(Log.LogLevel.WARN, 1, 2, "app2", "tag2", timestamp), "message2"),
      ))

    textAccumulator.hintRanges.forEach {
      assertThat(it.getText(textAccumulator.text).trim()).isEqualTo(it.data)
    }
  }
}

private fun <T> TextAccumulator.Range<T>.getText(text: String) = text.substring(start, end)
