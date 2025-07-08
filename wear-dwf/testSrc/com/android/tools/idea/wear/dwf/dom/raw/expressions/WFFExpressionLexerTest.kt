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
package com.android.tools.idea.wear.dwf.dom.raw.expressions

import com.android.tools.idea.lang.AndroidLexerTestCase
import com.intellij.psi.TokenType

class WFFExpressionLexerTest : AndroidLexerTestCase(WFFExpressionLexer()) {
  fun testSimpleExpression() {
    assertTokenTypes(
      "1 + 2",
      "1" to WFFExpressionTypes.INTEGER,
      " " to TokenType.WHITE_SPACE,
      "+" to WFFExpressionTypes.OPERATORS,
      " " to TokenType.WHITE_SPACE,
      "2" to WFFExpressionTypes.INTEGER,
    )
  }

  fun testParentheses() {
    assertTokenTypes(
      "(1 + 2)",
      "(" to WFFExpressionTypes.OPEN_PAREN,
      "1" to WFFExpressionTypes.INTEGER,
      " " to TokenType.WHITE_SPACE,
      "+" to WFFExpressionTypes.OPERATORS,
      " " to TokenType.WHITE_SPACE,
      "2" to WFFExpressionTypes.INTEGER,
      ")" to WFFExpressionTypes.CLOSE_PAREN,
    )
  }

  fun testDataSource() {
    assertTokenTypes(
      "[SECONDS_IN_DAY]",
      "[" to WFFExpressionTypes.OPEN_BRACKET,
      "SECONDS_IN_DAY" to WFFExpressionTypes.ID,
      "]" to WFFExpressionTypes.CLOSE_BRACKET,
    )
  }

  fun testConfiguration() {
    assertTokenTypes(
      "[CONFIGURATION.showBackgroundInAfternoon]",
      "[" to WFFExpressionTypes.OPEN_BRACKET,
      "CONFIGURATION" to WFFExpressionTypes.ID,
      "." to WFFExpressionTypes.DOT,
      "showBackgroundInAfternoon" to WFFExpressionTypes.ID,
      "]" to WFFExpressionTypes.CLOSE_BRACKET,
    )

    assertTokenTypes(
      "[CONFIGURATION.themeColor.1]",
      "[" to WFFExpressionTypes.OPEN_BRACKET,
      "CONFIGURATION" to WFFExpressionTypes.ID,
      "." to WFFExpressionTypes.DOT,
      "themeColor" to WFFExpressionTypes.ID,
      "." to WFFExpressionTypes.DOT,
      "1" to WFFExpressionTypes.INTEGER,
      "]" to WFFExpressionTypes.CLOSE_BRACKET,
    )
  }

  fun testFunctionCall() {
    assertTokenTypes(
      "log10(10, 2, 3)",
      "log10" to WFFExpressionTypes.ID,
      "(" to WFFExpressionTypes.OPEN_PAREN,
      "10" to WFFExpressionTypes.INTEGER,
      "," to WFFExpressionTypes.COMMA,
      " " to TokenType.WHITE_SPACE,
      "2" to WFFExpressionTypes.INTEGER,
      "," to WFFExpressionTypes.COMMA,
      " " to TokenType.WHITE_SPACE,
      "3" to WFFExpressionTypes.INTEGER,
      ")" to WFFExpressionTypes.CLOSE_PAREN,
    )
  }

  fun testStringLiteral() {
    assertTokenTypes("\"hello world\"", "\"hello world\"" to WFFExpressionTypes.QUOTED_STRING)
  }

  fun testId() {
    assertTokenTypes("myVariable", "myVariable" to WFFExpressionTypes.ID)
  }

  fun testNull() {
    assertTokenTypes("null", "null" to WFFExpressionTypes.NULL)
  }

  fun testComplexExpression() {
    assertTokenTypes(
      "[CONFIGURATION.showBackgroundInAfternoon] == \"TRUE\" && [SECONDS_IN_DAY] < (log10(10, 2, 3) * 50.0)",
      "[" to WFFExpressionTypes.OPEN_BRACKET,
      "CONFIGURATION" to WFFExpressionTypes.ID,
      "." to WFFExpressionTypes.DOT,
      "showBackgroundInAfternoon" to WFFExpressionTypes.ID,
      "]" to WFFExpressionTypes.CLOSE_BRACKET,
      " " to TokenType.WHITE_SPACE,
      "==" to WFFExpressionTypes.OPERATORS,
      " " to TokenType.WHITE_SPACE,
      "\"TRUE\"" to WFFExpressionTypes.QUOTED_STRING,
      " " to TokenType.WHITE_SPACE,
      "&&" to WFFExpressionTypes.OPERATORS,
      " " to TokenType.WHITE_SPACE,
      "[" to WFFExpressionTypes.OPEN_BRACKET,
      "SECONDS_IN_DAY" to WFFExpressionTypes.ID,
      "]" to WFFExpressionTypes.CLOSE_BRACKET,
      " " to TokenType.WHITE_SPACE,
      "<" to WFFExpressionTypes.OPERATORS,
      " " to TokenType.WHITE_SPACE,
      "(" to WFFExpressionTypes.OPEN_PAREN,
      "log10" to WFFExpressionTypes.ID,
      "(" to WFFExpressionTypes.OPEN_PAREN,
      "10" to WFFExpressionTypes.INTEGER,
      "," to WFFExpressionTypes.COMMA,
      " " to TokenType.WHITE_SPACE,
      "2" to WFFExpressionTypes.INTEGER,
      "," to WFFExpressionTypes.COMMA,
      " " to TokenType.WHITE_SPACE,
      "3" to WFFExpressionTypes.INTEGER,
      ")" to WFFExpressionTypes.CLOSE_PAREN,
      " " to TokenType.WHITE_SPACE,
      "*" to WFFExpressionTypes.OPERATORS,
      " " to TokenType.WHITE_SPACE,
      "50" to WFFExpressionTypes.INTEGER,
      "." to WFFExpressionTypes.DOT,
      "0" to WFFExpressionTypes.INTEGER,
      ")" to WFFExpressionTypes.CLOSE_PAREN,
    )
  }

  fun testWeather() {
    assertTokenTypes(
      "[WEATHER.IS_AVAILABLE]",
      "[" to WFFExpressionTypes.OPEN_BRACKET,
      "WEATHER" to WFFExpressionTypes.ID,
      "." to WFFExpressionTypes.DOT,
      "IS_AVAILABLE" to WFFExpressionTypes.ID,
      "]" to WFFExpressionTypes.CLOSE_BRACKET,
    )
    assertTokenTypes(
      "[WEATHER.HOURS.0.CONDITION]",
      "[" to WFFExpressionTypes.OPEN_BRACKET,
      "WEATHER" to WFFExpressionTypes.ID,
      "." to WFFExpressionTypes.DOT,
      "HOURS" to WFFExpressionTypes.ID,
      "." to WFFExpressionTypes.DOT,
      "0" to WFFExpressionTypes.INTEGER,
      "." to WFFExpressionTypes.DOT,
      "CONDITION" to WFFExpressionTypes.ID,
      "]" to WFFExpressionTypes.CLOSE_BRACKET,
    )
  }
}
