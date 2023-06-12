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

import com.android.tools.idea.logcat.filters.parser.LogcatFilterLexer.YYINITIAL
import com.intellij.psi.tree.IElementType

private val lexer = LogcatFilterLexer(null)

/**
 * A simple engine that pumps string through the Lexer, so we can debug issues.
 */
fun main(args: Array<String>) {

  for (text in args) {
    println("===============================================")
    println("Parsing $text")
    println("  ${TestLexer.parse(text).joinToString("\n  ")}")
  }
}

object TestLexer {
  internal fun parse(text: String): MutableList<TokenInfo> {
    lexer.reset(text, 0, text.length, 0)
    val tokens = mutableListOf<TokenInfo>()
    while (true) {
      val element = lexer.advance() ?: break
      tokens.add(TokenInfo(element, lexer.yytext(text), lexer.yystate()))
    }
    return tokens
  }
}

private fun LogcatFilterLexer.yytext(text: String) = text.substring(tokenStart, tokenEnd)

internal data class TokenInfo(val name: IElementType, val text: String, val state: Int = YYINITIAL)
