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
package com.android.tools.idea.compose.preview.util.device

import com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecLexer
import com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecTypes
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingColors

private val prefix =
  arrayOf(
    TextAttributesKey.createTextAttributesKey("PREFIX", DefaultLanguageHighlighterColors.STRING)
  )
private val paramName =
  arrayOf(
    TextAttributesKey.createTextAttributesKey("PARAM", KotlinHighlightingColors.NAMED_ARGUMENT)
  )
private val separator =
  arrayOf(
    TextAttributesKey.createTextAttributesKey("SEPARATOR", DefaultLanguageHighlighterColors.COMMA)
  )
private val operator =
  arrayOf(
    TextAttributesKey.createTextAttributesKey("OPERATOR", KotlinHighlightingColors.NAMED_ARGUMENT)
  )
private val primitive =
  arrayOf(
    TextAttributesKey.createTextAttributesKey("PRIMITIVE", DefaultLanguageHighlighterColors.NUMBER)
  )
private val unit =
  arrayOf(
    TextAttributesKey.createTextAttributesKey("UNIT", KotlinHighlightingColors.EXTENSION_PROPERTY)
  )
private val string =
  arrayOf(
    TextAttributesKey.createTextAttributesKey("STRING", DefaultLanguageHighlighterColors.CONSTANT)
  )

class DeviceSpecSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
  override fun getSyntaxHighlighter(
    project: Project?,
    virtualFile: VirtualFile?
  ): SyntaxHighlighter {
    return DeviceSpecSyntaxHighlighter()
  }
}

class DeviceSpecSyntaxHighlighter : SyntaxHighlighterBase() {
  override fun getHighlightingLexer(): Lexer {
    return DeviceSpecLexer()
  }

  override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> =
    when (tokenType) {
      DeviceSpecTypes.ID_KEYWORD,
      DeviceSpecTypes.SPEC_KEYWORD,
      DeviceSpecTypes.NAME_KEYWORD,
      DeviceSpecTypes.COLON -> prefix
      DeviceSpecTypes.NUMERIC_T, DeviceSpecTypes.TRUE, DeviceSpecTypes.FALSE -> primitive
      DeviceSpecTypes.PORTRAIT_KEYWORD,
      DeviceSpecTypes.LANDSCAPE_KEYWORD,
      DeviceSpecTypes.STRING_T -> string
      DeviceSpecTypes.PX, DeviceSpecTypes.DP -> unit
      DeviceSpecTypes.COMMA -> separator
      DeviceSpecTypes.EQUALS -> operator
      DeviceSpecTypes.PARENT_KEYWORD,
      DeviceSpecTypes.WIDTH_KEYWORD,
      DeviceSpecTypes.HEIGHT_KEYWORD,
      DeviceSpecTypes.DPI_KEYWORD,
      DeviceSpecTypes.IS_ROUND_KEYWORD,
      DeviceSpecTypes.CHIN_SIZE_KEYWORD,
      DeviceSpecTypes.ORIENTATION_KEYWORD -> paramName
      else -> emptyArray()
    }
}
