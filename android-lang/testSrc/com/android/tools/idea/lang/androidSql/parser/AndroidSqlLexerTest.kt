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
package com.android.tools.idea.lang.androidSql.parser

import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.BACKTICK_LITERAL
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.BRACKET_LITERAL
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.COMMA
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.COMMENT
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.CREATE
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.CURRENT_TIME
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.CURRENT_TIMESTAMP
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.DOT
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.DOUBLE_QUOTE_STRING_LITERAL
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.FROM
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.GTE
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.IDENTIFIER
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.LINE_COMMENT
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.LPAREN
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.MINUS
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.NAMED_PARAMETER
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.NULL
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.NUMBERED_PARAMETER
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.NUMERIC_LITERAL
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.PLUS
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.RPAREN
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.SELECT
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.SEMICOLON
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.SINGLE_QUOTE_STRING_LITERAL
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.STAR
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.TABLE
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.WHERE
import com.android.tools.idea.lang.androidSql.psi.UNTERMINATED_BACKTICK_LITERAL
import com.android.tools.idea.lang.androidSql.psi.UNTERMINATED_BRACKET_LITERAL
import com.android.tools.idea.lang.androidSql.psi.UNTERMINATED_DOUBLE_QUOTE_STRING_LITERAL
import com.android.tools.idea.lang.androidSql.psi.UNTERMINATED_SINGLE_QUOTE_STRING_LITERAL
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.TokenType
import com.intellij.psi.TokenType.BAD_CHARACTER
import com.intellij.psi.tree.IElementType
import junit.framework.TestCase

class AndroidSqlLexerTest : TestCase() {
  private fun assertTokenTypes(input: String, vararg expectedTokenTypes: Pair<String, IElementType>) {
    val lexer = AndroidSqlLexer()
    val actualTokenTypes = mutableListOf<Pair<String, IElementType>>()
    lexer.start(input)
    while (lexer.tokenType != null) {
      actualTokenTypes.add(lexer.tokenText to lexer.tokenType!!)
      lexer.advance()
    }

    assertThat(actualTokenTypes).containsExactlyElementsIn(expectedTokenTypes.asIterable())
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
        ":arg" to NAMED_PARAMETER)
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

  fun testIdentifiers() {
    assertTokenTypes(
        "select * from _table",
        "select" to SELECT,
        SPACE,
        "*" to STAR,
        SPACE,
        "from" to FROM,
        SPACE,
        "_table" to IDENTIFIER)

    assertTokenTypes(
        "select null, nulls, current_time, current_times, current_timestamp",
        "select" to SELECT,
        SPACE,
        "null" to NULL,
        "," to COMMA,
        SPACE,
        "nulls" to IDENTIFIER,
        "," to COMMA,
        SPACE,
        "current_time" to CURRENT_TIME,
        "," to COMMA,
        SPACE,
        "current_times" to IDENTIFIER,
        "," to COMMA,
        SPACE,
        "current_timestamp" to CURRENT_TIMESTAMP
    )

    assertTokenTypes(
        "select :P1, :_p2, :3p",
        "select" to SELECT,
        SPACE,
        ":P1" to NAMED_PARAMETER,
        "," to COMMA,
        SPACE,
        ":_p2" to NAMED_PARAMETER,
        SPACE,
        "," to COMMA,
        ":3p" to NAMED_PARAMETER)

    assertTokenTypes(
        "select :P1, ? from foo",
        "select" to SELECT,
        SPACE,
        ":P1" to NAMED_PARAMETER,
        "," to COMMA,
        SPACE,
        "?" to NUMBERED_PARAMETER,
        SPACE,
        "from" to FROM,
        SPACE,
        "foo" to IDENTIFIER)

    assertTokenTypes(
        "select ::P1",
        "select" to SELECT,
        SPACE,
        ":" to BAD_CHARACTER,
        ":P1" to NAMED_PARAMETER)

    assertTokenTypes(
        "select [table].[column] from [database].[column]",
        "select" to SELECT,
        SPACE,
        "[table]" to BRACKET_LITERAL,
        "." to DOT,
        "[column]" to BRACKET_LITERAL,
        SPACE,
        "from" to FROM,
        SPACE,
        "[database]" to BRACKET_LITERAL,
        "." to DOT,
        "[column]" to BRACKET_LITERAL)

    assertTokenTypes(
        "select `table`.`column` from `database`.`column`",
        "select" to SELECT,
        SPACE,
        "`table`" to BACKTICK_LITERAL,
        "." to DOT,
        "`column`" to BACKTICK_LITERAL,
        SPACE,
        "from" to FROM,
        SPACE,
        "`database`" to BACKTICK_LITERAL,
        "." to DOT,
        "`column`" to BACKTICK_LITERAL)

    assertTokenTypes(
        "select 11*11.22e33+11e+22-11.22e-33",
        "select" to SELECT,
        SPACE,
        "11" to NUMERIC_LITERAL,
        "*" to STAR,
        "11.22e33" to NUMERIC_LITERAL,
        "+" to PLUS,
        "11e+22" to NUMERIC_LITERAL,
        "-" to MINUS,
        "11.22e-33" to NUMERIC_LITERAL)
  }

  fun testStrings() {
    assertTokenTypes(
        """select "",'foo''bar','foo"bar'""",
        "select" to SELECT,
        SPACE,
        "\"\"" to DOUBLE_QUOTE_STRING_LITERAL,
        "," to COMMA,
        "'foo''bar'" to SINGLE_QUOTE_STRING_LITERAL,
        "," to COMMA,
        "'foo\"bar'" to SINGLE_QUOTE_STRING_LITERAL)

    assertTokenTypes(
        """CREATE TABLE "TABLE"("#!@""'☺\", "");""",
        "CREATE" to CREATE,
        SPACE,
        "TABLE" to TABLE,
        SPACE,
        """"TABLE"""" to DOUBLE_QUOTE_STRING_LITERAL,
        "(" to LPAREN,
        """"#!@""'☺\"""" to DOUBLE_QUOTE_STRING_LITERAL,
        "," to COMMA,
        SPACE,
        "\"\"" to DOUBLE_QUOTE_STRING_LITERAL,
        ")" to RPAREN,
        ";" to SEMICOLON)

    assertTokenTypes(
        """select x'blob'""",
        "select" to SELECT,
        SPACE,
        "x'blob'" to SINGLE_QUOTE_STRING_LITERAL)

    assertTokenTypes(
        """select 'unterminated string""",
        "select" to SELECT,
        SPACE,
        "'unterminated string" to UNTERMINATED_SINGLE_QUOTE_STRING_LITERAL)

    assertTokenTypes(
        """select 'unterminated '' string""",
        "select" to SELECT,
        SPACE,
        "'unterminated '' string" to UNTERMINATED_SINGLE_QUOTE_STRING_LITERAL)

    assertTokenTypes(
        """select X"unterminated blob""",
        "select" to SELECT,
        SPACE,
        "X\"unterminated blob" to UNTERMINATED_DOUBLE_QUOTE_STRING_LITERAL)

    assertTokenTypes(
        """select X"unterminated "" blob""",
        "select" to SELECT,
        SPACE,
        "X\"unterminated \"\" blob" to UNTERMINATED_DOUBLE_QUOTE_STRING_LITERAL)

    assertTokenTypes(
        """select [unterminated bracket""",
        "select" to SELECT,
        SPACE,
        "[unterminated bracket" to UNTERMINATED_BRACKET_LITERAL)

    assertTokenTypes(
        "select `foo``bar`",
        "select" to SELECT,
        SPACE,
        "`foo``bar`" to BACKTICK_LITERAL)

    assertTokenTypes(
        """select `unterminated backtick""",
        "select" to SELECT,
        SPACE,
        "`unterminated backtick" to UNTERMINATED_BACKTICK_LITERAL)
  }

  fun testNeedsQuoting() {
    assertFalse(AndroidSqlLexer.needsQuoting("foo"))
    assertTrue(AndroidSqlLexer.needsQuoting("select"))
    assertTrue(AndroidSqlLexer.needsQuoting("foo.bar"))
    assertTrue(AndroidSqlLexer.needsQuoting("foo'bar"))
    assertTrue(AndroidSqlLexer.needsQuoting("foo bar"))
    assertTrue(AndroidSqlLexer.needsQuoting("foo`bar"))
    assertTrue(AndroidSqlLexer.needsQuoting(":foo"))
    assertTrue(AndroidSqlLexer.needsQuoting("@foo"))
    assertTrue(AndroidSqlLexer.needsQuoting("?foo"))
    assertTrue(AndroidSqlLexer.needsQuoting("\$foo"))
  }

  fun testValidName() {
    assertThat(AndroidSqlLexer.getValidName("foo")).isEqualTo("foo")
    assertThat(AndroidSqlLexer.getValidName("Order")).isEqualTo("`Order`")
    assertThat(AndroidSqlLexer.getValidName("foo bar")).isEqualTo("`foo bar`")
    assertThat(AndroidSqlLexer.getValidName("foo'bar'baz")).isEqualTo("`foo'bar'baz`")
    assertThat(AndroidSqlLexer.getValidName("foo`bar`baz")).isEqualTo("`foo``bar``baz`")
    assertThat(AndroidSqlLexer.getValidName("\$foo")).isEqualTo("`\$foo`")
  }
}
