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
package com.android.tools.idea.gradle.catalog

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.highlightedAs
import com.intellij.codeInsight.daemon.impl.analysis.AnnotationSessionImpl.computeWithSession
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiElement
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.toml.lang.psi.TomlElement
import org.toml.lang.psi.TomlPsiFactory
import org.toml.lang.psi.TomlRecursiveVisitor

@RunWith(JUnit4::class)
@RunsInEdt
class VersionsTomlAnnotatorTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withSdk()

  private lateinit var fixture: JavaCodeInsightTestFixture

  @Before
  fun setup() {
    fixture = projectRule.fixture
  }

  @Test
  fun checkAliasNamingOneLetter() {
    val file = fixture.addFileToProject("gradle/libs.versions.toml","""
      [plugins]
      ${"a" highlightedAs HighlightSeverity.ERROR} = "some:plugin"
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkAliasNamingStartWithDigit() {
    val file = fixture.addFileToProject("gradle/libs.versions.toml","""
      [plugins]
      ${"1A" highlightedAs HighlightSeverity.ERROR} = "some:plugin"
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkAliasNamingForTwoLetters() {
    val file = fixture.addFileToProject("gradle/libs.versions.toml","""
      [plugins]
      aa = "some:plugin"
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkManualIterationThroughTree() {
    val file = fixture.addFileToProject("gradle/libs.versions.toml", """
      [plugins]
      aa = "some:plugin"
      [libraries]
      lib = "com.example:example:1.9"
    """.trimIndent())
    val psiFile = file.originalFile
    val annotator = VersionsTomlAnnotator()
    runReadAction {
      computeWithSession(psiFile, false) { holder ->
        val visitor = object : TomlRecursiveVisitor() {
          override fun visitElement(element: TomlElement) {
            holder.runAnnotatorWithContext(element, annotator)
            super.visitElement(element as PsiElement)
          }
        }
        visitor.visitElement(psiFile)
      }
    }
  }

  @Test
  fun checkManualCheckNewElement() {
    val file = fixture.addFileToProject("gradle/libs.versions.toml", """
      [plugins]
      aa = "some:plugin"
    """.trimIndent())
    val psiFile = file.originalFile
    val annotator = VersionsTomlAnnotator()
    val libs = runReadAction {
      TomlPsiFactory(fixture.project).createTable("libraries")
    }

    WriteCommandAction.runWriteCommandAction(fixture.project) { psiFile.add(libs) }

    runReadAction {
      computeWithSession(psiFile, false) { holder ->
        holder.runAnnotatorWithContext(libs, annotator)
        holder.runAnnotatorWithContext(libs.header.key?.segments?.get(0)?.firstChild!!, annotator) // check leaf
      }
    }
  }

  @Test
  fun checkAliasNamingWrongSymbol() {
    val file = fixture.addFileToProject("gradle/libs.versions.toml","""
      [plugins]
      ${"\"wrong+symbol\"" highlightedAs HighlightSeverity.ERROR} = "some:plugin"
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkNormalAlias() {
    val file = fixture.addFileToProject("gradle/libs.versions.toml","""
      [plugins]
      some_Normal-PluginAlias = "some:plugin"
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkNormalAliasQuted() {
    val file = fixture.addFileToProject("gradle/libs.versions.toml","""
      [plugins]
      "some_Normal-PluginAlias" = "some:plugin"
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkAliasFirstCapital() {
    val file = fixture.addFileToProject("gradle/libs.versions.toml","""
      [plugins]
      ${"Alias" highlightedAs HighlightSeverity.ERROR}= "some:plugin"
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkWrongTableName() {
    val file = fixture.addFileToProject("gradle/libs.versions.toml","""
      [${"temp" highlightedAs HighlightSeverity.ERROR}]
      alias = "some:plugin"
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkWrongTableName2() {
    val file = fixture.addFileToProject("gradle/libs.versions.toml","""
      [${"plugins.plugins" highlightedAs HighlightSeverity.ERROR}]
      alias = "some:plugin"
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkQuotedTableName() {
    val file = fixture.addFileToProject("gradle/libs.versions.toml","""
      ["plugins"]
      alias = "some:plugin"
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkNormalTableName() {
    val file = fixture.addFileToProject("gradle/libs.versions.toml","""
      [plugins]
      alias = "some:plugin"
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }


  @Test
  fun checkDuplicationNames_SimpleCase() {
    val file = fixture.addFileToProject("gradle/libs.versions.toml","""
      [plugins]
      ${"alias" highlightedAs HighlightSeverity.ERROR} = "some:plugin"
      ${"alias" highlightedAs HighlightSeverity.ERROR} = "some:plugin"
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkDuplicationNames_MixedNotation() {
    val file = fixture.addFileToProject("gradle/libs.versions.toml","""
      [plugins]
      ${"some_plugin" highlightedAs HighlightSeverity.ERROR} = "some:plugin"
      ${"some-plugin" highlightedAs HighlightSeverity.ERROR} = "some:plugin"
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkDuplicationNames_MixedNotation2() {
    val file = fixture.addFileToProject("gradle/libs.versions.toml","""
      [plugins]
      ${"some_plugin" highlightedAs HighlightSeverity.ERROR} = "some:plugin"
      ${"\"some-plugin\"" highlightedAs HighlightSeverity.ERROR} = "some:plugin"
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkDuplicationNames_MixedNotation3() {
    val file = fixture.addFileToProject("gradle/libs.versions.toml","""
      [plugins]
      ${"some_plugin" highlightedAs HighlightSeverity.ERROR} = "some:plugin"
      ${"\"some-plugin\"" highlightedAs HighlightSeverity.ERROR} = "some:plugin"
      ${"some-plugin" highlightedAs HighlightSeverity.ERROR} = "some:plugin"
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkDuplicationNames_WithinSingleTable() {
    val file = fixture.addFileToProject("gradle/libs.versions.toml","""
      [plugins]
      ${"some_alias" highlightedAs HighlightSeverity.ERROR} = "some:plugin"
      ${"\"some-alias\"" highlightedAs HighlightSeverity.ERROR} = "some:plugin"
      ${"some_alias" highlightedAs HighlightSeverity.ERROR} = "some:plugin"

      [libraries]
      some_alias = "some:group:1.0"
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkDuplicationNames_CheckMessage() {
    val file = fixture.addFileToProject("gradle/libs.versions.toml","""
      [plugins]
      ${"some_alias".highlightedAs(HighlightSeverity.ERROR, "Duplicated alias name. Effectively same as some-alias.")} = "some:plugin"
      ${"\"some-alias\"".highlightedAs(HighlightSeverity.ERROR, "Duplicated alias name. Effectively same as some_alias.")} = "some:plugin"

      [libraries]
      some_alias = "some:group:1.0"
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkDuplicationNames_CheckMessageForThree() {
    val file = fixture.addFileToProject("gradle/libs.versions.toml","""
      [plugins]
      ${"some_alias".highlightedAs(HighlightSeverity.ERROR, "Duplicated alias name. Effectively same as some-alias, some-alias etc.")} = "some:plugin"
      ${"\"some-alias\"".highlightedAs(HighlightSeverity.ERROR, "Duplicated alias name. Effectively same as some_alias, some-alias etc.")} = "some:plugin"
      ${"\"some-alias\"".highlightedAs(HighlightSeverity.ERROR, "Duplicated alias name. Effectively same as some_alias, some-alias etc.")} = "some:plugin"
      ${"\"some-alias\"".highlightedAs(HighlightSeverity.ERROR, "Duplicated alias name. Effectively same as some_alias, some-alias etc.")} = "some:plugin"

      [libraries]
      some_alias = "some:group:1.0"
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkTableDuplicationNames() {
    val file = fixture.addFileToProject("gradle/libs.versions.toml","""
      [${"plugins".highlightedAs(HighlightSeverity.ERROR, "Duplicated table name.")}]
      alias = "some:plugin"

      [libraries]
      some_alias = "some:group:1.0"

      [${"plugins".highlightedAs(HighlightSeverity.ERROR, "Duplicated table name.")}]
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkWrongTableNames_withDots() {
    val file = fixture.addFileToProject("gradle/libs.versions.toml","""
      [${"plugins.plugins" highlightedAs HighlightSeverity.ERROR}]
      alias = "some:plugin"

      [libraries]
      some_alias = "some:group:1.0"

    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkWrongTableNames_withDots2() {
    val file = fixture.addFileToProject("gradle/libs.versions.toml","""
      [${"\"plugins.plugins\"" highlightedAs HighlightSeverity.ERROR}]
      alias = "some:plugin"

      [libraries]
      some_alias = "some:group:1.0"

    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkTableDuplicationNames_withQuotationMarks() {
    val file = fixture.addFileToProject("gradle/libs.versions.toml","""
      [${"\"plugins\"".highlightedAs(HighlightSeverity.ERROR, "Duplicated table name.")}]
      alias = "some:plugin"

      [libraries]
      some_alias = "some:group:1.0"

      [${"plugins".highlightedAs(HighlightSeverity.ERROR, "Duplicated table name.")}]
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkAliasDuplicationUnicode() {
    val file = fixture.addFileToProject("gradle/libs.versions.toml","""
      [plugins]
      ${"alias" highlightedAs HighlightSeverity.ERROR } = "some:plugin"
      ${"\"\\u0061\\u006c\\u0069\\u0061\\u0073\"" highlightedAs HighlightSeverity.ERROR}= "some:plugin"
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkTableDuplicationNames_Unicode() {
    val file = fixture.addFileToProject("gradle/libs.versions.toml","""
      [${"\"\\u0070\\u006c\\u0075\\u0067\\u0069\\u006e\\u0073\"".highlightedAs(HighlightSeverity.ERROR, "Duplicated table name.")}]
      alias = "some:plugin"

      [libraries]
      some_alias = "some:group:1.0"

      [${"plugins".highlightedAs(HighlightSeverity.ERROR, "Duplicated table name.")}]
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkQuotatedAliasWithDotsSpecialCase() {
    val file = fixture.addFileToProject("gradle/libs.versions.toml","""
      [plugins]
      ${"\"some.alias\".alias" highlightedAs HighlightSeverity.ERROR} = "some:plugin"
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkAliasDuplicationSpecialCase() {
    // This syntax is invalid, but we don't check such case as it's quite rare
    // It must be something like some.id = "..." or some.value = "..."
    val file = fixture.addFileToProject("gradle/libs.versions.toml","""
      [plugins]
      "some_alias".alias = "some:plugin"
      some.alias.alias = "some:plugin"
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkAliasDuplicationSpecialCase2() {
    val file = fixture.addFileToProject("gradle/libs.versions.toml","""
      [plugins]
      alias.id = "id"
      alias.version = "1.0"
      ${"alias2" highlightedAs HighlightSeverity.ERROR } = "some:plugin"
      ${"alias2" highlightedAs HighlightSeverity.ERROR } = "some:plugin"
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkAliasDuplicationSpecialCase3() {
    val file = fixture.addFileToProject("gradle/libs.versions.toml","""
      [plugins]
      alias.id = "id"
      alias.version = "1.0"
      ${"alias_A" highlightedAs HighlightSeverity.ERROR } = "some:plugin"
      ${"alias_a" highlightedAs HighlightSeverity.ERROR } = "some:plugin"
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkDoubleUnderscore() {
    val file = fixture.addFileToProject("gradle/libs.versions.toml","""
      [plugins]
      ${"al__ias2" highlightedAs HighlightSeverity.ERROR } = "some:plugin"
      ${"al-_ias2" highlightedAs HighlightSeverity.ERROR } = "some:plugin"
      ${"al__ias2" highlightedAs HighlightSeverity.ERROR } = "some:plugin"
      ${"al_-ias2" highlightedAs HighlightSeverity.ERROR } = "some:plugin"
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkDoubleUnderscore2() {
    val file = fixture.addFileToProject("gradle/libs.versions.toml","""
      [plugins]
      ${"al______ias2" highlightedAs HighlightSeverity.ERROR } = "some:plugin"
      ${"ali_-_-_-as2" highlightedAs HighlightSeverity.ERROR } = "some:plugin"
      ${"ali------as2" highlightedAs HighlightSeverity.ERROR } = "some:plugin"
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkTrailingUnderscore() {
    val file = fixture.addFileToProject("gradle/libs.versions.toml","""
      [plugins]
      ${"alias_" highlightedAs HighlightSeverity.ERROR } = "some:plugin"
      ${"alias-" highlightedAs HighlightSeverity.ERROR } = "some:plugin"
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkDigitAfterDelimiter() {
    val file = fixture.addFileToProject("gradle/libs.versions.toml","""
      [plugins]
      ${"first_4Plugin" highlightedAs HighlightSeverity.ERROR } = "some:plugin"
      ${"second-4Plugin" highlightedAs HighlightSeverity.ERROR } = "some:plugin"
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkDigitAfterDelimiterForVersions() {
    val file = fixture.addFileToProject("gradle/libs.versions.toml","""
      [versions]
      ${"first_4Version" highlightedAs HighlightSeverity.WARNING } = "1.0"
      ${"second-4Version" highlightedAs HighlightSeverity.WARNING } = "1.0"
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkGradleNamingConflict() {
    val file = fixture.addFileToProject("gradle/libs.versions.toml","""
      [plugins]
      bundles = "some:plugin"

      [libraries]
      ${"plugins_some" highlightedAs HighlightSeverity.ERROR } = "some:library"
      ${"bundles" highlightedAs HighlightSeverity.ERROR } = "some:library"
      ${"versions-alias" highlightedAs HighlightSeverity.ERROR } = "some:library"

      [versions]
      plugins = "some:plugin"

      [bundles]
      bundles = "some:plugin"
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

}
