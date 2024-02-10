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
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

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

}
