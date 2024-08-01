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
package com.android.tools.idea.gradle.declarative.parser

import com.android.tools.idea.gradle.declarative.DeclarativeParserDefinition
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
        DeclarativeTokenType.string ('"abc"')
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
        DeclarativeTokenType.string ('"abc"')
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
        DeclarativeTokenType.string ('"abc"')
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
        DeclarativeTokenType.string ('"abc"')
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
        DeclarativeTokenType.string ('"abc"')
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
      DeclarativeTokenType.string ('"abc"')
      WHITE_SPACE ('\n')
      DeclarativeTokenType.BLOCK_COMMENT ('/***/')
      WHITE_SPACE (' ')
      DeclarativeTokenType.string ('"def"')
      """.trimIndent()
    )
  }

  fun testNumber() {
    doTest(
      """
        1 23 456 7_8 9__10L
      """.trimIndent(),
      """
        DeclarativeTokenType.number ('1')
        WHITE_SPACE (' ')
        DeclarativeTokenType.number ('23')
        WHITE_SPACE (' ')
        DeclarativeTokenType.number ('456')
        WHITE_SPACE (' ')
        DeclarativeTokenType.number ('7_8')
        WHITE_SPACE (' ')
        DeclarativeTokenType.number ('9__10L')
      """.trimIndent()
    )
  }

  fun testString() {
    doTest(
      """
        "abc" "def"
      """.trimIndent(),
      """
        DeclarativeTokenType.string ('"abc"')
        WHITE_SPACE (' ')
        DeclarativeTokenType.string ('"def"')
      """.trimIndent()
    )
  }

  fun testComma() {
    doTest(
      """
        "foo", "bar"
      """.trimIndent(),
      """
        DeclarativeTokenType.string ('"foo"')
        DeclarativeTokenType., (',')
        WHITE_SPACE (' ')
        DeclarativeTokenType.string ('"bar"')
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

  fun testMultilineString() {
    val quotes = "\"\"\""
    doTest(
      "a=$quotes my string $quotes",
      """
        DeclarativeTokenType.token ('a')
        DeclarativeTokenType.= ('=')
        DeclarativeTokenType.multiline_string ('$quotes my string $quotes')
      """.trimIndent())

    doTest(
      """
        $quotes
        my
        string
        $quotes
      """.trimIndent(),
      """
        DeclarativeTokenType.multiline_string ('$quotes\nmy\nstring\n$quotes')
      """.trimIndent())
  }

  override fun createLexer() = DeclarativeParserDefinition().createLexer(null)
  override fun getDirPath() = ""

}