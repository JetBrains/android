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
package com.android.tools.idea.logcat.messages

import com.android.tools.idea.logcat.LogcatBundle
import com.android.tools.idea.logcat.message.LogLevel
import com.android.utils.usLocaleCapitalize
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

/**
 * Tests for [LogcatColorSettingsPage]
 */
class LogcatColorSettingsPageTest {
  private val colors = LogcatColors()
  private val logcatColorSettingsPage = LogcatColorSettingsPage()

  @Test
  fun attributeDescriptors() {
    val descriptors = logcatColorSettingsPage.attributeDescriptors.associate { it.displayName to it.key }

    assertThat(descriptors.size).isEqualTo(LogLevel.values().size * 2)
    for (level in LogLevel.values()) {
      val name = level.name.lowercase(Locale.getDefault()).usLocaleCapitalize()
      assertThat(descriptors).containsEntry(LogcatBundle.message("logcat.color.page.indicator", name), colors.getLogLevelKey(level))
      assertThat(descriptors).containsEntry(LogcatBundle.message("logcat.color.page.message", name), colors.getMessageKey(level))
    }
  }

  @Test
  fun additionalHighlightingTagToDescriptorMap() {
    val map = logcatColorSettingsPage.additionalHighlightingTagToDescriptorMap

    assertThat(map.size).isEqualTo(LogLevel.values().size * 2)
    for (level in LogLevel.values()) {
      assertThat(map).containsEntry(level.toIndicatorTag(), colors.getLogLevelKey(level))
      assertThat(map).containsEntry(level.toMessageTag(), colors.getMessageKey(level))
    }
  }

  @Test
  fun demoText() {
    // Extract only lines containing a sample log entry and trim out everything before the first color tag (first '<' char)
    val lines = logcatColorSettingsPage.demoText.lines().filter { it.contains("Sample") }.map { it.substring(it.indexOf("<")) }

    assertThat(lines.size).isEqualTo(LogLevel.values().size)
    for (level in LogLevel.values()) {
      val indicatorTag = level.toIndicatorTag()
      val messageTag = level.toMessageTag()
      assertThat(lines)
        .contains("<$indicatorTag> ${level.priorityLetter} </$indicatorTag> <$messageTag>Sample ${level.stringValue} text</$messageTag>")
    }
  }
}

private fun LogLevel.toIndicatorTag() = "${priorityLetter.toLowerCase()}"
private fun LogLevel.toMessageTag() = "${priorityLetter.toLowerCase()}m"