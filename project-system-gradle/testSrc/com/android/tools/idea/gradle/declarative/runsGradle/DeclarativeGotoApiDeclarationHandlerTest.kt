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
package com.android.tools.idea.gradle.declarative.runsGradle

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.declarative.DeclarativeGoToApiDeclarationHandler
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findDocument
import com.intellij.openapi.vfs.writeText
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.utils.editor.commitToPsi
import com.intellij.testFramework.utils.editor.reloadFromDisk
import com.intellij.testFramework.utils.vfs.createFile
import com.intellij.testFramework.utils.vfs.getPsiFile
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

// This is slow test as it requires indexing
@RunsInEdt
class DeclarativeGotoApiDeclarationHandlerTest {
  @get:Rule
  val projectRule = AndroidGradleProjectRule().onEdt()

  @Before
  fun onBefore() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION_SCHEMA_KTS)
    StudioFlags.GRADLE_DECLARATIVE_IDE_SUPPORT.override(true)
  }

  @After
  fun tearDown() {
    StudioFlags.GRADLE_DECLARATIVE_IDE_SUPPORT.clearOverride()
  }

  @Test
  fun testGoToAaptOptionsInDcl() {
    checkUsage(
      "android { aaptOpt|ions { } }",
      "com.android.build.api.dsl.CommonExtension",
      "fun aaptOptions(action: com.android.build.api.dsl.AaptOptions.() -> kotlin.Unit): kotlin.Unit"
    )
  }

  @Test
  fun testGoToAndroidInDcl() {
    checkUsage(
      "andr|oid { }",
      "com.android.build.gradle.internal.dsl.BaseAppModuleExtension",
      "public open class BaseAppModuleExtension "
    )
  }

  @Test
  fun testGoToSetterProperty() {
    checkUsage(
      """
        android {
          compil|eSdk = 12
        }
      """.trimIndent(),
      "com.android.build.api.dsl.CommonExtension",
      "public abstract var compileSdk: kotlin.Int?"
    )
  }

  @Test
  fun testGoToPropertyInTheMiddle() {
    checkUsage(
      """
        android{
            aaptO|ptions { ignoreAssetsPattern = "aaa" }
        }
      """.trimIndent(),
      "com.android.build.api.dsl.CommonExtension",
      "public abstract fun aaptOptions(action: com.android.build.api.dsl.AaptOptions.() -> kotlin.Unit)"
    )
  }

  @Test
  fun testGoToFromPropertyInComplexContext() {
    checkUsage(
      """
        plugins {
        }
        android {
          compileSdk = 12
          aaptO|ptions { ignoreAssetsPattern = "aaa" }
        }
      """.trimIndent(),
      "com.android.build.api.dsl.CommonExtension",
      "public abstract fun aaptOptions(action: com.android.build.api.dsl.AaptOptions.() -> kotlin.Unit)"
    )
  }

  @Test
  @Ignore("Schema does not support Named Domain objects for now")
  fun testGoToFromBuildType() {
    checkUsage(
      "android { buildTypes { debug { versionN|ameSuffix =\"aaa\" } } }",
      "com.android.build.api.dsl.ApplicationVariantDimension",
      "var versionNameSuffix: kotlin.String?"
    )
  }

  private fun checkUsage(caretContext: String, expectedClass: String, expected: String) {
    val caret = caretContext.indexOf('|')
    assertWithMessage("The caretContext must include | somewhere to point to the caret position").that(caret).isNotEqualTo(-1)

    val withoutCaret = caretContext.substring(0, caret) + caretContext.substring(caret + 1)
    val psiFile = writeTextAndCommit("app/build.gradle.dcl", withoutCaret).getPsiFile(projectRule.project)

    projectRule.fixture.openFileInEditor(psiFile.virtualFile)

    val handler = DeclarativeGoToApiDeclarationHandler()
    val element = psiFile.findElementAt(caret)
    val target = handler.getGotoDeclarationTarget(element, null)
    assertWithMessage("Didn't find a go to destination from $caretContext").that(target).isNotNull()
    assertThat(target?.text?.substringBefore("\n")).contains(expected)
    assertThat(target?.containingFile!!.virtualFile.path).contains(expectedClass.replace(".", "/"))
  }

  // need those utility methods to make sure all files are in the same folder
  // as fixture.addFileToProject() adds file to unit named subfolder that breaks module finding logic.
  private fun writeTextAndCommit(relativePath: String, text: String): VirtualFile {
    val root = StandardFileSystems.local().findFileByPath(projectRule.project.basePath!!)!!
    return runWriteActionAndWait {
      val file = root.findFileByRelativePath(relativePath) ?: root.createFile(relativePath)
      file.writeTextAndCommit(text)
      file
    }
  }

  private fun VirtualFile.writeTextAndCommit(text: String) {
    findDocument()?.reloadFromDisk()
    writeText(text)
    findDocument()?.commitToPsi(projectRule.project)
  }

}