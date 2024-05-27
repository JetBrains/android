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

import com.android.test.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.gradle.declarative.DeclarativeParserDefinition
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ParsingTestCase

class DeclarativeParserTest : ParsingTestCase("no_data_path_needed", "dcl", DeclarativeParserDefinition()) {
  fun testAssignment() {
    assertThat(
        """
          foo = 3
        """.toParseTreeText())
      .isEqualTo(
        """
          FILE
            DeclarativeAssignmentImpl(ASSIGNMENT)
              DeclarativeIdentifierImpl(IDENTIFIER)
                PsiElement(DeclarativeTokenType.token)('foo')
              PsiElement(DeclarativeTokenType.=)('=')
              DeclarativeLiteralImpl(LITERAL)
                PsiElement(DeclarativeTokenType.number)('3')
        """.trimIndent()
    )
  }

  fun testBlock() {
    assertThat(
      """
        dependencies {
        }
      """.toParseTreeText()
    )
      .isEqualTo(
        """
          FILE
            DeclarativeBlockImpl(BLOCK)
              DeclarativeIdentifierImpl(IDENTIFIER)
                PsiElement(DeclarativeTokenType.token)('dependencies')
              DeclarativeBlockGroupImpl(BLOCK_GROUP)
                PsiElement(DeclarativeTokenType.{)('{')
                PsiElement(DeclarativeTokenType.})('}')
        """.trimIndent()
      )
  }

  fun testFactory() {
    assertThat(
      """
        foo("abc/def")
      """.toParseTreeText()
    )
      .isEqualTo(
        """
          FILE
            DeclarativeFactoryImpl(FACTORY)
              DeclarativeIdentifierImpl(IDENTIFIER)
                PsiElement(DeclarativeTokenType.token)('foo')
              PsiElement(DeclarativeTokenType.()('(')
              DeclarativeArgumentsListImpl(ARGUMENTS_LIST)
                DeclarativeLiteralImpl(LITERAL)
                  PsiElement(DeclarativeTokenType.string)('"abc/def"')
              PsiElement(DeclarativeTokenType.))(')')
        """.trimIndent()
      )
  }

  fun testFactoryBlock() {
    assertThat(
      """
        create("foo") {
        }
      """.toParseTreeText()
    )
      .isEqualTo(
        """
          FILE
            DeclarativeBlockImpl(BLOCK)
              DeclarativeFactoryImpl(FACTORY)
                DeclarativeIdentifierImpl(IDENTIFIER)
                  PsiElement(DeclarativeTokenType.token)('create')
                PsiElement(DeclarativeTokenType.()('(')
                DeclarativeArgumentsListImpl(ARGUMENTS_LIST)
                  DeclarativeLiteralImpl(LITERAL)
                    PsiElement(DeclarativeTokenType.string)('"foo"')
                PsiElement(DeclarativeTokenType.))(')')
              DeclarativeBlockGroupImpl(BLOCK_GROUP)
                PsiElement(DeclarativeTokenType.{)('{')
                PsiElement(DeclarativeTokenType.})('}')
        """.trimIndent()
      )
  }

  fun testStringHasOnlyOneLine() {
    assertThat(
      """
          foo = "some\nstring
          bar = 3
        """.toParseTreeText())
      .isEqualTo(
        """
          FILE
            DeclarativeAssignmentImpl(ASSIGNMENT)
              DeclarativeIdentifierImpl(IDENTIFIER)
                PsiElement(DeclarativeTokenType.token)('foo')
              PsiElement(DeclarativeTokenType.=)('=')
              DeclarativeLiteralImpl(LITERAL)
                PsiElement(DeclarativeTokenType.string)('"some\nstring')
            DeclarativeAssignmentImpl(ASSIGNMENT)
              DeclarativeIdentifierImpl(IDENTIFIER)
                PsiElement(DeclarativeTokenType.token)('bar')
              PsiElement(DeclarativeTokenType.=)('=')
              DeclarativeLiteralImpl(LITERAL)
                PsiElement(DeclarativeTokenType.number)('3')
          """.trimIndent()
      )
  }

  fun testStringHandleEscapeQuotes() {
    assertThat(
      """
          foo = "some\"string\""
        """.toParseTreeText())
      .isEqualTo(
        """
          FILE
            DeclarativeAssignmentImpl(ASSIGNMENT)
              DeclarativeIdentifierImpl(IDENTIFIER)
                PsiElement(DeclarativeTokenType.token)('foo')
              PsiElement(DeclarativeTokenType.=)('=')
              DeclarativeLiteralImpl(LITERAL)
                PsiElement(DeclarativeTokenType.string)('"some\"string\""')
          """.trimIndent()
      )
  }

  fun testZeroArgumentFactory() {
    assertThat(
      """
        foo()
      """.toParseTreeText()
    )
      .isEqualTo(
        """
          FILE
            DeclarativeFactoryImpl(FACTORY)
              DeclarativeIdentifierImpl(IDENTIFIER)
                PsiElement(DeclarativeTokenType.token)('foo')
              PsiElement(DeclarativeTokenType.()('(')
              DeclarativeArgumentsListImpl(ARGUMENTS_LIST)
                <empty list>
              PsiElement(DeclarativeTokenType.))(')')
        """.trimIndent()
      )
  }

  fun testMultiArgumentFactory() {
    assertThat(
      """
        foo("hello, world!", "foo",123,456, true,    bar())
      """.toParseTreeText()
    )
      .isEqualTo(
        """
          FILE
            DeclarativeFactoryImpl(FACTORY)
              DeclarativeIdentifierImpl(IDENTIFIER)
                PsiElement(DeclarativeTokenType.token)('foo')
              PsiElement(DeclarativeTokenType.()('(')
              DeclarativeArgumentsListImpl(ARGUMENTS_LIST)
                DeclarativeLiteralImpl(LITERAL)
                  PsiElement(DeclarativeTokenType.string)('"hello, world!"')
                PsiElement(DeclarativeTokenType.,)(',')
                DeclarativeLiteralImpl(LITERAL)
                  PsiElement(DeclarativeTokenType.string)('"foo"')
                PsiElement(DeclarativeTokenType.,)(',')
                DeclarativeLiteralImpl(LITERAL)
                  PsiElement(DeclarativeTokenType.number)('123')
                PsiElement(DeclarativeTokenType.,)(',')
                DeclarativeLiteralImpl(LITERAL)
                  PsiElement(DeclarativeTokenType.number)('456')
                PsiElement(DeclarativeTokenType.,)(',')
                DeclarativeLiteralImpl(LITERAL)
                  PsiElement(DeclarativeTokenType.boolean)('true')
                PsiElement(DeclarativeTokenType.,)(',')
                DeclarativeFactoryImpl(FACTORY)
                  DeclarativeIdentifierImpl(IDENTIFIER)
                    PsiElement(DeclarativeTokenType.token)('bar')
                  PsiElement(DeclarativeTokenType.()('(')
                  DeclarativeArgumentsListImpl(ARGUMENTS_LIST)
                    <empty list>
                  PsiElement(DeclarativeTokenType.))(')')
              PsiElement(DeclarativeTokenType.))(')')
        """.trimIndent()
      )
  }

  private fun String.toParseTreeText() = createPsiFile("in-memory", this.trimIndent()).let {
    toParseTreeText(it, true, false).trim()
  }

  override fun getTestDataPath(): String = resolveWorkspacePath("tools/adt/idea/gradle-dsl/testData").toString()
}
