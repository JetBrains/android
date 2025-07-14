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

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import junit.framework.TestCase.fail
import org.junit.Test
import java.awt.Color
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** Tests for [LogcatColors] */
class LogcatColorsTest {
  private val logcatColors = LogcatColors()

  @Test
  fun tagColors_areJBColors() {
    assertJBColors(logcatColors.getTagColor("tag"))
  }

  @Test
  fun tagColors_areDiverse() {
    val colors = mutableMapOf<TextAttributes, MutableList<Int>>()
    repeat(100) {
      colors.computeIfAbsent(logcatColors.getTagColor("tag$it")) { mutableListOf() }.add(it)
    }

    assertThat(colors.size).isAtLeast(50)
    colors.forEach { (_, tags) -> assertThat(tags.size).isAtMost(10) }
  }

  @Test
  fun tagColors_retainPerTag() {
    val colors = mutableListOf<TextAttributes>()
    repeat(10) { colors.add(logcatColors.getTagColor("tag$it")) }

    repeat(10) { assertThat(colors[it]).isSameAs(logcatColors.getTagColor("tag$it")) }
  }

  @Test
  fun tagColors_doNotHaveBackground() {
    assertThat(logcatColors.getTagColor("tag").backgroundColor)
      .isEqualTo(TextAttributes().backgroundColor)
  }

  @Test
  fun getTagColors_threadSafe() {
    val exceptions = AtomicInteger()
    val block: () -> Unit = {
      try {
        repeat(10000) { logcatColors.getTagColor("tag$it") }
      } catch (e: ConcurrentModificationException) {
        exceptions.incrementAndGet()
      }
    }
    val thread1 = thread(block = block)
    val thread2 = thread(block = block)
    thread1.join()
    thread2.join()
    if (exceptions.get() > 0) {
      fail("logcatColors.getTagColor() is not thread safe")
    }
  }
}

private fun assertJBColors(textAttributes: TextAttributes) {
  assertJBColor(textAttributes.foregroundColor)
  assertJBColor(textAttributes.backgroundColor)
}

private fun assertJBColor(color: Color?) {
  color?.run { assertThat(color).isInstanceOf(JBColor::class.java) }
}
