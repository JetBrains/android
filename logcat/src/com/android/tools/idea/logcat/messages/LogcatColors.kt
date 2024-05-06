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

import com.android.tools.adtui.common.ColorPaletteManager
import com.android.tools.idea.logcat.message.LogLevel
import com.google.gson.Gson
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.TextAttributes
import java.util.concurrent.ConcurrentHashMap

/**
 * The logcat-tags-palette.json file is
 * [auto generated](https://source.cloud.google.com/google.com:adux-source/studio-palettes/+/master:client/app/index.js;l=176)
 * and should be updated when the design team creates new colors.
 */
private const val PALETTE_JSON_FILENAME = "/palette/logcat-tags-palette.json"

/**
 * Manages the various colors used in a log view.
 *
 * Colors are stored as [TextAttributes] which can be assigned to
 * [com.intellij.openapi.editor.Document] [com.intellij.openapi.editor.markup.MarkupModel] ranges.
 *
 * [LogLevel] colors are provided with an extension method.
 *
 * Tag colors are assigned dynamically from a small pool and stored in a map.
 */
internal class LogcatColors {

  private val colorPaletteManager by lazy {
    javaClass.getResourceAsStream(PALETTE_JSON_FILENAME)?.let {
      ColorPaletteManager(
        Gson().fromJson(it.reader(), Array<ColorPaletteManager.ColorPalette>::class.java)
      )
    } ?: throw IllegalStateException("Resource not found")
  }
  private val tagColors = ConcurrentHashMap<Int, TextAttributes>()

  /** Map a [LogLevel] to a [TextAttributesKey] object for rendering a log level. */
  internal fun getLogLevelKey(level: LogLevel) =
    LEVEL_KEYS[level]
      ?: throw IllegalStateException("TextAttributesKey for log level $level is not registered")

  /** Map a [LogLevel] to a [TextAttributesKey] object for rendering a message. */
  internal fun getMessageKey(level: LogLevel) =
    MESSAGE_KEYS[level]
      ?: throw IllegalStateException("TextAttributesKey for log level $level is not registered")

  /**
   * Map a Logcat tag to a [TextAttributes] object.
   *
   * Leverage [ColorPaletteManager] for color selection but since we use [TextAttributes], we
   * maintain our own cache.
   */
  internal fun getTagColor(tag: String): TextAttributes {
    val index = tag.hashCode()
    return tagColors.computeIfAbsent(index) {
      // We only use the foreground colors. Background colors from the palette are ignored
      TextAttributes().apply { foregroundColor = colorPaletteManager.getForegroundColor(index) }
    }
  }

  companion object {
    val LEVEL_VERBOSE_KEY = TextAttributesKey.createTextAttributesKey("LOGCAT_V2_LEVEL_VERBOSE")
    val LEVEL_DEBUG_KEY = TextAttributesKey.createTextAttributesKey("LOGCAT_V2_LEVEL_DEBUG")
    val LEVEL_INFO_KEY = TextAttributesKey.createTextAttributesKey("LOGCAT_V2_LEVEL_INFO")
    val LEVEL_WARNING_KEY = TextAttributesKey.createTextAttributesKey("LOGCAT_V2_LEVEL_WARNING")
    val LEVEL_ERROR_KEY = TextAttributesKey.createTextAttributesKey("LOGCAT_V2_LEVEL_ERROR")
    val LEVEL_ASSERT_KEY = TextAttributesKey.createTextAttributesKey("LOGCAT_V2_LEVEL_ASSERT")

    private val LEVEL_KEYS =
      mapOf(
        LogLevel.VERBOSE to LEVEL_VERBOSE_KEY,
        LogLevel.DEBUG to LEVEL_DEBUG_KEY,
        LogLevel.INFO to LEVEL_INFO_KEY,
        LogLevel.WARN to LEVEL_WARNING_KEY,
        LogLevel.ERROR to LEVEL_ERROR_KEY,
        LogLevel.ASSERT to LEVEL_ASSERT_KEY,
      )

    val MESSAGE_VERBOSE_KEY = TextAttributesKey.createTextAttributesKey("LOGCAT_V2_MESSAGE_VERBOSE")
    val MESSAGE_DEBUG_KEY = TextAttributesKey.createTextAttributesKey("LOGCAT_V2_MESSAGE_DEBUG")
    val MESSAGE_INFO_KEY = TextAttributesKey.createTextAttributesKey("LOGCAT_V2_MESSAGE_INFO")
    val MESSAGE_WARNING_KEY = TextAttributesKey.createTextAttributesKey("LOGCAT_V2_MESSAGE_WARNING")
    val MESSAGE_ERROR_KEY = TextAttributesKey.createTextAttributesKey("LOGCAT_V2_MESSAGE_ERROR")
    val MESSAGE_ASSERT_KEY = TextAttributesKey.createTextAttributesKey("LOGCAT_V2_MESSAGE_ASSERT")

    private val MESSAGE_KEYS =
      mapOf(
        LogLevel.VERBOSE to MESSAGE_VERBOSE_KEY,
        LogLevel.DEBUG to MESSAGE_DEBUG_KEY,
        LogLevel.INFO to MESSAGE_INFO_KEY,
        LogLevel.WARN to MESSAGE_WARNING_KEY,
        LogLevel.ERROR to MESSAGE_ERROR_KEY,
        LogLevel.ASSERT to MESSAGE_ASSERT_KEY,
      )
  }
}
