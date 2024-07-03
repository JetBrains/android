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

import com.android.tools.idea.gradle.declarative.parser.DeclarativeElementTypeHolder.BLOCK_COMMENT
import com.android.tools.idea.gradle.declarative.parser.DeclarativeElementTypeHolder.LINE_COMMENT
import com.android.tools.idea.gradle.declarative.parser.DeclarativeLexer
import com.android.tools.idea.gradle.declarative.psi.DeclarativeFile
import com.intellij.lexer.Lexer
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.search.IndexPatternBuilder
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet

class DeclarativeTodoIndexPatternBuilder : IndexPatternBuilder {
  private val comments = TokenSet.create(BLOCK_COMMENT, LINE_COMMENT)

  override fun getIndexingLexer(file: PsiFile): Lexer? =
    if (file is DeclarativeFile) DeclarativeLexer() else null

  override fun getCommentTokenSet(file: PsiFile): TokenSet? =
    if (file is DeclarativeFile) comments else null

  override fun getCommentStartDelta(tokenType: IElementType?): Int = 2 // as both comments has two symbols as prefix // and /*

  override fun getCommentEndDelta(tokenType: IElementType?): Int =
    when (tokenType) {
      BLOCK_COMMENT -> "*/".length
      else -> 0
    }
}
