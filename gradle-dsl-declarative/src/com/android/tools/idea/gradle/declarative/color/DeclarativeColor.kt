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

import com.android.tools.idea.gradle.declarative.DeclarativeBundle.messagePointer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.options.OptionsBundle
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.util.NlsContexts.AttributeDescriptor
import java.util.function.Supplier

enum class DeclarativeColor(humanName: Supplier<@AttributeDescriptor String>, default: TextAttributesKey? = null) {
  COMMENT(messagePointer("color.settings.dcl.comments"), DefaultLanguageHighlighterColors.LINE_COMMENT),
  BLOCK_COMMENT(messagePointer("color.settings.dcl.block.comments"), DefaultLanguageHighlighterColors.BLOCK_COMMENT),

  BOOLEAN(messagePointer("color.settings.dcl.boolean"), DefaultLanguageHighlighterColors.KEYWORD),
  NUMBER(OptionsBundle.messagePointer("options.language.defaults.number"), DefaultLanguageHighlighterColors.NUMBER),
  NULL(messagePointer("color.settings.dcl.null"), DefaultLanguageHighlighterColors.KEYWORD),
  STRING(OptionsBundle.messagePointer("options.language.defaults.string"), DefaultLanguageHighlighterColors.STRING),
  ;

  val textAttributesKey: TextAttributesKey = TextAttributesKey.createTextAttributesKey("org.gradle.declarative.$name", default)
  val attributesDescriptor: AttributesDescriptor = AttributesDescriptor(humanName, textAttributesKey)
}
