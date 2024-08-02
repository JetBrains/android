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
package com.android.tools.idea.gradle.declarative.psi

import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.LightPlatformTestCase

class DeclarativeLiteralTest : LightPlatformTestCase() {
  fun testLiteralStringValue() {
    val literal = DeclarativePsiFactory(project).createLiteral("abc")
    assertThat(literal.value).isEqualTo("abc")
  }

  fun testLiteralStringValueEmbeddedQuote() {
    val literal = DeclarativePsiFactory(project).createLiteral("ab\"c")
    assertThat(literal.value).isEqualTo("ab\"c")
  }

  fun testLiteralStringValueStartingQuote() {
    val literal = DeclarativePsiFactory(project).createLiteral("\"abc")
    assertThat(literal.value).isEqualTo("\"abc")
  }

  fun testLiteralStringValueEndingQuote() {
    val literal = DeclarativePsiFactory(project).createLiteral("abc\"")
    assertThat(literal.value).isEqualTo("abc\"")
  }

  fun testLiteralStringValueEnclosingQuotes() {
    val literal = DeclarativePsiFactory(project).createLiteral("\"abc\"")
    assertThat(literal.value).isEqualTo("\"abc\"")
  }

  fun testLiteralStringValueSingleEscapes() {
    val literal = DeclarativePsiFactory(project).createLiteral("a\tb\bc\nd\re\'f\"g\\h\$i")
    assertThat(literal.value).isEqualTo("a\tb\bc\nd\re\'f\"g\\h\$i")
  }

  fun testLiteralStringValueUnicodeEscapes() {
    val literal = DeclarativePsiFactory(project).createLiteral("a\u0020b")
    assertThat(literal.value).isEqualTo("a b")
  }

  fun testLiteralStringValueLiteralUnicodeEscapes() {
    val literal = DeclarativePsiFactory(project).createLiteralFromText("\"a\\u0020b\"")
    assertThat(literal.text).isEqualTo("\"a\\u0020b\"")
    assertThat(literal.value).isEqualTo("a b")
  }

  fun testLiteralIntegerValue() {
    val literal = DeclarativePsiFactory(project).createLiteral(42)
    assertThat(literal.value).isInstanceOf(java.lang.Integer::class.java)
    assertThat(literal.value).isEqualTo(42)
  }

  fun testLiteralLongValue() {
    val literal = DeclarativePsiFactory(project).createLiteral(42L)
    assertThat(literal.value).isInstanceOf(java.lang.Long::class.java)
    assertThat(literal.value).isEqualTo(42)
  }

  fun testLiteralUIntegerValue() {
    val literal = DeclarativePsiFactory(project).createLiteral(42U)
    assertThat(literal.value).isInstanceOf(UInt::class.java)
    assertThat(literal.value).isEqualTo(42U)
  }

  fun testLiteralULongValue() {
    val literal = DeclarativePsiFactory(project).createLiteral(42UL)
    assertThat(literal.value).isInstanceOf(ULong::class.java)
    assertThat(literal.value).isEqualTo(42UL)
  }

  fun testLiteralHexValue() {
    val literal = DeclarativePsiFactory(project).createLiteral(0xFF)
    assertThat(literal.value).isInstanceOf(java.lang.Integer::class.java)
    assertThat(literal.value).isEqualTo(0xFF)
  }

  fun testLiteralBinValue() {
    val literal = DeclarativePsiFactory(project).createLiteral(0x0111)
    assertThat(literal.value).isInstanceOf(java.lang.Integer::class.java)
    assertThat(literal.value).isEqualTo(0x0111)
  }

  fun testLiteralLargeLongValue() {
    val literal = DeclarativePsiFactory(project).createLiteral(281474976710656)
    assertThat(literal.value).isInstanceOf(java.lang.Long::class.java)
    assertThat(literal.value).isEqualTo(281474976710656)
  }

  fun testLiteralLargeLongValueWithUnderscores() {
    val literal = DeclarativePsiFactory(project).createLiteralFromText("281_474_976_710_656")
    assertThat(literal.value).isInstanceOf(java.lang.Long::class.java)
    assertThat(literal.value).isEqualTo(281474976710656)
  }

  fun testLiteralBooleanValue() {
    val literal = DeclarativePsiFactory(project).createLiteral(true)
    assertThat(literal.value).isEqualTo(true)
  }
}