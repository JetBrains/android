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

import com.android.tools.idea.logcat.filters.parser.LogcatFilterLexer.KVALUE_STATE
import com.android.tools.idea.logcat.filters.parser.LogcatFilterLexer.REGEX_KVALUE_STATE
import com.android.tools.idea.logcat.filters.parser.LogcatFilterLexer.STRING_KVALUE_STATE
import com.android.tools.idea.logcat.filters.parser.LogcatFilterLexer.YYINITIAL
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.KEY
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.KVALUE
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.REGEX_KEY
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.REGEX_KVALUE
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.STRING_KEY
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.STRING_KVALUE
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.VALUE
import com.intellij.lexer.FlexLexer
import com.intellij.psi.tree.IElementType
import java.util.Stack

private val STRING_KEYS_REGEX = listOf(
  "app",
  "line",
  "message",
  "msg",
  "package",
  "tag",
).joinToString("|")
val KEYS = listOf(
  "age",
  "fromLevel",
  "level",
  "toLevel",
)
private val KEYS_REGEX = KEYS.joinToString("|")
private val KEY_VALUE_REGEX = "((-?($STRING_KEYS_REGEX)~?)|($KEYS_REGEX)):.*".toRegex()

/**
 * A wrapper around [LogcatFilterLexer] that allows to tweak its behavior.
 *
 * For example, in order to be able to treat `tag:foo` as a KEY-VALUE pair while treating `hello:world` as a top level value, it's easier
 * to let the flex code treat it as a top level value and then post process it and split into a KEY-VALUE pair if it has a valid key.
 */
internal class LogcatFilterLexerWrapper : FlexLexer {
  private var buf: CharSequence? = null
  private val delegate = LogcatFilterLexer(null)

  // When we find a top-level value that is actually a key-value pair, we split it into key & value [Token]s and push them on this
  // stack.
  private val tokenStack = Stack<Token>()

  override fun yybegin(state: Int) {
    delegate.yybegin(state)
  }

  override fun yystate(): Int = if (tokenStack.isEmpty()) delegate.yystate() else tokenStack.peek().state

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
    val colon = text.indexOf(':')
    val pos = start + colon + 1

    val (keyType, valueType, state) = when {
      KEYS.contains(text.substring(0, colon)) -> TokenValues(KEY, KVALUE, KVALUE_STATE)
      text[colon - 1] == '~' -> TokenValues(REGEX_KEY, REGEX_KVALUE, REGEX_KVALUE_STATE)
      else -> TokenValues(STRING_KEY, STRING_KVALUE, STRING_KVALUE_STATE)
    }
    tokenStack.push(Token(valueType, pos, tokenEnd, YYINITIAL))
    tokenStack.push(Token(keyType, start, pos, state))
    return keyType
  }

  override fun reset(buf: CharSequence?, start: Int, end: Int, initialState: Int) {
    this.buf = buf
    delegate.reset(buf, start, end, initialState)
    tokenStack.clear()
  }
}

private fun isHiddenKeyValuePair(elementType: IElementType?, text: CharSequence) =
  elementType == VALUE && KEY_VALUE_REGEX.matches(text)

private data class TokenValues(val keyType: IElementType, val valueType: IElementType, val state: Int)

private data class Token(val elementType: IElementType, val start: Int, val end: Int, val state: Int)
