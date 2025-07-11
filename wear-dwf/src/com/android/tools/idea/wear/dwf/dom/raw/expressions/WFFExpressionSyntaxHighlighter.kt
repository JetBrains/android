/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.wear.dwf.dom.raw.expressions

import com.android.tools.idea.wear.dwf.dom.raw.expressions.WFFExpressionTypes.COMMA
import com.android.tools.idea.wear.dwf.dom.raw.expressions.WFFExpressionTypes.DOT
import com.android.tools.idea.wear.dwf.dom.raw.expressions.WFFExpressionTypes.ID
import com.android.tools.idea.wear.dwf.dom.raw.expressions.WFFExpressionTypes.NULL
import com.android.tools.idea.wear.dwf.dom.raw.expressions.WFFExpressionTypes.NUMBER
import com.android.tools.idea.wear.dwf.dom.raw.expressions.WFFExpressionTypes.OPERATORS
import com.android.tools.idea.wear.dwf.dom.raw.expressions.WFFExpressionTypes.QUOTED_STRING
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.TokenType.BAD_CHARACTER
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet

private val PARENTHESES =
  TokenSet.create(WFFExpressionTypes.OPEN_PAREN, WFFExpressionTypes.CLOSE_PAREN)

private val BRACKETS =
  TokenSet.create(WFFExpressionTypes.OPEN_BRACKET, WFFExpressionTypes.CLOSE_BRACKET)

enum class WFFExpressionTextAttributes(fallback: TextAttributesKey) {
  BAD_CHARACTER(HighlighterColors.BAD_CHARACTER),
  DATA_SOURCE(DefaultLanguageHighlighterColors.STATIC_FIELD),
  CONFIGURATION(DefaultLanguageHighlighterColors.INSTANCE_FIELD),
  PARENTHESES(DefaultLanguageHighlighterColors.PARENTHESES),
  BRACKETS(DefaultLanguageHighlighterColors.BRACKETS),
  STRING(DefaultLanguageHighlighterColors.STRING),
  FUNCTION_ID(DefaultLanguageHighlighterColors.STATIC_METHOD),
  ID(DefaultLanguageHighlighterColors.IDENTIFIER),
  COMMA(DefaultLanguageHighlighterColors.COMMA),
  DOT(DefaultLanguageHighlighterColors.DOT),
  NULL(DefaultLanguageHighlighterColors.KEYWORD),
  OPERATORS(DefaultLanguageHighlighterColors.OPERATION_SIGN),
  NUMBER(DefaultLanguageHighlighterColors.NUMBER);

  val key = TextAttributesKey.createTextAttributesKey("WFF_expression_$name", fallback)
  val keys = arrayOf(key)
}

/**
 * [SyntaxHighlighter] that highlights the Lexer output (the leaf tokens) of WFF expressions.
 *
 * More complicated structures are highlighted with [WFFExpressionAnnotator].
 */
class WFFExpressionSyntaxHighlighter : SyntaxHighlighterBase() {
  override fun getHighlightingLexer() = WFFExpressionLexer()

  override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
    return when (tokenType) {
      in PARENTHESES -> WFFExpressionTextAttributes.PARENTHESES.keys
      in BRACKETS -> WFFExpressionTextAttributes.BRACKETS.keys
      NUMBER -> WFFExpressionTextAttributes.NUMBER.keys
      QUOTED_STRING -> WFFExpressionTextAttributes.STRING.keys
      ID -> WFFExpressionTextAttributes.ID.keys
      OPERATORS -> WFFExpressionTextAttributes.OPERATORS.keys
      COMMA -> WFFExpressionTextAttributes.COMMA.keys
      DOT -> WFFExpressionTextAttributes.DOT.keys
      NULL -> WFFExpressionTextAttributes.NULL.keys
      BAD_CHARACTER -> WFFExpressionTextAttributes.BAD_CHARACTER.keys
      else -> TextAttributesKey.EMPTY_ARRAY
    }
  }
}

class WFFExpressionSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
  override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?) =
    WFFExpressionSyntaxHighlighter()
}
