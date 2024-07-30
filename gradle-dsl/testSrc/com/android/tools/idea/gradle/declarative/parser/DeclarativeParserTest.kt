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

import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.gradle.declarative.DeclarativeParserDefinition
import com.android.tools.idea.gradle.declarative.psi.DeclarativeASTFactory
import com.google.common.truth.Truth.assertThat
import com.intellij.lang.LanguageASTFactory
import com.intellij.testFramework.ParsingTestCase

class DeclarativeParserTest : ParsingTestCase("no_data_path_needed", "dcl", DeclarativeParserDefinition()) {

  override fun setUp() {
    super.setUp()
    addExplicitExtension(LanguageASTFactory.INSTANCE, myLanguage, DeclarativeASTFactory())
  }

  fun testAssignment() {
    assertThat(
      """
          foo = 3
        """.toParseTreeText())
      .isEqualTo(
        """
          FILE
            PsiElement(ASSIGNMENT)
              PsiElement(IDENTIFIER)
                PsiElement(DeclarativeTokenType.token)('foo')
              PsiElement(DeclarativeTokenType.=)('=')
              PsiElement(LITERAL)
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
            PsiElement(BLOCK)
              PsiElement(IDENTIFIER)
                PsiElement(DeclarativeTokenType.token)('dependencies')
              PsiElement(BLOCK_GROUP)
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
            PsiElement(FACTORY)
              PsiElement(IDENTIFIER)
                PsiElement(DeclarativeTokenType.token)('foo')
              PsiElement(DeclarativeTokenType.()('(')
              PsiElement(ARGUMENTS_LIST)
                PsiElement(LITERAL)
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
            PsiElement(BLOCK)
              PsiElement(FACTORY)
                PsiElement(IDENTIFIER)
                  PsiElement(DeclarativeTokenType.token)('create')
                PsiElement(DeclarativeTokenType.()('(')
                PsiElement(ARGUMENTS_LIST)
                  PsiElement(LITERAL)
                    PsiElement(DeclarativeTokenType.string)('"foo"')
                PsiElement(DeclarativeTokenType.))(')')
              PsiElement(BLOCK_GROUP)
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
            PsiElement(ASSIGNMENT)
              PsiElement(IDENTIFIER)
                PsiElement(DeclarativeTokenType.token)('foo')
              PsiElement(DeclarativeTokenType.=)('=')
              PsiElement(LITERAL)
                PsiElement(DeclarativeTokenType.string)('"some\nstring')
            PsiElement(ASSIGNMENT)
              PsiElement(IDENTIFIER)
                PsiElement(DeclarativeTokenType.token)('bar')
              PsiElement(DeclarativeTokenType.=)('=')
              PsiElement(LITERAL)
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
            PsiElement(ASSIGNMENT)
              PsiElement(IDENTIFIER)
                PsiElement(DeclarativeTokenType.token)('foo')
              PsiElement(DeclarativeTokenType.=)('=')
              PsiElement(LITERAL)
                PsiElement(DeclarativeTokenType.string)('"some\"string\""')
          """.trimIndent()
      )
  }

  fun testMultiLineString() {
    val quotes = "\"\"\""
    assertThat(
      """
          foo = ${quotes}some string$quotes
        """.toParseTreeText())
      .isEqualTo(
        """
          FILE
            PsiElement(ASSIGNMENT)
              PsiElement(IDENTIFIER)
                PsiElement(DeclarativeTokenType.token)('foo')
              PsiElement(DeclarativeTokenType.=)('=')
              PsiElement(LITERAL)
                PsiElement(DeclarativeTokenType.multiline_string)('${quotes}some string$quotes')
          """.trimIndent()
      )
  }

  fun testMultiLineStringNoClosingQuotes() {
    val quotes = "\"\"\""
    assertThat(
      """
          foo = ${quotes}some string
          bar = 3
        """.toParseTreeText())
      .isEqualTo(
        """
          FILE
            PsiElement(ASSIGNMENT)
              PsiElement(IDENTIFIER)
                PsiElement(DeclarativeTokenType.token)('foo')
              PsiElement(DeclarativeTokenType.=)('=')
              PsiElement(LITERAL)
                PsiElement(DeclarativeTokenType.multiline_string)('${quotes}some string\nbar = 3')
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
            PsiElement(FACTORY)
              PsiElement(IDENTIFIER)
                PsiElement(DeclarativeTokenType.token)('foo')
              PsiElement(DeclarativeTokenType.()('(')
              PsiElement(ARGUMENTS_LIST)
                <empty list>
              PsiElement(DeclarativeTokenType.))(')')
        """.trimIndent()
      )
  }

  fun testOnlyComments() {
    assertThat(
      """
        // comment
        /* Some comment
         foo()
        */
      """.toParseTreeText()
    )
      .isEqualTo(
        """
          FILE
            PsiComment(DeclarativeTokenType.line_comment)('// comment')
            PsiComment(DeclarativeTokenType.BLOCK_COMMENT)('/* Some comment\n foo()\n*/')
        """.trimIndent()
      )
  }

  fun testCommentsAfterEntity() {
    assertThat(
      """
        dependencies {
        }
        /* Some comment
         foo()
        */
      """.toParseTreeText()
    )
      .isEqualTo(
        """
        FILE
          PsiElement(BLOCK)
            PsiElement(IDENTIFIER)
              PsiElement(DeclarativeTokenType.token)('dependencies')
            PsiElement(BLOCK_GROUP)
              PsiElement(DeclarativeTokenType.{)('{')
              PsiElement(DeclarativeTokenType.})('}')
          PsiComment(DeclarativeTokenType.BLOCK_COMMENT)('/* Some comment\n foo()\n*/')
            """.trimIndent()
      )
  }


  fun testCommentInsideBlock() {
    assertThat(
      """
        // comment
        dependencies {
        /* Some comment
         foo()
        */
        }
      """.toParseTreeText()
    )
      .isEqualTo(
        """
        FILE
          PsiComment(DeclarativeTokenType.line_comment)('// comment')
          PsiElement(BLOCK)
            PsiElement(IDENTIFIER)
              PsiElement(DeclarativeTokenType.token)('dependencies')
            PsiElement(BLOCK_GROUP)
              PsiElement(DeclarativeTokenType.{)('{')
              PsiComment(DeclarativeTokenType.BLOCK_COMMENT)('/* Some comment\n foo()\n*/')
              PsiElement(DeclarativeTokenType.})('}')
        """.trimIndent()
      )
  }

  fun testCommentInsideBlock2() {
    assertThat(
      """
        plugins{
         /*apply(libs.plugins.app)*/
         apply(libs.plugins.lib)
        }
      """.toParseTreeText()
    )
      .isEqualTo(
        """
        FILE
          PsiElement(BLOCK)
            PsiElement(IDENTIFIER)
              PsiElement(DeclarativeTokenType.token)('plugins')
            PsiElement(BLOCK_GROUP)
              PsiElement(DeclarativeTokenType.{)('{')
              PsiComment(DeclarativeTokenType.BLOCK_COMMENT)('/*apply(libs.plugins.app)*/')
              PsiElement(FACTORY)
                PsiElement(IDENTIFIER)
                  PsiElement(DeclarativeTokenType.token)('apply')
                PsiElement(DeclarativeTokenType.()('(')
                PsiElement(ARGUMENTS_LIST)
                  PsiElement(QUALIFIED)
                    PsiElement(QUALIFIED)
                      PsiElement(BARE)
                        PsiElement(IDENTIFIER)
                          PsiElement(DeclarativeTokenType.token)('libs')
                      PsiElement(DeclarativeTokenType..)('.')
                      PsiElement(IDENTIFIER)
                        PsiElement(DeclarativeTokenType.token)('plugins')
                    PsiElement(DeclarativeTokenType..)('.')
                    PsiElement(IDENTIFIER)
                      PsiElement(DeclarativeTokenType.token)('lib')
                PsiElement(DeclarativeTokenType.))(')')
              PsiElement(DeclarativeTokenType.})('}')
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
            PsiElement(FACTORY)
              PsiElement(IDENTIFIER)
                PsiElement(DeclarativeTokenType.token)('foo')
              PsiElement(DeclarativeTokenType.()('(')
              PsiElement(ARGUMENTS_LIST)
                PsiElement(LITERAL)
                  PsiElement(DeclarativeTokenType.string)('"hello, world!"')
                PsiElement(DeclarativeTokenType.,)(',')
                PsiElement(LITERAL)
                  PsiElement(DeclarativeTokenType.string)('"foo"')
                PsiElement(DeclarativeTokenType.,)(',')
                PsiElement(LITERAL)
                  PsiElement(DeclarativeTokenType.number)('123')
                PsiElement(DeclarativeTokenType.,)(',')
                PsiElement(LITERAL)
                  PsiElement(DeclarativeTokenType.number)('456')
                PsiElement(DeclarativeTokenType.,)(',')
                PsiElement(LITERAL)
                  PsiElement(DeclarativeTokenType.boolean)('true')
                PsiElement(DeclarativeTokenType.,)(',')
                PsiElement(FACTORY)
                  PsiElement(IDENTIFIER)
                    PsiElement(DeclarativeTokenType.token)('bar')
                  PsiElement(DeclarativeTokenType.()('(')
                  PsiElement(ARGUMENTS_LIST)
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
