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
package com.android.tools.idea.gradle.something

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.dsl.TestFileName
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.annotations.SystemIndependent
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.IOException

// Test is based on generated schema files. Schema has declarations for
// androidApplication{compileSdk, namespace, jdkVersion, minSdk}, declarativeDependencies{api, implementation}
@RunsInEdt
class SomethingCompletionContributorTest {
  @get:Rule
  val projectRule = AndroidProjectRule.onDisk().onEdt()
  private val fixture by lazy { projectRule.fixture }

  @Before
  fun before() {
    StudioFlags.GRADLE_DECLARATIVE_SOMETHING_IDE_SUPPORT.override(true)
  }

  @After
  fun onAfter() = StudioFlags.GRADLE_DECLARATIVE_SOMETHING_IDE_SUPPORT.clearOverride()

  @Test
  fun testBasicRootCompletion() {
    writeToSchemaFile(TestFile.DECLARATIVE_GENERATED_SCHEMAS)
    doTest("a$caret") { suggestions ->
      assertThat(suggestions.toList()).containsExactly(
        "androidApplication", "declarativeDependencies"
      )
    }
  }

  @Test
  fun testInsideBlockCompletion() {
    writeToSchemaFile(TestFile.DECLARATIVE_GENERATED_SCHEMAS)
    doTest("""
      androidApplication{
        a$caret
      }
      """) { suggestions ->
      assertThat(suggestions.toList()).containsExactly(
        "namespace"
      )
    }
  }

  @Test
  fun testPluginBlockCompletion() {
    writeToSchemaFile(TestFile.DECLARATIVE_GENERATED_SCHEMAS)
    doTest("""
      pl$caret
      """) { suggestions ->
      assertThat(suggestions.toList()).containsExactly(
        "plugins", "androidApplication"
      )
    }
  }

  @Test
  fun testInsidePluginBlockCompletion() {
    writeToSchemaFile(TestFile.DECLARATIVE_GENERATED_SCHEMAS)
    doTest("""
      plugins{
        i$caret
      }
      """) { suggestions ->
      assertThat(suggestions.toList()).containsExactly(
        "id", "kotlin"
      )
    }
  }

  private fun doTest(declarativeFile: String, check: (List<String>) -> Unit) {
    val buildFile = fixture.addFileToProject(
      "build.gradle.something", declarativeFile)
    fixture.configureFromExistingVirtualFile(buildFile.virtualFile)
    fixture.completeBasic()
    val list: List<String> = fixture.lookupElements!!.map {
      val presentation = LookupElementPresentation()
      it.renderElement(presentation)
      it.lookupString
    }

    check.invoke(list)
  }
  // Keep this method here for now as schema files suppose to go away soon
  @Throws(IOException::class)
  private fun writeToSchemaFile(filename: TestFileName) {
    val projectFileName = "project.something.schema"
    val pluginFileName = "plugins.something.schema"

    val myTestDataRelativePath = "tools/adt/idea/gradle-dsl/testData/parser"
    val projectFile = filename.toFile(myTestDataRelativePath, projectFileName)
    val pluginFile = filename.toFile(myTestDataRelativePath, pluginFileName)
    val virtualProjectFile = VfsUtil.findFileByIoFile(projectFile, true)
    val virtualPluginFile = VfsUtil.findFileByIoFile(pluginFile, true)
    val projectDir = projectRule.project.guessProjectDir()!!
    runWriteAction {
      val gradlePath =  projectDir.createChildDirectory(this, ".gradle")
      val schemaFolder = gradlePath.createChildDirectory(this, "restricted-schema")
      val schemaProjectFile = schemaFolder.createChildData(this, projectFileName)
      val schemaPluginFile = schemaFolder.createChildData(this, pluginFileName)
      VfsUtil.saveText(schemaProjectFile, VfsUtilCore.loadText(
        virtualProjectFile!!))
      VfsUtil.saveText(schemaPluginFile, VfsUtilCore.loadText(
        virtualPluginFile!!))
    }
  }

  internal enum class TestFile(private val path: @SystemIndependent String) : TestFileName {
    DECLARATIVE_GENERATED_SCHEMAS("somethingDeclarative/schemas");

    override fun toFile(basePath: @SystemIndependent String, extension: String): File {
      return super.toFile("$basePath/$path/", extension)
    }
  }

}