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
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.testFramework.LightPlatformTestCase

class SomethingPsiFactoryTest : LightPlatformTestCase() {
  fun testCreateStringLiteral() {
    val literal = SomethingPsiFactory(project).createStringLiteral("someLiteral")
    assertThat(literal).isNotNull()
    assertThat(literal.kind).isInstanceOf(SomethingLiteralKind.String::class.java)
    assertThat(literal.text).isEqualTo("\"someLiteral\"")
  }

  fun testCreateStringSingleEscapes() {
    val literal = SomethingPsiFactory(project).createStringLiteral("a\tb\bc\nd\re\'f\"g\\h\$i")
    assertThat(literal).isNotNull()
    assertThat(literal.kind).isInstanceOf(SomethingLiteralKind.String::class.java)
    assertThat(literal.text).isEqualTo("\"a\\tb\\bc\\nd\\re\\'f\\\"g\\\\h\\\$i\"")
  }

  fun testCreateStringUnicodeEscapes() {
    val literal = SomethingPsiFactory(project).createStringLiteral("\u201cHello, World!\u201d")
    assertThat(literal).isNotNull()
    assertThat(literal.kind).isInstanceOf(SomethingLiteralKind.String::class.java)
    assertThat(literal.text).isEqualTo("\"“Hello, World!”\"")
  }

  fun testCreateLiteralString() {
    val literal = SomethingPsiFactory(project).createLiteral("someOtherLiteral")
    assertThat(literal).isNotNull()
    assertThat(literal.kind).isInstanceOf(SomethingLiteralKind.String::class.java)
    assertThat(literal.text).isEqualTo("\"someOtherLiteral\"")
  }

  fun testCreateIntegerLiteral() {
    val literal = SomethingPsiFactory(project).createIntLiteral(101)
    assertThat(literal).isNotNull()
    assertThat(literal.kind).isInstanceOf(SomethingLiteralKind.Number::class.java)
    assertThat(literal.text).isEqualTo("101")
  }

  fun testCreateLiteralInteger() {
    val literal = SomethingPsiFactory(project).createLiteral(102)
    assertThat(literal).isNotNull()
    assertThat(literal.kind).isInstanceOf(SomethingLiteralKind.Number::class.java)
    assertThat(literal.text).isEqualTo("102")
  }

  fun testCreateLongLiteral() {
    val literal = SomethingPsiFactory(project).createLongLiteral(103L)
    assertThat(literal).isNotNull()
    assertThat(literal.kind).isInstanceOf(SomethingLiteralKind.Number::class.java)
    assertThat(literal.text).isEqualTo("103L")
  }

  fun testCreateLargeLongLiteral() {
    val literal = SomethingPsiFactory(project).createLongLiteral(281474976710656L)
    assertThat(literal).isNotNull()
    assertThat(literal.kind).isInstanceOf(SomethingLiteralKind.Number::class.java)
    assertThat(literal.text).isEqualTo("281474976710656")
  }

  fun testCreateLiteralLong() {
    val literal = SomethingPsiFactory(project).createLiteral(104L)
    assertThat(literal).isNotNull()
    assertThat(literal.kind).isInstanceOf(SomethingLiteralKind.Number::class.java)
    assertThat(literal.text).isEqualTo("104L")
  }

  fun testCreateLiteralLargeLong() {
    val literal = SomethingPsiFactory(project).createLiteral(281474976710656L)
    assertThat(literal).isNotNull()
    assertThat(literal.kind).isInstanceOf(SomethingLiteralKind.Number::class.java)
    assertThat(literal.text).isEqualTo("281474976710656")
  }

  fun testCreateBooleanLiteral() {
    val literal = SomethingPsiFactory(project).createBooleanLiteral(true)
    assertThat(literal).isNotNull()
    assertThat(literal.kind).isInstanceOf(SomethingLiteralKind.Boolean::class.java)
    assertThat(literal.text).isEqualTo("true")
  }

  fun testCreateLiteralBoolean() {
    val literal = SomethingPsiFactory(project).createLiteral(false)
    assertThat(literal).isNotNull()
    assertThat(literal.kind).isInstanceOf(SomethingLiteralKind.Boolean::class.java)
    assertThat(literal.text).isEqualTo("false")
  }

  fun testCreateLiteralUnsupported() {
    val factory = SomethingPsiFactory(project)
    for (obj in listOf(listOf(""), mapOf("a" to 1), Any(), null)) {
      assertThrows(IllegalStateException::class.java) { factory.createLiteral(obj) }
    }
  }

  fun testCreateStringLiteralFromText() {
    val literal = SomethingPsiFactory(project).createLiteralFromText("\"\\u201bHello, World!\\u201c\"")
    assertThat(literal).isNotNull()
    assertThat(literal.kind).isInstanceOf(SomethingLiteralKind.String::class.java)
    assertThat(literal.text).isEqualTo("\"\\u201bHello, World!\\u201c\"")
  }

  fun testCreateIntegerLiteralFromText() {
    val literal = SomethingPsiFactory(project).createLiteralFromText("42")
    assertThat(literal).isNotNull()
    assertThat(literal.kind).isInstanceOf(SomethingLiteralKind.Number::class.java)
    assertThat(literal.text).isEqualTo("42")
  }

  fun testCreateLongLiteralFromText() {
    val literal = SomethingPsiFactory(project).createLiteralFromText("4__2L")
    assertThat(literal).isNotNull()
    assertThat(literal.kind).isInstanceOf(SomethingLiteralKind.Number::class.java)
    assertThat(literal.text).isEqualTo("4__2L")
  }

  fun testCreateBooleanLiteralFromText() {
    val literal = SomethingPsiFactory(project).createLiteralFromText("true")
    assertThat(literal).isNotNull()
    assertThat(literal.kind).isInstanceOf(SomethingLiteralKind.Boolean::class.java)
    assertThat(literal.text).isEqualTo("true")
  }

  fun testCreateNewLine() {
    val psi = SomethingPsiFactory(project).createNewline()
    assertThat(psi).isNotNull()
    assertThat(psi).isInstanceOf(LeafPsiElement::class.java)
    assertThat(psi.text).isEqualTo("\n")
  }

  fun testAssignment() {
    val assignment = SomethingPsiFactory(project).createAssignment("key", "\"value\"")
    assertThat(assignment).isNotNull()
    assertThat(assignment).isInstanceOf(SomethingAssignment::class.java)
    assertThat(assignment.text).isEqualTo("key = \"value\"")
  }

  fun testBlock() {
    val block = SomethingPsiFactory(project).createBlock("block")
    assertThat(block).isNotNull()
    assertThat(block).isInstanceOf(SomethingBlock::class.java)
    assertThat(block.text).isEqualTo("block {\n}")
  }

  fun testFactory() {
    val factory = SomethingPsiFactory(project).createFactory("factory")
    assertThat(factory).isNotNull()
    assertThat(factory).isInstanceOf(SomethingFactory::class.java)
    assertThat(factory.text).isEqualTo("factory()")
  }
}