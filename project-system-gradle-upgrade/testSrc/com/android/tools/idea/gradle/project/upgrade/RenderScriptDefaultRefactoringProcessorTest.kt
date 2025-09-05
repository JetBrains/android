/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.buildMainSourceProviderStub
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.writeText
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@RunsInEdt
class RenderScriptDefaultRefactoringProcessorTest : UpgradeGradleFileModelTestCase() {
  private lateinit var gradlePropertiesFile : VirtualFile

  override val projectRule = AndroidProjectRule.withAndroidModel(
    AndroidProjectBuilder(includeRenderScriptSources = { true }).withMainSourceProvider { buildMainSourceProviderStub() }
  )

  @Before
  fun setUpGradlePropertiesFile() {
    com.intellij.openapi.application.runWriteAction {
      gradlePropertiesFile = projectRule.fixture.tempDirFixture.createFile("gradle.properties")
      assertTrue(gradlePropertiesFile.isWritable)
    }
  }

  @Test
  fun testReadMoreUrl() {
    val processor = RenderScriptDefaultRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.0.0"))
    assertEquals("https://developer.android.com/r/tools/upgrade-assistant/render-script-default", processor.getReadMoreUrl())
  }

  @Test
  fun testNoRenderScriptDirectory() {
    writeToBuildFile(TestFileName("RenderScriptDefault/NoRenderScriptDeclaration"))
    val processor = RenderScriptDefaultRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RenderScriptDefault/NoRenderScriptDeclaration"))
    assertThat(gradlePropertiesFile.load()).doesNotContain("android.defaults.buildfeatures.renderscript")
  }

  @Test
  fun testEmptyRenderScriptDirectory() {
    writeToBuildFile(TestFileName("RenderScriptDefault/NoRenderScriptDeclaration"))
    runWriteAction {
      projectRule.fixture.tempDirFixture.findOrCreateDir("src/main/rs")
    }
    val processor = RenderScriptDefaultRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RenderScriptDefault/NoRenderScriptDeclaration"))
    assertThat(gradlePropertiesFile.load()).doesNotContain("android.defaults.buildfeatures.renderscript")
  }

  @Test
  fun testNonEmptyRenderScriptDirectory() {
    writeToBuildFile(TestFileName("RenderScriptDefault/NoRenderScriptDeclaration"))
    runWriteAction {
      projectRule.fixture.tempDirFixture.findOrCreateDir("src/main/rs")
      projectRule.fixture.tempDirFixture.createFile("src/main/rs/script.rs", "#pragma version(1)\n")
    }
    val processor = RenderScriptDefaultRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RenderScriptDefault/RenderScriptDeclarationAdded"))
    assertThat(gradlePropertiesFile.load()).doesNotContain("android.defaults.buildfeatures.renderscript")
  }

  @Test
  fun testRenderScriptFalse() {
    writeToBuildFile(TestFileName("RenderScriptDefault/RenderScriptFalse"))
    runWriteAction {
      projectRule.fixture.tempDirFixture.findOrCreateDir("src/main/rs")
      projectRule.fixture.tempDirFixture.createFile("src/main/rs/script.rs", "#pragma version(1)\n")
    }
    val processor = RenderScriptDefaultRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RenderScriptDefault/RenderScriptFalse"))
    assertThat(gradlePropertiesFile.load()).doesNotContain("android.defaults.buildfeatures.renderscript")
  }

  @Test
  fun testRenderScriptTrue() {
    writeToBuildFile(TestFileName("RenderScriptDefault/RenderScriptTrue"))
    runWriteAction {
      projectRule.fixture.tempDirFixture.findOrCreateDir("src/main/rs")
      projectRule.fixture.tempDirFixture.createFile("src/main/rs/script.rs", "#pragma version(1)\n")
    }
    val processor = RenderScriptDefaultRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RenderScriptDefault/RenderScriptTrue"))
    assertThat(gradlePropertiesFile.load()).doesNotContain("android.defaults.buildfeatures.renderscript")
  }

  @Test
  fun testExplicitPropertyFalse() {
    writeToBuildFile(TestFileName("RenderScriptDefault/NoRenderScriptDeclaration"))
    runWriteAction {
      projectRule.fixture.tempDirFixture.findOrCreateDir("src/main/rs")
      projectRule.fixture.tempDirFixture.createFile("src/main/rs/script.rs", "#pragma version(1)\n")
      gradlePropertiesFile.writeText("android.defaults.buildfeatures.renderscript=FaLsE")
    }
    val processor = RenderScriptDefaultRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RenderScriptDefault/NoRenderScriptDeclaration"))
    assertThat(gradlePropertiesFile.load()).contains("android.defaults.buildfeatures.renderscript=FaLsE")
  }

  @Test
  fun testExplicitPropertyTrue() {
    writeToBuildFile(TestFileName("RenderScriptDefault/NoRenderScriptDeclaration"))
    runWriteAction {
      projectRule.fixture.tempDirFixture.findOrCreateDir("src/main/rs")
      projectRule.fixture.tempDirFixture.createFile("src/main/rs/script.rs", "#pragma version(1)\n")
      gradlePropertiesFile.writeText("android.defaults.buildfeatures.renderscript=TrUe")
    }
    val processor = RenderScriptDefaultRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RenderScriptDefault/NoRenderScriptDeclaration"))
    assertThat(gradlePropertiesFile.load()).contains("android.defaults.buildfeatures.renderscript=TrUe")

  }
  fun VirtualFile.load():String = VfsUtilCore.loadText(this).normalize()

  fun String.normalize() = replace("[ \\t]+".toRegex(), "").trim { it <= ' ' }

}