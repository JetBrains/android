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
import com.intellij.codeInsight.daemon.ProblemHighlightFilter
import com.intellij.lang.ExternalLanguageAnnotators
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiFile
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import org.jetbrains.kotlin.idea.core.script.dependencies.KotlinScriptWorkspaceFileIndexContributor
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class KtsCatalogAnnotatorTest {

  @get:Rule
  val projectRule = AndroidProjectRule.onDisk()

  private lateinit var fixture: CodeInsightTestFixture

  @get:Rule
  val disposableRule = DisposableRule()
  @Before
  fun setup() {
    val extension = object: ProblemHighlightFilter() {
      override fun shouldHighlight(file: PsiFile): Boolean = file.name.endsWith("build.gradle.kts")
      override fun shouldProcessInBatch(file: PsiFile) = true
    }

    ExtensionTestUtil.maskExtensions(ProblemHighlightFilter.EP_NAME, listOf(extension), disposableRule.disposable)
    // skipping lint annotator
    ExtensionTestUtil.maskExtensions(ExternalLanguageAnnotators.EP_NAME, listOf(), disposableRule.disposable)

    fixture = projectRule.fixture
    val catalog = fixture.addFileToProject("gradle/libs.versions.toml","""
      [versions]
      my_version = "1.0"
      [plugins]
      android_application = "com.android.application:8.0"
      [libraries]
      some_library = "com.example:some:1.0.0"
      [bundles]
      some_bundle = ["some_library"]
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(catalog.virtualFile)
  }

  @Test
  fun checkLibsAlias() {
    val file = fixture.addFileToProject("build.gradle.kts","""
      dependencies {
         implementation(${"libs.some.library.wrong" highlightedAs HighlightSeverity.ERROR})
      }
    """.trimIndent())

    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkBundlesAlias() {
    val file = fixture.addFileToProject("build.gradle.kts","""
      dependencies {
         implementation(${"libs.bundles.some.wrong" highlightedAs HighlightSeverity.ERROR})
      }
    """.trimIndent())

    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkPluginAlias() {
    val file = fixture.addFileToProject("build.gradle.kts","""
      plugins{
         alias(${"libs.plugin.android.application2" highlightedAs HighlightSeverity.ERROR})
      }
    """.trimIndent())

    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkNoErrors() {
    val file = fixture.addFileToProject("build.gradle.kts","""
      plugins {
         alias(libs.plugins.android.application)
      }
      dependencies {
         implementation(libs.some.library)
         implementation(libs.bundles.some.bundle)
      }
    """.trimIndent())

    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }
  @Test
  fun checkGetSyntaxSupport() {
    val file = fixture.addFileToProject("build.gradle.kts","""
      val version = libs.versions.my.version.get()
    """.trimIndent())

    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkGetSyntaxSupportWithErrors() {
    val file = fixture.addFileToProject("build.gradle.kts","""
      val version = ${"libs.versions.my.version2" highlightedAs HighlightSeverity.ERROR}.get()
    """.trimIndent())

    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  // no highlighting if it's not a catalog reference
  @Test
  fun checkNotCatalogProperties() {
    val file = fixture.addFileToProject("build.gradle.kts","""
      android {
        testOptions.manageDevices.devices {
        }
      }
    """.trimIndent())

    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

}
