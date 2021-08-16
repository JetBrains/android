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
package com.android.tools.idea.logcat

import com.android.ddmlib.Log
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import com.intellij.util.containers.MultiMap
import org.junit.Test
import java.awt.Color

/**
 * Tests for [LogcatColors]
 */
class LogcatColorsTest {
  private val logcatColors = LogcatColors()

  @Test
  fun logLevelColors_areJBColor() {
    for (level in Log.LogLevel.values()) {
      assertJBColors(logcatColors.getLogLevelColor(level)!!)
    }
  }

  @Test
  fun tagColors_areJBColors() {
    for (color in logcatColors.availableTagColors) {
      assertJBColors(color)
    }
  }

  @Test
  fun tagColors_cycle() {
    val colors = MultiMap<TextAttributes, String>()
    for (i in 1..logcatColors.availableTagColors.size * 2) {
      val tag = "tag$i"
      val color = logcatColors.getTagColor(tag)
      colors.putValue(color, tag)
    }

    assertThat(colors.size()).isEqualTo(logcatColors.availableTagColors.size)
    colors.toHashMap().forEach { (_, tags) -> assertThat(tags).hasSize(2) }
  }

  @Test
  fun tagColors_retainPerTag() {
    val colors = mutableListOf<TextAttributes>()
    for (i in 0 until 10) {
      colors.add(logcatColors.getTagColor("tag$i"))
    }

    for (i in 0 until 10) {
      assertThat(colors[i]).isEqualTo(logcatColors.getTagColor("tag$i"))
    }
  }

  @Test
  fun tagColors_doNotHaveBackground() {
    val defaultTxtAttributes = TextAttributes()

    for (textAttributes in logcatColors.availableTagColors) {
      assertThat(textAttributes.backgroundColor).isEqualTo(defaultTxtAttributes.backgroundColor)
    }
  }

  @Test
  fun tagColors_haveUniqueForeground() {
    val colors = mutableSetOf<Color>()
    val darkColors = mutableSetOf<Color>()

    for (textAttributes in logcatColors.availableTagColors) {
      colors.add(textAttributes.foregroundColor)
      @Suppress("UnstableApiUsage")
      darkColors.add((textAttributes.foregroundColor as JBColor).darkVariant)
    }

    assertThat(colors).hasSize(logcatColors.availableTagColors.size)
    assertThat(darkColors).hasSize(logcatColors.availableTagColors.size)
  }
}

private fun assertJBColors(textAttributes: TextAttributes) {
  assertThat(textAttributes.foregroundColor).isInstanceOf(JBColor::class.java)
}
