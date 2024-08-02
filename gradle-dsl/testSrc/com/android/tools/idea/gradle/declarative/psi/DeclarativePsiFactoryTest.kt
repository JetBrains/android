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
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.testFramework.LightPlatformTestCase

class DeclarativePsiFactoryTest : LightPlatformTestCase() {
  fun testCreateStringLiteral() {
    val literal = DeclarativePsiFactory(project).createStringLiteral("someLiteral")
    assertThat(literal).isNotNull()
    assertThat(literal.kind).isInstanceOf(DeclarativeLiteralKind.String::class.java)
    assertThat(literal.text).isEqualTo("\"someLiteral\"")
  }

  fun testCreateStringSingleEscapes() {
    val literal = DeclarativePsiFactory(project).createStringLiteral("a\tb\bc\nd\re\'f\"g\\h\$i")
    assertThat(literal).isNotNull()
    assertThat(literal.kind).isInstanceOf(DeclarativeLiteralKind.String::class.java)
    assertThat(literal.text).isEqualTo("\"a\\tb\\bc\\nd\\re\\'f\\\"g\\\\h\\\$i\"")
  }

  fun testCreateStringUnicodeEscapes() {
    val literal = DeclarativePsiFactory(project).createStringLiteral("\u201cHello, World!\u201d")
    assertThat(literal).isNotNull()
    assertThat(literal.kind).isInstanceOf(DeclarativeLiteralKind.String::class.java)
    assertThat(literal.text).isEqualTo("\"“Hello, World!”\"")
  }

  fun testCreateLiteralString() {
    val literal = DeclarativePsiFactory(project).createLiteral("someOtherLiteral")
    assertThat(literal).isNotNull()
    assertThat(literal.kind).isInstanceOf(DeclarativeLiteralKind.String::class.java)
    assertThat(literal.text).isEqualTo("\"someOtherLiteral\"")
  }

  fun testCreateIntegerLiteral() {
    val literal = DeclarativePsiFactory(project).createIntLiteral(101)
    assertThat(literal).isNotNull()
    assertThat(literal.kind).isInstanceOf(DeclarativeLiteralKind.Int::class.java)
    assertThat(literal.text).isEqualTo("101")
  }

  fun testCreateUIntegerLiteral() {
    val literal = DeclarativePsiFactory(project).createUIntLiteral(101U)
    assertThat(literal).isNotNull()
    assertThat(literal.kind).isInstanceOf(DeclarativeLiteralKind.UInt::class.java)
    assertThat(literal.text).isEqualTo("101U")
  }

  fun testCreateLiteralInteger() {
    val literal = DeclarativePsiFactory(project).createLiteral(102)
    assertThat(literal).isNotNull()
    assertThat(literal.kind).isInstanceOf(DeclarativeLiteralKind.Int::class.java)
    assertThat(literal.text).isEqualTo("102")
  }

  fun testCreateLongLiteral() {
    val literal = DeclarativePsiFactory(project).createLongLiteral(103L)
    assertThat(literal).isNotNull()
    assertThat(literal.kind).isInstanceOf(DeclarativeLiteralKind.Long::class.java)
    assertThat(literal.text).isEqualTo("103L")
  }

  fun testCreateLargeLongLiteral() {
    val literal = DeclarativePsiFactory(project).createLongLiteral(281474976710656L)
    assertThat(literal).isNotNull()
    assertThat(literal.kind).isInstanceOf(DeclarativeLiteralKind.Int::class.java)
    assertThat(literal.text).isEqualTo("281474976710656")
  }

  fun testCreateULongLiteral() {
    val literal = DeclarativePsiFactory(project).createULongLiteral(103UL)
    assertThat(literal).isNotNull()
    assertThat(literal.kind).isInstanceOf(DeclarativeLiteralKind.ULong::class.java)
    assertThat(literal.text).isEqualTo("103UL")
  }

  fun testCreateLiteralLong() {
    val literal = DeclarativePsiFactory(project).createLiteral(104L)
    assertThat(literal).isNotNull()
    assertThat(literal.kind).isInstanceOf(DeclarativeLiteralKind.Long::class.java)
    assertThat(literal.text).isEqualTo("104L")
  }

  fun testCreateLiteralLargeLong() {
    val literal = DeclarativePsiFactory(project).createLiteral(281474976710656L)
    assertThat(literal).isNotNull()
    assertThat(literal.kind).isInstanceOf(DeclarativeLiteralKind.Int::class.java)
    assertThat(literal.text).isEqualTo("281474976710656")
  }

  fun testCreateBooleanLiteral() {
    val literal = DeclarativePsiFactory(project).createBooleanLiteral(true)
    assertThat(literal).isNotNull()
    assertThat(literal.kind).isInstanceOf(DeclarativeLiteralKind.Boolean::class.java)
    assertThat(literal.text).isEqualTo("true")
  }

  fun testCreateLiteralBoolean() {
    val literal = DeclarativePsiFactory(project).createLiteral(false)
    assertThat(literal).isNotNull()
    assertThat(literal.kind).isInstanceOf(DeclarativeLiteralKind.Boolean::class.java)
    assertThat(literal.text).isEqualTo("false")
  }

  fun testCreateLiteralUnsupported() {
    val factory = DeclarativePsiFactory(project)
    for (obj in listOf(listOf(""), mapOf("a" to 1), Any(), null)) {
      assertThrows(IllegalStateException::class.java) { factory.createLiteral(obj) }
    }
  }

  fun testCreateStringLiteralFromText() {
    val literal = DeclarativePsiFactory(project).createLiteralFromText("\"\\u201bHello, World!\\u201c\"")
    assertThat(literal).isNotNull()
    assertThat(literal.kind).isInstanceOf(DeclarativeLiteralKind.String::class.java)
    assertThat(literal.text).isEqualTo("\"\\u201bHello, World!\\u201c\"")
  }

  fun testCreateIntegerLiteralFromText() {
    val literal = DeclarativePsiFactory(project).createLiteralFromText("42")
    assertThat(literal).isNotNull()
    assertThat(literal.kind).isInstanceOf(DeclarativeLiteralKind.Int::class.java)
    assertThat(literal.text).isEqualTo("42")
  }

  fun testCreateUnsignedIntegerLiteralFromText() {
    val literal = DeclarativePsiFactory(project).createLiteralFromText("4__2U")
    assertThat(literal).isNotNull()
    assertThat(literal.kind).isInstanceOf(DeclarativeLiteralKind.UInt::class.java)
    assertThat(literal.text).isEqualTo("4__2U")
  }

  fun testCreateLongLiteralFromText() {
    val literal = DeclarativePsiFactory(project).createLiteralFromText("4__2L")
    assertThat(literal).isNotNull()
    assertThat(literal.kind).isInstanceOf(DeclarativeLiteralKind.Long::class.java)
    assertThat(literal.text).isEqualTo("4__2L")
  }

  fun testCreateUnsignedLongLiteralFromText() {
    val literal = DeclarativePsiFactory(project).createLiteralFromText("4__2UL")
    assertThat(literal).isNotNull()
    assertThat(literal.kind).isInstanceOf(DeclarativeLiteralKind.ULong::class.java)
    assertThat(literal.text).isEqualTo("4__2UL")
  }

  fun testCreateBooleanLiteralFromText() {
    val literal = DeclarativePsiFactory(project).createLiteralFromText("true")
    assertThat(literal).isNotNull()
    assertThat(literal.kind).isInstanceOf(DeclarativeLiteralKind.Boolean::class.java)
    assertThat(literal.text).isEqualTo("true")
  }

  fun testCreateNewLine() {
    val psi = DeclarativePsiFactory(project).createNewline()
    assertThat(psi).isNotNull()
    assertThat(psi).isInstanceOf(LeafPsiElement::class.java)
    assertThat(psi.text).isEqualTo("\n")
  }

  fun testAssignment() {
    val assignment = DeclarativePsiFactory(project).createAssignment("key", "\"value\"")
    assertThat(assignment).isNotNull()
    assertThat(assignment).isInstanceOf(DeclarativeAssignment::class.java)
    assertThat(assignment.text).isEqualTo("key = \"value\"")
  }

  fun testBlock() {
    val block = DeclarativePsiFactory(project).createBlock("block")
    assertThat(block).isNotNull()
    assertThat(block).isInstanceOf(DeclarativeBlock::class.java)
    assertThat(block.text).isEqualTo("block {\n}")
  }

  fun testFactory() {
    val factory = DeclarativePsiFactory(project).createFactory("factory")
    assertThat(factory).isNotNull()
    assertThat(factory).isInstanceOf(DeclarativeFactory::class.java)
    assertThat(factory.text).isEqualTo("factory()")
  }
}