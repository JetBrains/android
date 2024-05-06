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
package com.android.tools.idea.logcat.filters

import com.android.tools.idea.logcat.filters.parser.LogcatFilterFileType
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage

private val DESCRIPTORS =
  arrayOf(
    AttributesDescriptor("Key", LogcatFilterTextAttributes.KEY.key),
    AttributesDescriptor("Value", LogcatFilterTextAttributes.KVALUE.key),
    AttributesDescriptor("String value", LogcatFilterTextAttributes.STRING_KVALUE.key),
    AttributesDescriptor("Regex value", LogcatFilterTextAttributes.REGEX_KVALUE.key),
    AttributesDescriptor("Text", LogcatFilterTextAttributes.VALUE.key),
  )

/** A [ColorSettingsPage] for the Logcat Filter Language. */
internal class LogcatFilterColorSettingsPage : ColorSettingsPage {
  override fun getIcon() = LogcatFilterFileType.icon

  override fun getHighlighter() = LogcatFilterSyntaxHighlighter()

  override fun getDemoText() =
    """
    // Note that the selected line does not show background color

    tag:foo bar line~:Foo|Bar level:DEBUG
  """
      .trimIndent()

  override fun getAttributeDescriptors() = DESCRIPTORS

  override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

  override fun getDisplayName() = "Logcat Filter"

  override fun getAdditionalHighlightingTagToDescriptorMap():
    MutableMap<String, TextAttributesKey>? = null
}
