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
package com.android.tools.idea.editors.literals.ui

import com.android.tools.idea.editors.literals.LITERAL_TEXT_ATTRIBUTE_KEY
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import org.jetbrains.android.util.AndroidBundle.message
import org.jetbrains.kotlin.idea.highlighter.KotlinColorSettingsPage
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingColors
import javax.swing.Icon

class LiveLiteralsHighlightColorConfigurable : ColorSettingsPage {
  override fun getHighlighter(): SyntaxHighlighter = KotlinColorSettingsPage().highlighter

  override fun getAdditionalHighlightingTagToDescriptorMap(): MutableMap<String,
    TextAttributesKey> {
    val attributes = HashMap<String, TextAttributesKey>()
    attributes["LL_HIGHLIGHT"] = LITERAL_TEXT_ATTRIBUTE_KEY
    attributes["ANNOTATION"] = KotlinHighlightingColors.ANNOTATION
    attributes["KEYWORD"] = KotlinHighlightingColors.KEYWORD
    attributes["FUNCTION_DECLARATION"] = KotlinHighlightingColors.FUNCTION_DECLARATION
    attributes["FUNCTION_PARAMETER"] = KotlinHighlightingColors.PARAMETER
    return attributes
  }

  override fun getIcon(): Icon? = null
  override fun getAttributeDescriptors(): Array<AttributesDescriptor> =
    arrayOf(AttributesDescriptor(message("live.literals.breadcrumbs.highlights"), LITERAL_TEXT_ATTRIBUTE_KEY))

  override fun getColorDescriptors(): Array<ColorDescriptor> = emptyArray()
  override fun getDisplayName(): String = message("live.literals")

  override fun getDemoText(): String {
    return """
      <ANNOTATION>@Composable</ANNOTATION>
      <KEYWORD>fun</KEYWORD> <FUNCTION_DECLARATION>Greeting</FUNCTION_DECLARATION>() {
               Text(<LL_HIGHLIGHT>"Hello"</LL_HIGHLIGHT>)
      }""".trimIndent()
  }
}