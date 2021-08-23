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
import com.android.tools.adtui.common.ColorPaletteManager
import com.google.gson.Gson
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import java.io.InputStreamReader

/**
 * The logcat-tags-palette.json file is
 * [auto generated](https://source.cloud.google.com/google.com:adux-source/studio-palettes/+/master:client/app/index.js;l=176)
 * and should be updated when the design team creates new colors.
 */
private const val PALETTE_JSON_FILENAME = "/palette/logcat-tags-palette.json"

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
 * TODO(b/197762564): Get proper colors from UX
 */
internal class LogcatColors {

  private val colorPaletteManager by lazy {
    javaClass.getResourceAsStream(PALETTE_JSON_FILENAME)?.let {
      ColorPaletteManager(Gson().fromJson(InputStreamReader(it), Array<ColorPaletteManager.ColorPalette>::class.java))
    } ?: throw IllegalStateException("Resource not found")

  }
  private val tagColors = mutableMapOf<Int, TextAttributes>()

  /**
   * Map a [Log.LogLevel] to a [TextAttributes] object.
   */
  internal fun getLogLevelColor(level: LogLevel) = LEVEL_COLORS[level]

  /**
   * Map a Logcat tag to a [TextAttributes] object.
   *
   * Leverage [ColorPaletteManager] for color selection but since we use [TextAttributes], we maintain our own cache.
   *
   */
  @UiThread
  internal fun getTagColor(tag: String): TextAttributes {
    val index = tag.hashCode()
    return tagColors.computeIfAbsent(index) {
      // We only use the foreground colors. Background colors from the palette are ignored
      TextAttributes().apply { foregroundColor = colorPaletteManager.getForegroundColor(index) }
    }
  }
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
