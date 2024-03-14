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
package com.android.tools.idea.gradle.something

import com.android.tools.idea.gradle.something.parser.SomethingElementTypeHolder
import com.android.tools.idea.gradle.something.parser.SomethingHighlightingLexer
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType

enum class SomethingTextAttributes(fallback: TextAttributesKey) {
  NUMBER(DefaultLanguageHighlighterColors.NUMBER),
  STRING(DefaultLanguageHighlighterColors.STRING),
  LINE_COMMENT(DefaultLanguageHighlighterColors.LINE_COMMENT),
  KEYWORD(DefaultLanguageHighlighterColors.KEYWORD),
  ;

  val key = TextAttributesKey.createTextAttributesKey("Something_$name", fallback)
  val keys = arrayOf(key)
}

class SomethingHighlighter: SyntaxHighlighterBase() {
  override fun getHighlightingLexer(): Lexer = SomethingHighlightingLexer()

  override fun getTokenHighlights(tokenType: IElementType): Array<out TextAttributesKey> {
    return when (tokenType) {
      SomethingElementTypeHolder.STRING -> SomethingTextAttributes.STRING.keys
      SomethingElementTypeHolder.NUMBER -> SomethingTextAttributes.NUMBER.keys
      SomethingElementTypeHolder.BOOLEAN -> SomethingTextAttributes.KEYWORD.keys
      SomethingElementTypeHolder.LINE_COMMENT -> SomethingTextAttributes.LINE_COMMENT.keys
      SomethingElementTypeHolder.NULL -> SomethingTextAttributes.KEYWORD.keys
      else -> return TextAttributesKey.EMPTY_ARRAY
    }
  }
}

