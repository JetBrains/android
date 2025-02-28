/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.intellij.testFramework.LexerTestCase

class DeclarativeEscapeLexerTest: LexerTestCase() {
  fun testValidUnicode() {
    doTest(
      "\"\\u0020\\uD7FFabcd\"",
      """
     DeclarativeTokenType.one_line_string_literal ('"')
     VALID_STRING_ESCAPE_TOKEN ('\u0020')
     VALID_STRING_ESCAPE_TOKEN ('\uD7FF')
     DeclarativeTokenType.one_line_string_literal ('abcd"')
      """.trimIndent()
    )
  }

  fun testInvalidUnicode() {
    doTest(
      "\"\\u002m\\uD7Fpppp\"",
      """
      DeclarativeTokenType.one_line_string_literal ('"')
      INVALID_UNICODE_ESCAPE_TOKEN ('\u002m')
      INVALID_UNICODE_ESCAPE_TOKEN ('\uD7Fp')
      DeclarativeTokenType.one_line_string_literal ('ppp"')
      """.trimIndent()
    )
  }

  fun testValidSymbols(){
    doTest(
      "\"\\b\\n\\r\\t\\\"\\\\\"",
      """
      DeclarativeTokenType.one_line_string_literal ('"')
      VALID_STRING_ESCAPE_TOKEN ('\b')
      VALID_STRING_ESCAPE_TOKEN ('\n')
      VALID_STRING_ESCAPE_TOKEN ('\r')
      VALID_STRING_ESCAPE_TOKEN ('\t')
      VALID_STRING_ESCAPE_TOKEN ('\"')
      VALID_STRING_ESCAPE_TOKEN ('\\')
      DeclarativeTokenType.one_line_string_literal ('"')
        """.trimIndent()
    )
  }
  override fun createLexer() = DeclarativeHighlightingLexer()
  override fun getDirPath() = ""
}