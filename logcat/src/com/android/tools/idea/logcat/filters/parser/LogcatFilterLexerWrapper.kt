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
package com.android.tools.idea.logcat.filters.parser

import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.KEY
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.KVALUE
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.VALUE
import com.intellij.lexer.FlexLexer
import com.intellij.psi.tree.IElementType
import java.util.Stack

private val STRING_KEYS = listOf(
  "app",
  "line",
  "message",
  "msg",
  "package",
  "tag",
).joinToString("|")
private val KEYS = listOf(
  "age",
  "fromLevel",
  "level",
  "toLevel",
).joinToString("|")
private val KEY_VALUE_REGEX = "((-?($STRING_KEYS)~?)|($KEYS)):.*".toRegex()

/**
 * A wrapper around [LogcatFilterLexer] that allows to tweak its behavior.
 *
 * For example, in order to be able to treat `tag:foo` as a KEY-VALUE pair while treating `hello:world` as a top level value, it's easier
 * to let the flex code treat it as a top level value and then post process it and split into a KEY-VALUE pair if it has a valid key.
 */
internal class LogcatFilterLexerWrapper : FlexLexer {
  private val delegate = LogcatFilterLexer(null)

  // When we find a top-level value that is actually a key-value pair, we split it into key & value [Token]s and push them on this
  // stack.
  private val tokenStack = Stack<Token>()

  override fun yybegin(state: Int) {
    delegate.yybegin(state)
  }

  // yystate is only used internally by the Lexer, so we don't need to have it on the stack.
  override fun yystate(): Int = delegate.yystate()

  override fun getTokenStart(): Int = if (tokenStack.isEmpty()) delegate.tokenStart else tokenStack.peek().start

  override fun getTokenEnd() = if (tokenStack.isEmpty()) delegate.tokenEnd else tokenStack.peek().end

  override fun advance(): IElementType? {
    return if (tokenStack.isEmpty()) {
      val elementType = delegate.advance()
      val text = delegate.yytext()
      if (isHiddenKeyValuePair(elementType, text)) pushKeyValueTokens(text) else elementType
    }
    else {
      tokenStack.pop()
      // If the stack is empty, we advance the delegate. We don't need to check the next token because it can never be another VALUE.
      if (tokenStack.isEmpty()) delegate.advance() else tokenStack.first().elementType
    }
  }

  private fun pushKeyValueTokens(text: CharSequence): IElementType {
    val start = delegate.tokenStart
    val pos = start + text.indexOf(':') + 1
    tokenStack.push(Token(KVALUE, pos, tokenEnd))
    tokenStack.push(Token(KEY, start, pos))
    return KEY
  }

  override fun reset(buf: CharSequence?, start: Int, end: Int, initialState: Int) {
    delegate.reset(buf, start, end, initialState)
  }
}

private fun isHiddenKeyValuePair(elementType: IElementType?, text: CharSequence) =
  elementType == VALUE && KEY_VALUE_REGEX.matches(text)

private data class Token(val elementType: IElementType, val start: Int, val end: Int)
