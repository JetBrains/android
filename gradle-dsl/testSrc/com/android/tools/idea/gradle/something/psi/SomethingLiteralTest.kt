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
package com.android.tools.idea.gradle.something.psi

import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.LightPlatformTestCase

class SomethingLiteralTest : LightPlatformTestCase() {
  fun testLiteralStringValue() {
    val literal = SomethingPsiFactory(project).createLiteral("abc")
    assertThat(literal.value).isEqualTo("abc")
  }

  fun testLiteralStringValueEmbeddedQuote() {
    val literal = SomethingPsiFactory(project).createLiteral("ab\"c")
    assertThat(literal.value).isEqualTo("ab\"c")
  }

  fun testLiteralStringValueStartingQuote() {
    val literal = SomethingPsiFactory(project).createLiteral("\"abc")
    assertThat(literal.value).isEqualTo("\"abc")
  }

  fun testLiteralStringValueEndingQuote() {
    val literal = SomethingPsiFactory(project).createLiteral("abc\"")
    assertThat(literal.value).isEqualTo("abc\"")
  }

  fun testLiteralStringValueEnclosingQuotes() {
    val literal = SomethingPsiFactory(project).createLiteral("\"abc\"")
    assertThat(literal.value).isEqualTo("\"abc\"")
  }

  fun testLiteralStringValueSingleEscapes() {
    val literal = SomethingPsiFactory(project).createLiteral("a\tb\bc\nd\re\'f\"g\\h\$i")
    assertThat(literal.value).isEqualTo("a\tb\bc\nd\re\'f\"g\\h\$i")
  }

  fun testLiteralStringValueUnicodeEscapes() {
    val literal = SomethingPsiFactory(project).createLiteral("a\u0020b")
    assertThat(literal.value).isEqualTo("a b")
  }

  fun testLiteralStringValueLiteralUnicodeEscapes() {
    val literal = SomethingPsiFactory(project).createLiteralFromText("\"a\\u0020b\"")
    assertThat(literal.text).isEqualTo("\"a\\u0020b\"")
    assertThat(literal.value).isEqualTo("a b")
  }

  fun testLiteralIntegerValue() {
    val literal = SomethingPsiFactory(project).createLiteral(42)
    assertThat(literal.value).isEqualTo(42)
  }

  fun testLiteralBooleanValue() {
    val literal = SomethingPsiFactory(project).createLiteral(true)
    assertThat(literal.value).isEqualTo(true)
  }
}