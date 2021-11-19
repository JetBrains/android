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
import com.android.tools.idea.logcat.filters.parser.LogcatFilterLexer.STRING_KVALUE_STATE
import com.android.tools.idea.logcat.filters.parser.LogcatFilterLexer.YYINITIAL
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.KEY
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.KVALUE
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes.VALUE
import com.google.common.truth.Truth.assertThat
import org.junit.Test

private val STRING_KEYS = listOf(
  "app",
  "line",
  "message",
  "msg",
  "package",
  "tag",
)
private val NON_STRING_KEYS = listOf(
  "age",
  "fromLevel",
  "level",
  "toLevel",
)

/**
 * Tests for [LogcatFilterLexerWrapper]
 */
class LogcatFilterLexerWrapperTest {

  @Test
  fun stringKeys() {
    for (key in STRING_KEYS) {
      assertThat(TestLexer.parse("$key:foo")).named(key).containsExactly(
        TokenInfo(KEY, "$key:", STRING_KVALUE_STATE),
        TokenInfo(KVALUE, "foo"),
      ).inOrder()
    }
  }

  @Test
  fun keys() {
    for (key in NON_STRING_KEYS) {
      assertThat(TestLexer.parse("$key:foo")).named(key).containsExactly(
        TokenInfo(KEY, "$key:", KVALUE_STATE),
        TokenInfo(KVALUE, "foo"),
      ).inOrder()
    }
  }

  @Test
  fun stringKeys_negated() {
    for (key in STRING_KEYS) {
      assertThat(TestLexer.parse("-$key:foo")).named(key).containsExactly(
        TokenInfo(KEY, "-$key:", STRING_KVALUE_STATE),
        TokenInfo(KVALUE, "foo"),
      ).inOrder()
    }
  }

  @Test
  fun stringKeys_regex() {
    for (key in STRING_KEYS) {
      assertThat(TestLexer.parse("$key~:foo")).named(key).containsExactly(
        TokenInfo(KEY, "$key~:", STRING_KVALUE_STATE),
        TokenInfo(KVALUE, "foo"),
      ).inOrder()
    }
  }

  @Test
  fun stringKeys_negatedRegex() {
    for (key in STRING_KEYS) {
      assertThat(TestLexer.parse("-$key~:foo")).named(key).containsExactly(
        TokenInfo(KEY, "-$key~:", STRING_KVALUE_STATE),
        TokenInfo(KVALUE, "foo"),
      ).inOrder()
    }
  }

  @Test
  fun nonStringKeys_negated() {
    for (key in NON_STRING_KEYS) {
      val text = "-$key:foo"
      assertThat(TestLexer.parse(text)).named(key).containsExactly(TokenInfo(VALUE, text))
    }
  }

  @Test
  fun nonStringKeys_regex() {
    for (key in NON_STRING_KEYS) {
      val text = "$key~:foo"
      assertThat(TestLexer.parse(text)).named(key).containsExactly(TokenInfo(VALUE, text))
    }
  }

  @Test
  fun nonStringKeys_negatedRegex() {
    for (key in NON_STRING_KEYS) {
      val text = "-$key~:foo"
      assertThat(TestLexer.parse(text)).named(key).containsExactly(TokenInfo(VALUE, text))
    }
  }

  @Test
  fun notKeyValue() {
    assertThat(TestLexer.parse("foo:bar")).containsExactly(TokenInfo(VALUE, "foo:bar"))
  }

  @Test
  fun hiddenKeyValuePairStates_stringKValue() {
    val text = "tag:bar"
    val lexer = LogcatFilterLexerWrapper()
    lexer.reset(text, 0, text.length, YYINITIAL)

    assertThat(lexer.advance()).isEqualTo(KEY)
    assertThat(lexer.yystate()).isEqualTo(STRING_KVALUE_STATE)
    assertThat(lexer.advance()).isEqualTo(KVALUE)
    assertThat(lexer.yystate()).isEqualTo(YYINITIAL)
  }

  @Test
  fun hiddenKeyValuePairStates_kValue() {
    val text = "level:bar"
    val lexer = LogcatFilterLexerWrapper()
    lexer.reset(text, 0, text.length, YYINITIAL)

    assertThat(lexer.advance()).isEqualTo(KEY)
    assertThat(lexer.yystate()).isEqualTo(KVALUE_STATE)
    assertThat(lexer.advance()).isEqualTo(KVALUE)
    assertThat(lexer.yystate()).isEqualTo(YYINITIAL)
  }

  @Test
  fun validKeyInValue() {
    assertThat(TestLexer.parse("tag:tag:bar")).containsExactly(
      TokenInfo(KEY, "tag:", STRING_KVALUE_STATE),
      TokenInfo(KVALUE, "tag:bar"),
    )
  }
}
