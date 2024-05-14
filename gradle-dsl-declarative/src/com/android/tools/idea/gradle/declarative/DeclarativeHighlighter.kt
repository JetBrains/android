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
package com.android.tools.idea.gradle.declarative

import com.android.tools.idea.gradle.declarative.parser.DeclarativeElementTypeHolder
import com.android.tools.idea.gradle.declarative.parser.DeclarativeElementTypeHolder.BLOCK_COMMENT
import com.android.tools.idea.gradle.declarative.parser.DeclarativeElementTypeHolder.BOOLEAN
import com.android.tools.idea.gradle.declarative.parser.DeclarativeElementTypeHolder.LINE_COMMENT
import com.android.tools.idea.gradle.declarative.parser.DeclarativeElementTypeHolder.NULL
import com.android.tools.idea.gradle.declarative.parser.DeclarativeElementTypeHolder.NUMBER
import com.android.tools.idea.gradle.declarative.parser.DeclarativeElementTypeHolder.STRING
import com.android.tools.idea.gradle.declarative.parser.DeclarativeHighlightingLexer
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType

enum class DeclarativeTextAttributes(fallback: TextAttributesKey) {
  NUMBER(DefaultLanguageHighlighterColors.NUMBER),
  STRING(DefaultLanguageHighlighterColors.STRING),
  LINE_COMMENT(DefaultLanguageHighlighterColors.LINE_COMMENT),
  BLOCK_COMMENT(DefaultLanguageHighlighterColors.BLOCK_COMMENT),
  KEYWORD(DefaultLanguageHighlighterColors.KEYWORD),
  ;

  val key = TextAttributesKey.createTextAttributesKey("Declarative_$name", fallback)
  val keys = arrayOf(key)
}

class DeclarativeHighlighter: SyntaxHighlighterBase() {
  override fun getHighlightingLexer(): Lexer = DeclarativeHighlightingLexer()

  override fun getTokenHighlights(tokenType: IElementType): Array<out TextAttributesKey> {
    return when (tokenType) {
      STRING -> DeclarativeTextAttributes.STRING.keys
      NUMBER -> DeclarativeTextAttributes.NUMBER.keys
      BOOLEAN -> DeclarativeTextAttributes.KEYWORD.keys
      LINE_COMMENT -> DeclarativeTextAttributes.LINE_COMMENT.keys
      BLOCK_COMMENT -> DeclarativeTextAttributes.BLOCK_COMMENT.keys
      NULL -> DeclarativeTextAttributes.KEYWORD.keys
      else -> return TextAttributesKey.EMPTY_ARRAY
    }
  }
}

