/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.declarative.color

import com.android.tools.idea.gradle.declarative.DeclarativeFileType
import com.android.tools.idea.gradle.declarative.DeclarativeHighlighter
import com.android.tools.idea.gradle.declarative.DeclarativeLanguage
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import javax.swing.Icon

class DeclarativeColorSettingsPage : ColorSettingsPage {

  private val attributesDescriptors = DeclarativeColor.values().map { it.attributesDescriptor }.toTypedArray()
  private val tagToDescriptorMap = DeclarativeColor.values().associateBy({ it.name }, { it.textAttributesKey })
  private val highlighterDemoText = """
    <COMMENT>// one line comment</COMMENT>
    <BLOCK_COMMENT>/* block comment */</BLOCK_COMMENT>
    android {
       namespace = <STRING>"com.example.myapplication"</STRING>
       compileSdk = <NUMBER>34</NUMBER>
       vectorDrawables {
            useSupportLibrary = <BOOLEAN>true</BOOLEAN>
       }
       toolchain {
           languageVersion = JavaLanguageVersion.of(<NULL>null</NULL>)
       }
    }
  """.trimIndent()

  override fun getDisplayName(): String = DeclarativeLanguage.getInstance().displayName
  override fun getHighlighter(): SyntaxHighlighter = DeclarativeHighlighter()
  override fun getIcon(): Icon = DeclarativeFileType.INSTANCE.icon
  override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> = tagToDescriptorMap
  override fun getAttributeDescriptors(): Array<AttributesDescriptor> = attributesDescriptors
  override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
  override fun getDemoText(): String = highlighterDemoText
}