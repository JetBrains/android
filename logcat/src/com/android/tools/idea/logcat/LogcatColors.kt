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

import com.android.annotations.concurrency.UiThread
import com.android.ddmlib.Log
import com.android.ddmlib.Log.LogLevel
import com.google.common.collect.Iterables
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import org.jetbrains.annotations.VisibleForTesting

/**
 * Manages the various colors used in a log view.
 *
 * Colors are stored as [TextAttributes] which can be assigned to [com.intellij.openapi.editor.Document]
 * [com.intellij.openapi.editor.markup.MarkupModel] ranges.
 *
 * [Log.LogLevel] colors are provided with an extension method.
 *
 * Tag colors colors are assigned dynamically from a small pool and stored in a map.
 *
 * TODO(aalbert): Get proper colors from UX.
 */
internal class LogcatColors {
  private val tagColors = mutableMapOf<String, TextAttributes>()
  private val nextTagColor = Iterables.cycle(AVAILABLE_TAG_COLORS).iterator()

  /**
   * Map a [Log.LogLevel] to a [TextAttributes] object.
   */
  internal fun getLogLevelColor(level: LogLevel) = LEVEL_COLORS[level]

  /**
   * Map a Logcat tag to a [TextAttributes] object.
   *
   * Colors are assigned dynamically from a cyclic pool of available colors. Some common tags are seeded into the initial store.
   *
   * TODO(aalbert): Maybe persist the color table in a global store so it persists between sessions?
   */
  @UiThread
  internal fun getTagColor(tag: String): TextAttributes = tagColors.computeIfAbsent(tag) { nextTagColor.next() }

  @VisibleForTesting
  internal val availableTagColors = AVAILABLE_TAG_COLORS
}

private val LEVEL_VERBOSE = TextAttributes().apply { foregroundColor = JBColor.WHITE; backgroundColor = JBColor.BLACK }
private val LEVEL_DEBUG = TextAttributes().apply { foregroundColor = JBColor.WHITE; backgroundColor = JBColor.BLUE }
private val LEVEL_INFO = TextAttributes().apply { foregroundColor = JBColor.BLACK; backgroundColor = JBColor.GREEN }
private val LEVEL_WARNING = TextAttributes().apply { foregroundColor = JBColor.BLACK; backgroundColor = JBColor.YELLOW }
private val LEVEL_ERROR = TextAttributes().apply { foregroundColor = JBColor.WHITE; backgroundColor = JBColor.RED }
private val LEVEL_ASSERT = LEVEL_ERROR

private val LEVEL_COLORS = mapOf(
  LogLevel.VERBOSE to LEVEL_VERBOSE,
  LogLevel.DEBUG to LEVEL_DEBUG,
  LogLevel.INFO to LEVEL_INFO,
  LogLevel.WARN to LEVEL_WARNING,
  LogLevel.ERROR to LEVEL_ERROR,
  LogLevel.ASSERT to LEVEL_ASSERT,
)

private val TAG_RED = TextAttributes().apply { foregroundColor = JBColor.RED }
private val TAG_GREEN = TextAttributes().apply { foregroundColor = JBColor.GREEN }
private val TAG_YELLOW = TextAttributes().apply { foregroundColor = JBColor.YELLOW }
private val TAG_BLUE = TextAttributes().apply { foregroundColor = JBColor.BLUE }
private val TAG_MAGENTA = TextAttributes().apply { foregroundColor = JBColor.MAGENTA }
private val TAG_CYAN = TextAttributes().apply { foregroundColor = JBColor.CYAN }

private val AVAILABLE_TAG_COLORS = listOf(TAG_RED, TAG_GREEN, TAG_YELLOW, TAG_BLUE, TAG_MAGENTA, TAG_CYAN)
