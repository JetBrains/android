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

import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.gradle.something.SomethingParserDefinition
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ParsingTestCase

class SomethingParserTest : ParsingTestCase("no_data_path_needed", "something", SomethingParserDefinition()) {
  fun testAssignment() {
    assertThat(
        """
          foo = 3
        """.toParseTreeText())
      .isEqualTo(
        """
          FILE
            SomethingAssignmentImpl(ASSIGNMENT)
              SomethingIdentifierImpl(IDENTIFIER)
                PsiElement(SomethingTokenType.token)('foo')
              PsiElement(SomethingTokenType.=)('=')
              SomethingLiteralImpl(LITERAL)
                PsiElement(SomethingTokenType.number)('3')
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
            SomethingBlockImpl(BLOCK)
              SomethingIdentifierImpl(IDENTIFIER)
                PsiElement(SomethingTokenType.token)('dependencies')
              PsiElement(SomethingTokenType.{)('{')
              PsiElement(SomethingTokenType.})('}')
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
            SomethingFactoryImpl(FACTORY)
              SomethingIdentifierImpl(IDENTIFIER)
                PsiElement(SomethingTokenType.token)('foo')
              PsiElement(SomethingTokenType.()('(')
              SomethingArgumentsListImpl(ARGUMENTS_LIST)
                SomethingLiteralImpl(LITERAL)
                  PsiElement(SomethingTokenType.string)('"abc/def"')
              PsiElement(SomethingTokenType.))(')')
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
            SomethingBlockImpl(BLOCK)
              SomethingFactoryImpl(FACTORY)
                SomethingIdentifierImpl(IDENTIFIER)
                  PsiElement(SomethingTokenType.token)('create')
                PsiElement(SomethingTokenType.()('(')
                SomethingArgumentsListImpl(ARGUMENTS_LIST)
                  SomethingLiteralImpl(LITERAL)
                    PsiElement(SomethingTokenType.string)('"foo"')
                PsiElement(SomethingTokenType.))(')')
              PsiElement(SomethingTokenType.{)('{')
              PsiElement(SomethingTokenType.})('}')
        """.trimIndent()
      )
  }

  private fun String.toParseTreeText() = createPsiFile("in-memory", this.trimIndent()).let {
    toParseTreeText(it, true, false).trim()
  }

  override fun getTestDataPath(): String = resolveWorkspacePath("tools/adt/idea/gradle-dsl/testData").toString()
}
