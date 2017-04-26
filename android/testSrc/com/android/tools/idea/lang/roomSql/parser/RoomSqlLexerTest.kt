/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.lang.roomSql.parser

import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes.*
import com.google.common.truth.Truth
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import junit.framework.TestCase

class RoomSqlLexerTest : TestCase() {
  private fun assertTokenTypes(input: String, vararg expectedTokenTypes: Pair<String, IElementType>) {
    val lexer = RoomSqlLexer()
    val actualTokenTypes = mutableListOf<Pair<String, IElementType>>()
    lexer.start(input)
    while (lexer.tokenType != null) {
      actualTokenTypes.add(lexer.tokenText to lexer.tokenType!!)
      lexer.advance()
    }

    Truth.assertThat(actualTokenTypes).containsExactlyElementsIn(expectedTokenTypes.asIterable())
  }

  private val SPACE = " " to TokenType.WHITE_SPACE

  fun testSimpleQueries() {
    assertTokenTypes(
        "select foo from bar",
        "select" to SELECT,
        SPACE,
        "foo" to IDENTIFIER,
        SPACE,
        "from" to FROM,
        SPACE,
        "bar" to IDENTIFIER)

    assertTokenTypes("select -22", "select" to SELECT, SPACE, "-" to MINUS, "22" to NUMERIC_LITERAL)
  }

  fun testWhitespace() {
    val input = """
        select a,b
        from foo
        where baz >= :arg""".trimIndent()

    assertTokenTypes(
        input,
        "select" to SELECT,
        SPACE,
        "a" to IDENTIFIER,
        "," to COMMA,
        "b" to IDENTIFIER,
        "\n" to TokenType.WHITE_SPACE,
        "from" to FROM,
        SPACE,
        "foo" to IDENTIFIER,
        "\n" to TokenType.WHITE_SPACE,
        "where" to WHERE,
        SPACE,
        "baz" to IDENTIFIER,
        SPACE,
        ">=" to GTE,
        SPACE,
        ":arg" to PARAMETER_NAME)
  }

  fun testComments() {
    assertTokenTypes(
        "select 17 -- hello",
        "select" to SELECT,
        SPACE,
        "17" to NUMERIC_LITERAL,
        SPACE,
        "-- hello" to LINE_COMMENT)

    assertTokenTypes(
        "select 17 -- hello\nfrom bar",
        "select" to SELECT,
        SPACE,
        "17" to NUMERIC_LITERAL,
        SPACE,
        "-- hello" to LINE_COMMENT,
        "\n" to TokenType.WHITE_SPACE,
        "from" to FROM,
        SPACE,
        "bar" to IDENTIFIER)

    assertTokenTypes(
        "select /* hello */ 17",
        "select" to SELECT,
        SPACE,
        "/* hello */" to COMMENT,
        SPACE,
        "17" to NUMERIC_LITERAL)

    assertTokenTypes(
        "select /* hello",
        "select" to SELECT,
        SPACE,
        "/* hello" to COMMENT)
  }
}
