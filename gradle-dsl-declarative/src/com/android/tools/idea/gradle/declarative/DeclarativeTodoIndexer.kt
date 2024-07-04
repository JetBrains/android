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
import com.intellij.lexer.Lexer
import com.intellij.psi.impl.cache.impl.BaseFilterLexer
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer
import com.intellij.psi.impl.cache.impl.todo.LexerBasedTodoIndexer
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.tree.TokenSet

class DeclarativeTodoIndexer : LexerBasedTodoIndexer() {
  override fun createLexer(consumer: OccurrenceConsumer): Lexer = object : BaseFilterLexer(DeclarativeLexer(), consumer) {
    override fun advance() {
      if (myDelegate.tokenType in TokenSet.create(BLOCK_COMMENT, LINE_COMMENT)) {
        scanWordsInToken(UsageSearchContext.IN_COMMENTS.toInt(), false, false)
        advanceTodoItemCountsInToken()
      }
      myDelegate.advance()
    }
  }
}