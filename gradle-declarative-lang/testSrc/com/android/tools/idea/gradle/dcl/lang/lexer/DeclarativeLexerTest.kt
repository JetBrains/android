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
package com.android.tools.idea.gradle.dcl.lang.lexer

import com.android.tools.idea.gradle.dcl.lang.DeclarativeParserDefinition
import com.intellij.testFramework.LexerTestCase

class DeclarativeLexerTest : LexerTestCase() {
  fun testLineComment() {
    doTest(
      """
        // abc
        
        // defg
      """.trimIndent(),
      """
        DeclarativeTokenType.line_comment ('// abc')
        WHITE_SPACE ('\n\n')
        DeclarativeTokenType.line_comment ('// defg')
      """.trimIndent()
    )
  }

  fun testBlockComment() {
    doTest(
      """
        /* foo */ "abc"
      """.trimIndent(),
      """
        DeclarativeTokenType.BLOCK_COMMENT ('/* foo */')
        WHITE_SPACE (' ')
        DeclarativeTokenType.one_line_string_literal ('"abc"')
      """.trimIndent()
    )
  }

  fun testKDocComment() {
    doTest(
      """
        /** foo */ "abc"
      """.trimIndent(),
      """
        DeclarativeTokenType.BLOCK_COMMENT ('/** foo */')
        WHITE_SPACE (' ')
        DeclarativeTokenType.one_line_string_literal ('"abc"')
      """.trimIndent()
    )
  }

  fun testMultiLineBlockComment() {
    doTest(
      """
        /*
         * foo
         */ "abc"
      """.trimIndent(),
      """
        DeclarativeTokenType.BLOCK_COMMENT ('/*\n * foo\n */')
        WHITE_SPACE (' ')
        DeclarativeTokenType.one_line_string_literal ('"abc"')
      """.trimIndent()
    )
  }

  fun testMixedComment() {
    doTest(
      """
        /* foo // bar */ "abc"
      """.trimIndent(),
      """
        DeclarativeTokenType.BLOCK_COMMENT ('/* foo // bar */')
        WHITE_SPACE (' ')
        DeclarativeTokenType.one_line_string_literal ('"abc"')
      """.trimIndent()
    )
  }

  fun testNestedComments() {
    doTest(
      """
        /* foo
           bar /* baz */
           quux
         */ "abc"
      """.trimIndent(),
      """
        DeclarativeTokenType.BLOCK_COMMENT ('/* foo\n   bar /* baz */\n   quux\n */')
        WHITE_SPACE (' ')
        DeclarativeTokenType.one_line_string_literal ('"abc"')
      """.trimIndent()
    )
  }

  fun testBlockCommentEdgeCases() {
    doTest(
      """
        /**/ "abc"
        /***/ "def"
      """.trimIndent(),
      """
      DeclarativeTokenType.BLOCK_COMMENT ('/**/')
      WHITE_SPACE (' ')
      DeclarativeTokenType.one_line_string_literal ('"abc"')
      WHITE_SPACE ('\n')
      DeclarativeTokenType.BLOCK_COMMENT ('/***/')
      WHITE_SPACE (' ')
      DeclarativeTokenType.one_line_string_literal ('"def"')
      """.trimIndent()
    )
  }

  fun testString() {
    doTest(
      """
        "abc" "def" "\t\n" "\uF0FF" "$"
      """.trimIndent(),
      """
      DeclarativeTokenType.one_line_string_literal ('"abc"')
      WHITE_SPACE (' ')
      DeclarativeTokenType.one_line_string_literal ('"def"')
      WHITE_SPACE (' ')
      DeclarativeTokenType.one_line_string_literal ('"\t\n"')
      WHITE_SPACE (' ')
      DeclarativeTokenType.one_line_string_literal ('"\uF0FF"')
      WHITE_SPACE (' ')
      DeclarativeTokenType.one_line_string_literal ('"$"')
      """.trimIndent()
    )
  }

  fun testComma() {
    doTest(
      """
        "foo", "bar"
      """.trimIndent(),
      """
        DeclarativeTokenType.one_line_string_literal ('"foo"')
        DeclarativeTokenType., (',')
        WHITE_SPACE (' ')
        DeclarativeTokenType.one_line_string_literal ('"bar"')
      """.trimIndent()
    )
  }

  fun testNumbers() {
    doTest(
      """
        1
        123
        123_123
        123L
        123UL
        0xFFF
        0xFFFU
        0xFFFUL
        0b0111
        0b0111L
        0b0111UL
      """.trimIndent(),
      """
      DeclarativeTokenType.integer_literal ('1')
      WHITE_SPACE ('\n')
      DeclarativeTokenType.integer_literal ('123')
      WHITE_SPACE ('\n')
      DeclarativeTokenType.integer_literal ('123_123')
      WHITE_SPACE ('\n')
      DeclarativeTokenType.long_literal ('123L')
      WHITE_SPACE ('\n')
      DeclarativeTokenType.unsigned_long ('123UL')
      WHITE_SPACE ('\n')
      DeclarativeTokenType.integer_literal ('0xFFF')
      WHITE_SPACE ('\n')
      DeclarativeTokenType.unsigned_integer ('0xFFFU')
      WHITE_SPACE ('\n')
      DeclarativeTokenType.unsigned_long ('0xFFFUL')
      WHITE_SPACE ('\n')
      DeclarativeTokenType.integer_literal ('0b0111')
      WHITE_SPACE ('\n')
      DeclarativeTokenType.long_literal ('0b0111L')
      WHITE_SPACE ('\n')
      DeclarativeTokenType.unsigned_long ('0b0111UL')
      """.trimIndent()
    )
  }

  fun testFloatNumbers() {
    doTest(
      """
        0.1
        .1
        0_1.0
        0.1e+1
        0_0.1e+1
        0_0.1E+2
      """.trimIndent(),
      """
      DeclarativeTokenType.double_literal ('0.1')
      WHITE_SPACE ('\n')
      DeclarativeTokenType.double_literal ('.1')
      WHITE_SPACE ('\n')
      DeclarativeTokenType.double_literal ('0_1.0')
      WHITE_SPACE ('\n')
      DeclarativeTokenType.double_literal ('0.1e+1')
      WHITE_SPACE ('\n')
      DeclarativeTokenType.double_literal ('0_0.1e+1')
      WHITE_SPACE ('\n')
      DeclarativeTokenType.double_literal ('0_0.1E+2')
      """.trimIndent()
    )
  }

  fun testBoolean() {
    doTest(
      """
        true
        false
      """.trimIndent(),
      """
        DeclarativeTokenType.boolean ('true')
        WHITE_SPACE ('\n')
        DeclarativeTokenType.boolean ('false')
      """.trimIndent()
    )
    """""""""""".trimIndent()
  }

  fun testNull() {
    doTest("null", "DeclarativeTokenType.null ('null')")
  }

  fun testToken() {
    doTest(
      """
        abc def.ghi
      """.trimIndent(),
      """
        DeclarativeTokenType.token ('abc')
        WHITE_SPACE (' ')
        DeclarativeTokenType.token ('def')
        DeclarativeTokenType.. ('.')
        DeclarativeTokenType.token ('ghi')
      """.trimIndent())
  }

  fun testToken2() {
    doTest(
      """
        name age _count student1 calculateArea `_name_` `$%&^%&^` __hello_ `___` _12_3_
      """.trimIndent(),
      """
      DeclarativeTokenType.token ('name')
      WHITE_SPACE (' ')
      DeclarativeTokenType.token ('age')
      WHITE_SPACE (' ')
      DeclarativeTokenType.token ('_count')
      WHITE_SPACE (' ')
      DeclarativeTokenType.token ('student1')
      WHITE_SPACE (' ')
      DeclarativeTokenType.token ('calculateArea')
      WHITE_SPACE (' ')
      DeclarativeTokenType.token ('`_name_`')
      WHITE_SPACE (' ')
      DeclarativeTokenType.token ('`$%&^%&^`')
      WHITE_SPACE (' ')
      DeclarativeTokenType.token ('__hello_')
      WHITE_SPACE (' ')
      DeclarativeTokenType.token ('`___`')
      WHITE_SPACE (' ')
      DeclarativeTokenType.token ('_12_3_')
      """.trimIndent())
  }

  fun testWrongIdentifier() {
    doTest(
      """
        1name `` _ __ _____
      """.trimIndent(),
      """
      DeclarativeTokenType.integer_literal ('1')
      DeclarativeTokenType.token ('name')
      WHITE_SPACE (' ')
      BAD_CHARACTER ('`')
      BAD_CHARACTER ('`')
      WHITE_SPACE (' ')
      BAD_CHARACTER ('_')
      WHITE_SPACE (' ')
      BAD_CHARACTER ('_')
      BAD_CHARACTER ('_')
      WHITE_SPACE (' ')
      BAD_CHARACTER ('_')
      BAD_CHARACTER ('_')
      BAD_CHARACTER ('_')
      BAD_CHARACTER ('_')
      BAD_CHARACTER ('_')
      """.trimIndent())
  }

  fun testMultilineString() {
    val quotes = "\"\"\""
    doTest("""
      a=$quotes my string $quotes
      b=$quotes this is ""the"" "link" $quotes
      """.trimIndent(),
           """
        DeclarativeTokenType.token ('a')
        DeclarativeTokenType.= ('=')
        DeclarativeTokenType.multiline_string_literal ('$quotes my string $quotes')
        WHITE_SPACE ('\n')
        DeclarativeTokenType.token ('b')
        DeclarativeTokenType.= ('=')
        DeclarativeTokenType.multiline_string_literal ('$quotes this is ""the"" "link" $quotes')
       """.trimIndent())

    doTest(
      """
        $quotes
        my
        string
        $quotes
      """.trimIndent(),
      """
        DeclarativeTokenType.multiline_string_literal ('$quotes\nmy\nstring\n$quotes')
      """.trimIndent())
  }

  override fun createLexer() = DeclarativeParserDefinition().createLexer(null)
  override fun getDirPath() = ""

}