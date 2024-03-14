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
package com.android.tools.idea.gradle.something.parser

import com.android.tools.idea.gradle.something.SomethingParserDefinition
import com.intellij.testFramework.LexerTestCase

class SomethingLexerTest : LexerTestCase() {
  fun testLineComment() {
    doTest(
      """
        // abc
        
        // defg
      """.trimIndent(),
      """
        SomethingTokenType.line_comment ('// abc')
        WHITE_SPACE ('\n\n')
        SomethingTokenType.line_comment ('// defg')
      """.trimIndent()
    )
  }

  fun testNumber() {
    doTest(
      """
        1 23 456
      """.trimIndent(),
      """
        SomethingTokenType.number ('1')
        WHITE_SPACE (' ')
        SomethingTokenType.number ('23')
        WHITE_SPACE (' ')
        SomethingTokenType.number ('456')
      """.trimIndent()
    )
  }

  fun testString() {
    doTest(
      """
        "abc" "def"
      """.trimIndent(),
      """
        SomethingTokenType.string ('"abc"')
        WHITE_SPACE (' ')
        SomethingTokenType.string ('"def"')
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
        SomethingTokenType.boolean ('true')
        WHITE_SPACE ('\n')
        SomethingTokenType.boolean ('false')
      """.trimIndent()
    )
  }

  fun testNull() {
    doTest("null", "SomethingTokenType.null ('null')")
  }

  fun testToken() {
    doTest(
      """
        abc def.ghi
      """.trimIndent(),
      """
        SomethingTokenType.token ('abc')
        WHITE_SPACE (' ')
        SomethingTokenType.token ('def')
        SomethingTokenType.. ('.')
        SomethingTokenType.token ('ghi')
      """.trimIndent())
  }

  override fun createLexer() = SomethingParserDefinition().createLexer(null)
  override fun getDirPath() = ""

}