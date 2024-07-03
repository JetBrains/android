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

import com.android.tools.idea.logcat.SYSTEM_HEADER
import com.android.tools.idea.logcat.message.LogLevel
import com.android.tools.idea.logcat.message.LogLevel.ASSERT
import com.android.tools.idea.logcat.message.LogLevel.DEBUG
import com.android.tools.idea.logcat.message.LogLevel.ERROR
import com.android.tools.idea.logcat.message.LogLevel.INFO
import com.android.tools.idea.logcat.message.LogLevel.VERBOSE
import com.android.tools.idea.logcat.message.LogLevel.WARN
import com.android.tools.idea.logcat.message.LogcatHeader
import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.logcat.messages.ProcessThreadFormat.Style.PID
import com.android.tools.idea.logcat.messages.TimestampFormat.Style.DATETIME
import com.android.tools.idea.logcat.messages.TimestampFormat.Style.TIME
import com.android.tools.idea.logcat.util.logcatMessage
import com.android.tools.idea.studiobot.StudioBot
import com.android.tools.idea.testing.ApplicationServiceRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.RuleChain
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

private val TIMESTAMP = Instant.ofEpochMilli(1000)
private val ZONE_ID = ZoneId.of("Asia/Yerevan")

/** Tests for [MessageFormatter] */
class MessageFormatterTest {
  @get:Rule
  val rule =
    RuleChain(ApplicationRule(), ApplicationServiceRule(StudioBot::class.java, TestStudioBot))

  private val logcatColors = LogcatColors()
  private val formattingOptions = FormattingOptions()

  private val messageFormatter = MessageFormatter(logcatColors, ZONE_ID)

  @Test
  fun formatMessages_defaultFormat() {
    val textAccumulator = TextAccumulator()

    messageFormatter.formatMessages(
      formattingOptions,
      textAccumulator,
      listOf(
        LogcatMessage(
          LogcatHeader(WARN, 1, 2, "com.example.app1", "", "Tag1", TIMESTAMP),
          "message",
        ),
        LogcatMessage(
          LogcatHeader(WARN, 1, 20001, "com.example.app1", "", "Tag1", TIMESTAMP),
          "message",
        ),
        LogcatMessage(
          LogcatHeader(WARN, 1, 20001, "com.example.app1", "", "Tag2", TIMESTAMP),
          "message",
        ),
        LogcatMessage(
          LogcatHeader(
            WARN,
            10001,
            2,
            "com.long.company.name.app2",
            "",
            "LongCompanyNameTag1",
            TIMESTAMP,
          ),
          "message",
        ),
        LogcatMessage(
          LogcatHeader(
            WARN,
            10001,
            2,
            "com.long.company.name.app2",
            "",
            "LongCompanyNameTag1",
            TIMESTAMP,
          ),
          "message",
        ),
        LogcatMessage(
          LogcatHeader(
            WARN,
            10001,
            2,
            "com.long.company.name.app2",
            "",
            "LongCompanyNameTag2",
            TIMESTAMP,
          ),
          "line1\nline2",
        ),
      ),
    )

    assertThat(textAccumulator.text)
      .isEqualTo(
        """
      1970-01-01 04:00:01.000     1-2     Tag1                    com.example.app1                     W  message
      1970-01-01 04:00:01.000     1-20001 Tag1                    com.example.app1                     W  message
      1970-01-01 04:00:01.000     1-20001 Tag2                    com.example.app1                     W  message
      1970-01-01 04:00:01.000 10001-2     LongCompanyNameTag1     com.long.company.name.app2           W  message
      1970-01-01 04:00:01.000 10001-2     LongCompanyNameTag1     com.long.company.name.app2           W  message
      1970-01-01 04:00:01.000 10001-2     LongCompanyNameTag2     com.long.company.name.app2           W  line1
                                                                                                          line2

    """
          .trimIndent()
      )
  }

  @Test
  fun formatMessages_timeOnly() {
    val textAccumulator = TextAccumulator()
    formattingOptions.timestampFormat = TimestampFormat(TIME, enabled = true)

    messageFormatter.formatMessages(
      formattingOptions,
      textAccumulator,
      listOf(
        LogcatMessage(
          LogcatHeader(WARN, 1, 2, "com.example.app1", "", "Tag1", TIMESTAMP),
          "message",
        ),
        LogcatMessage(
          LogcatHeader(WARN, 1, 2, "com.example.app1", "", "Tag1", TIMESTAMP),
          "multiline\nmessage",
        ),
      ),
    )

    assertThat(textAccumulator.text)
      .isEqualTo(
        """
      04:00:01.000     1-2     Tag1                    com.example.app1                     W  message
      04:00:01.000     1-2     Tag1                    com.example.app1                     W  multiline
                                                                                               message

    """
          .trimIndent()
      )
  }

  @Test
  fun formatMessages_noTimestamp() {
    val textAccumulator = TextAccumulator()
    formattingOptions.timestampFormat = TimestampFormat(DATETIME, enabled = false)

    messageFormatter.formatMessages(
      formattingOptions,
      textAccumulator,
      listOf(
        LogcatMessage(
          LogcatHeader(WARN, 1, 2, "com.example.app1", "", "Tag1", TIMESTAMP),
          "message",
        ),
        LogcatMessage(
          LogcatHeader(WARN, 1, 2, "com.example.app1", "", "Tag1", TIMESTAMP),
          "multiline\nmessage",
        ),
      ),
    )

    assertThat(textAccumulator.text.trimEnd())
      .isEqualTo(
        """
      1-2     Tag1                    com.example.app1                     W  message
      1-2     Tag1                    com.example.app1                     W  multiline
                                                                              message

    """
          .replaceIndent("    ")
          .trimEnd()
      )
  }

  @Test
  fun formatMessages_pidOnly() {
    val textAccumulator = TextAccumulator()
    formattingOptions.processThreadFormat = ProcessThreadFormat(PID, enabled = true)

    messageFormatter.formatMessages(
      formattingOptions,
      textAccumulator,
      listOf(
        LogcatMessage(
          LogcatHeader(WARN, 1, 2, "com.example.app1", "", "Tag1", TIMESTAMP),
          "message",
        ),
        LogcatMessage(
          LogcatHeader(WARN, 10001, 20001, "com.example.app2", "", "Tag1", TIMESTAMP),
          "multiline\nmessage",
        ),
      ),
    )

    assertThat(textAccumulator.text)
      .isEqualTo(
        """
      1970-01-01 04:00:01.000 1     Tag1                    com.example.app1                     W  message
      1970-01-01 04:00:01.000 10001 Tag1                    com.example.app2                     W  multiline
                                                                                                    message

    """
          .trimIndent()
      )
  }

  @Test
  fun formatMessages_noIds() {
    val textAccumulator = TextAccumulator()
    formattingOptions.processThreadFormat = ProcessThreadFormat(enabled = false)

    messageFormatter.formatMessages(
      formattingOptions,
      textAccumulator,
      listOf(
        LogcatMessage(
          LogcatHeader(WARN, 1, 2, "com.example.app1", "", "Tag1", TIMESTAMP),
          "message",
        ),
        LogcatMessage(
          LogcatHeader(WARN, 10001, 20001, "com.example.app2", "", "Tag1", TIMESTAMP),
          "multiline\nmessage",
        ),
      ),
    )

    assertThat(textAccumulator.text)
      .isEqualTo(
        """
      1970-01-01 04:00:01.000 Tag1                    com.example.app1                     W  message
      1970-01-01 04:00:01.000 Tag1                    com.example.app2                     W  multiline
                                                                                              message

    """
          .trimIndent()
      )
  }

  @Test
  fun formatMessages_longTags() {
    val textAccumulator = TextAccumulator()
    formattingOptions.tagFormat = TagFormat(maxLength = 15)

    messageFormatter.formatMessages(
      formattingOptions,
      textAccumulator,
      listOf(
        LogcatMessage(
          LogcatHeader(WARN, 1, 2, "com.example.app1", "", "Tag1", TIMESTAMP),
          "message",
        ),
        LogcatMessage(
          LogcatHeader(WARN, 2, 2, "com.example.app1", "", "LongCompanyNameTag1", TIMESTAMP),
          "multiline\nmessage",
        ),
      ),
    )

    assertThat(textAccumulator.text)
      .isEqualTo(
        """
      1970-01-01 04:00:01.000     1-2     Tag1            com.example.app1                     W  message
      1970-01-01 04:00:01.000     2-2     LongCo...meTag1 com.example.app1                     W  multiline
                                                                                                  message

    """
          .trimIndent()
      )
  }

  @Test
  fun formatMessages_missingTag() {
    val textAccumulator = TextAccumulator()

    messageFormatter.formatMessages(
      formattingOptions,
      textAccumulator,
      listOf(
        LogcatMessage(LogcatHeader(WARN, 1, 2, "com.example.app1", "", "", TIMESTAMP), "message"),
        LogcatMessage(
          LogcatHeader(WARN, 1, 2, "com.example.app1", "", "", TIMESTAMP),
          "multiline\nmessage",
        ),
      ),
    )

    assertThat(textAccumulator.text)
      .isEqualTo(
        """
      1970-01-01 04:00:01.000     1-2     <no-tag>                com.example.app1                     W  message
      1970-01-01 04:00:01.000     1-2     <no-tag>                com.example.app1                     W  multiline
                                                                                                          message

    """
          .trimIndent()
      )
  }

  @Test
  fun formatMessages_hideDuplicateTags() {
    val textAccumulator = TextAccumulator()
    formattingOptions.tagFormat = TagFormat(hideDuplicates = true)

    messageFormatter.formatMessages(
      formattingOptions,
      textAccumulator,
      listOf(
        LogcatMessage(
          LogcatHeader(WARN, 1, 2, "com.example.app1", "", "Tag1", TIMESTAMP),
          "message",
        ),
        LogcatMessage(
          LogcatHeader(WARN, 1, 2, "com.example.app1", "", "Tag1", TIMESTAMP),
          "message",
        ),
        LogcatMessage(
          LogcatHeader(WARN, 1, 2, "com.example.app1", "", "Tag2", TIMESTAMP),
          "multiline\nmessage",
        ),
      ),
    )

    assertThat(textAccumulator.text)
      .isEqualTo(
        """
      1970-01-01 04:00:01.000     1-2     Tag1                    com.example.app1                     W  message
      1970-01-01 04:00:01.000     1-2                             com.example.app1                     W  message
      1970-01-01 04:00:01.000     1-2     Tag2                    com.example.app1                     W  multiline
                                                                                                          message

    """
          .trimIndent()
      )
  }

  @Test
  fun formatMessages_noTags() {
    val textAccumulator = TextAccumulator()
    formattingOptions.tagFormat = TagFormat(enabled = false)

    messageFormatter.formatMessages(
      formattingOptions,
      textAccumulator,
      listOf(
        LogcatMessage(
          LogcatHeader(WARN, 1, 2, "com.example.app1", "", "Tag1", TIMESTAMP),
          "message",
        ),
        LogcatMessage(
          LogcatHeader(WARN, 1, 2, "com.example.app1", "", "Tag1", TIMESTAMP),
          "multiline\nmessage",
        ),
      ),
    )

    assertThat(textAccumulator.text)
      .isEqualTo(
        """
      1970-01-01 04:00:01.000     1-2     com.example.app1                     W  message
      1970-01-01 04:00:01.000     1-2     com.example.app1                     W  multiline
                                                                                  message

    """
          .trimIndent()
      )
  }

  @Test
  fun formatMessages_longAppName() {
    val textAccumulator = TextAccumulator()
    formattingOptions.appNameFormat = AppNameFormat(maxLength = 20)

    messageFormatter.formatMessages(
      formattingOptions,
      textAccumulator,
      listOf(
        LogcatMessage(
          LogcatHeader(WARN, 1, 2, "com.example.app1", "", "Tag1", TIMESTAMP),
          "message",
        ),
        LogcatMessage(
          LogcatHeader(WARN, 2, 2, "com.long.company.name.app2", "", "Tag1", TIMESTAMP),
          "multiline\nmessage",
        ),
      ),
    )

    assertThat(textAccumulator.text)
      .isEqualTo(
        """
      1970-01-01 04:00:01.000     1-2     Tag1                    com.example.app1      W  message
      1970-01-01 04:00:01.000     2-2     Tag1                    com...pany.name.app2  W  multiline
                                                                                           message

    """
          .trimIndent()
      )
  }

  @Test
  fun formatMessages_hideDuplicateAppNames() {
    val textAccumulator = TextAccumulator()
    formattingOptions.appNameFormat = AppNameFormat(hideDuplicates = true)

    messageFormatter.formatMessages(
      formattingOptions,
      textAccumulator,
      listOf(
        LogcatMessage(
          LogcatHeader(WARN, 1, 2, "com.example.app1", "", "Tag1", TIMESTAMP),
          "message",
        ),
        LogcatMessage(
          LogcatHeader(WARN, 1, 2, "com.example.app1", "", "Tag1", TIMESTAMP),
          "message",
        ),
        LogcatMessage(
          LogcatHeader(WARN, 2, 2, "com.example.app2", "", "Tag2", TIMESTAMP),
          "multiline\nmessage",
        ),
      ),
    )

    assertThat(textAccumulator.text)
      .isEqualTo(
        """
      1970-01-01 04:00:01.000     1-2     Tag1                    com.example.app1                     W  message
      1970-01-01 04:00:01.000     1-2     Tag1                                                         W  message
      1970-01-01 04:00:01.000     2-2     Tag2                    com.example.app2                     W  multiline
                                                                                                          message

    """
          .trimIndent()
      )
  }

  @Test
  fun formatMessages_noAppNames() {
    val textAccumulator = TextAccumulator()
    formattingOptions.appNameFormat = AppNameFormat(enabled = false)

    messageFormatter.formatMessages(
      formattingOptions,
      textAccumulator,
      listOf(
        LogcatMessage(
          LogcatHeader(WARN, 1, 2, "com.example.app1", "", "Tag1", TIMESTAMP),
          "message",
        ),
        LogcatMessage(
          LogcatHeader(WARN, 1, 2, "com.example.app1", "", "Tag1", TIMESTAMP),
          "multiline\nmessage",
        ),
      ),
    )

    assertThat(textAccumulator.text)
      .isEqualTo(
        """
      1970-01-01 04:00:01.000     1-2     Tag1                     W  message
      1970-01-01 04:00:01.000     1-2     Tag1                     W  multiline
                                                                      message

    """
          .trimIndent()
      )
  }

  @Test
  fun formatMessages_withProcessName() {
    val textAccumulator = TextAccumulator()
    formattingOptions.processNameFormat = ProcessNameFormat(enabled = true)

    messageFormatter.formatMessages(
      formattingOptions,
      textAccumulator,
      listOf(
        LogcatMessage(
          LogcatHeader(WARN, 1, 2, "com.example.app1", "process1", "Tag1", TIMESTAMP),
          "message",
        ),
        LogcatMessage(
          LogcatHeader(WARN, 1, 2, "com.example.app1", "process1", "Tag1", TIMESTAMP),
          "multiline\nmessage",
        ),
      ),
    )

    assertThat(textAccumulator.text)
      .isEqualTo(
        """
      1970-01-01 04:00:01.000     1-2     Tag1                    com.example.app1                    process1                             W  message
      1970-01-01 04:00:01.000     1-2     Tag1                    com.example.app1                    process1                             W  multiline
                                                                                                                                              message

    """
          .trimIndent()
      )
  }

  @Test
  fun formatMessages_levelColors() {
    val messages = mutableListOf<LogcatMessage>()
    for (level in LogLevel.entries) {
      messages.add(LogcatMessage(LogcatHeader(level, 1, 2, "app", "", "tag", TIMESTAMP), "message"))
    }
    val textAccumulator = TextAccumulator()

    messageFormatter.formatMessages(formattingOptions, textAccumulator, messages)

    // Filter the ranges corresponding to a LogLevel and build a map level -> color.
    val textAttributes =
      textAccumulator.textAttributesKeyRanges
        .filter { it.getText(textAccumulator.text).matches(" [VDIWEA] ".toRegex()) }
        .associate { it.getText(textAccumulator.text).trim() to it.data }

    assertThat(textAttributes)
      .containsExactly(
        "V",
        logcatColors.getLogLevelKey(VERBOSE),
        "D",
        logcatColors.getLogLevelKey(DEBUG),
        "I",
        logcatColors.getLogLevelKey(INFO),
        "W",
        logcatColors.getLogLevelKey(WARN),
        "E",
        logcatColors.getLogLevelKey(ERROR),
        "A",
        logcatColors.getLogLevelKey(ASSERT),
      )
  }

  @Test
  fun formatMessages_messageColors() {
    val messages = mutableListOf<LogcatMessage>()
    for (level in LogLevel.entries) {
      messages.add(
        LogcatMessage(
          LogcatHeader(level, 1, 2, "app", "", "tag", TIMESTAMP),
          "message-${level.name}",
        )
      )
    }
    val textAccumulator = TextAccumulator()

    messageFormatter.formatMessages(formattingOptions, textAccumulator, messages)

    // Filter the ranges corresponding to a LogLevel and build a map level -> color.
    val textAttributes =
      textAccumulator.textAttributesKeyRanges
        .filter { it.getText(textAccumulator.text).matches("message-.*".toRegex()) }
        .associate { it.getText(textAccumulator.text) to it.data }

    assertThat(textAttributes)
      .containsExactly(
        "message-VERBOSE",
        logcatColors.getMessageKey(VERBOSE),
        "message-DEBUG",
        logcatColors.getMessageKey(DEBUG),
        "message-INFO",
        logcatColors.getMessageKey(INFO),
        "message-WARN",
        logcatColors.getMessageKey(WARN),
        "message-ERROR",
        logcatColors.getMessageKey(ERROR),
        "message-ASSERT",
        logcatColors.getMessageKey(ASSERT),
      )
  }

  @Test
  fun formatMessages_tagColors() {
    // Print with 10 different tags and then assert that there are 10 highlight ranges corresponding
    // to the tags with the proper color.
    val messages = mutableListOf<LogcatMessage>()
    val numTags = 10
    for (t in 1..numTags) {
      messages.add(
        LogcatMessage(LogcatHeader(INFO, 1, 2, "app", "", "tag$t", TIMESTAMP), "message")
      )
    }
    val textAccumulator = TextAccumulator()

    messageFormatter.formatMessages(formattingOptions, textAccumulator, messages)

    // Filter the ranges corresponding to a tag and build a map tag -> color.
    val tagColors =
      textAccumulator.textAttributesRanges
        .filter { it.getText(textAccumulator.text).matches("tag\\d+ *".toRegex()) }
        .associate { it.getText(textAccumulator.text).trim() to it.data }
    assertThat(tagColors).hasSize(numTags)
    tagColors.forEach { (tag, color) -> assertThat(color).isEqualTo(logcatColors.getTagColor(tag)) }
  }

  @Test
  fun formatMessages_systemMessages() {
    val textAccumulator = TextAccumulator()

    messageFormatter.formatMessages(
      formattingOptions,
      textAccumulator,
      listOf(LogcatMessage(SYSTEM_HEADER, "message")),
    )

    assertThat(textAccumulator.text).isEqualTo("message\n")
  }

  @Test
  fun formatMessages_exception_studioBotEnabled() {
    TestStudioBot.available = true
    val textAccumulator = TextAccumulator()

    messageFormatter.formatMessages(
      formattingOptions,
      textAccumulator,
      listOf(logcatMessage(message = "" + "Exception\n" + "\tat com.example(File.kt:1)\n")),
    )

    assertThat(textAccumulator.text.trim())
      .isEqualTo(
        "" +
          "1970-01-01 04:00:10.000     1-2     ExampleTag              com.example.app                      I  Exception (Ask Gemini)\n" +
          "                                                                                                    \tat com.example(File.kt:1)"
      )
  }

  @Test
  fun formatMessages_exception_studioBotDisabled() {
    TestStudioBot.available = false
    val textAccumulator = TextAccumulator()

    messageFormatter.formatMessages(
      formattingOptions,
      textAccumulator,
      listOf(logcatMessage(message = "" + "Exception\n" + "\tat com.example(File.kt:1)")),
    )

    assertThat(textAccumulator.text.trim())
      .isEqualTo(
        "" +
          "1970-01-01 04:00:10.000     1-2     ExampleTag              com.example.app                      I  Exception\n" +
          "                                                                                                    \tat com.example(File.kt:1)"
      )
  }

  private object TestStudioBot : StudioBot.StubStudioBot() {
    var available = true

    override fun isAvailable(): Boolean = available

    override fun isContextAllowed(project: Project): Boolean = true
  }
}

private fun <T> TextAccumulator.Range<T>.getText(text: String) = text.substring(start, end)
