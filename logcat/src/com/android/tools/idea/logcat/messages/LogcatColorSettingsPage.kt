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
import com.android.tools.idea.logcat.message.LogLevel.ASSERT
import com.android.tools.idea.logcat.message.LogLevel.DEBUG
import com.android.tools.idea.logcat.message.LogLevel.ERROR
import com.android.tools.idea.logcat.message.LogLevel.INFO
import com.android.tools.idea.logcat.message.LogLevel.VERBOSE
import com.android.tools.idea.logcat.message.LogLevel.WARN
import com.android.utils.usLocaleCapitalize
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import icons.StudioIcons
import java.util.Locale
import javax.swing.Icon

/**
 * A [ColorSettingsPage] for the Logcat view.
 */
internal class LogcatColorSettingsPage : ColorSettingsPage {
  override fun getIcon(): Icon = StudioIcons.Shell.ToolWindows.LOGCAT

  override fun getHighlighter() = PlainSyntaxHighlighter()

  override fun getDemoText() = """
    Logcat:

    2022-01-06 14:19:34.697 <v> V </v> <vm>Sample verbose text</vm>
    2022-01-06 14:19:34.697 <d> D </d> <dm>Sample debug text</dm>
    2022-01-06 14:19:34.697 <i> I </i> <im>Sample info text</im>
    2022-01-06 14:19:34.697 <w> W </w> <wm>Sample warn text</wm>
    2022-01-06 14:19:34.697 <e> E </e> <em>Sample error text</em>
    2022-01-06 14:19:34.697 <a> A </a> <am>Sample assert text</am>
  """.trimIndent()

  override fun getAttributeDescriptors(): Array<AttributesDescriptor> = ATTRIBUTES_DESCRIPTORS

  override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

  override fun getDisplayName() = LogcatBundle.message("logcat.color.page.name")

  override fun getAdditionalHighlightingTagToDescriptorMap(): MutableMap<String, TextAttributesKey> = ADDITIONAL_HIGHLIGHT_DESCRIPTORS
}

private class LogcatDescriptorInfo(
  level: LogLevel,
  val levelKey: TextAttributesKey,
  val levelTag: String,
  val messageKey: TextAttributesKey,
  val messageTag: String,
) {
  val name = level.name.lowercase(Locale.getDefault()).usLocaleCapitalize()
}

private val DESCRIPTOR_INFO = arrayOf(
  LogcatDescriptorInfo(VERBOSE, LogcatColors.LEVEL_VERBOSE_KEY, "v", LogcatColors.MESSAGE_VERBOSE_KEY, "vm"),
  LogcatDescriptorInfo(DEBUG, LogcatColors.LEVEL_DEBUG_KEY, "d", LogcatColors.MESSAGE_DEBUG_KEY, "dm"),
  LogcatDescriptorInfo(INFO, LogcatColors.LEVEL_INFO_KEY, "i", LogcatColors.MESSAGE_INFO_KEY, "im"),
  LogcatDescriptorInfo(WARN, LogcatColors.LEVEL_WARNING_KEY, "w", LogcatColors.MESSAGE_WARNING_KEY, "wm"),
  LogcatDescriptorInfo(ERROR, LogcatColors.LEVEL_ERROR_KEY, "e", LogcatColors.MESSAGE_ERROR_KEY, "em"),
  LogcatDescriptorInfo(ASSERT, LogcatColors.LEVEL_ASSERT_KEY, "a", LogcatColors.MESSAGE_ASSERT_KEY, "am"),
)

private val ATTRIBUTES_DESCRIPTORS =
  (
    DESCRIPTOR_INFO.map { AttributesDescriptor(LogcatBundle.message("logcat.color.page.indicator", it.name), it.levelKey) } +
    DESCRIPTOR_INFO.map { AttributesDescriptor(LogcatBundle.message("logcat.color.page.message", it.name), it.messageKey) }
  ).toTypedArray()


private val ADDITIONAL_HIGHLIGHT_DESCRIPTORS = DESCRIPTOR_INFO.flatMap {
  listOf(it.levelTag to it.levelKey, it.messageTag to it.messageKey)
}.toMap().toMutableMap()
