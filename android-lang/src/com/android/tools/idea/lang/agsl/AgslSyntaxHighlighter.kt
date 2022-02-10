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
package com.android.tools.idea.lang.agsl

import com.android.tools.idea.flags.StudioFlags
import com.intellij.lexer.EmptyLexer
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType

/**
 * Basic syntax highlighter that highlights the keywords and comments.
 */
class AgslSyntaxHighlighter : SyntaxHighlighterBase() {
  override fun getHighlightingLexer(): Lexer = if (StudioFlags.AGSL_LANGUAGE_SUPPORT.get()) AgslLexer() else EmptyLexer()

  override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> = when (tokenType) {
    in AgslTokenTypeSets.KEYWORDS -> pack(DefaultLanguageHighlighterColors.KEYWORD)
    in AgslTokenTypeSets.NUMBERS -> pack(DefaultLanguageHighlighterColors.NUMBER)
    in AgslTokenTypeSets.OPERATORS -> pack(DefaultLanguageHighlighterColors.OPERATION_SIGN)
    AgslTokenTypes.COMMENT -> pack(DefaultLanguageHighlighterColors.LINE_COMMENT)
    AgslTokenTypes.BLOCK_COMMENT -> pack(DefaultLanguageHighlighterColors.BLOCK_COMMENT)
    else -> TextAttributesKey.EMPTY_ARRAY
  }
}