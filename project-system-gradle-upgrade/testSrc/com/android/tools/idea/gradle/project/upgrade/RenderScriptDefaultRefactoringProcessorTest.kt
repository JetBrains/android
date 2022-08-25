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

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.buildMainSourceProviderStub
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.junit.Assert.assertEquals
import org.junit.Test

@RunsInEdt
class RenderScriptDefaultRefactoringProcessorTest : UpgradeGradleFileModelTestCase() {
  override val projectRule = AndroidProjectRule.withAndroidModel(
    AndroidProjectBuilder().withMainSourceProvider { buildMainSourceProviderStub() }
  )

  @Test
  fun testReadMoreUrl() {
    val processor = RenderScriptDefaultRefactoringProcessor(project, GradleVersion.parse("7.0.0"), GradleVersion.parse("8.0.0"))
    assertEquals("https://developer.android.com/r/tools/upgrade-assistant/render-script-default", processor.getReadMoreUrl())
  }

  @Test
  fun testNoRenderScriptDirectory() {
    writeToBuildFile(TestFileName("RenderScriptDefault/NoRenderScriptDeclaration"))
    val processor = RenderScriptDefaultRefactoringProcessor(project, GradleVersion.parse("7.0.0"), GradleVersion.parse("8.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RenderScriptDefault/NoRenderScriptDeclaration"))
  }

  @Test
  fun testEmptyRenderScriptDirectory() {
    writeToBuildFile(TestFileName("RenderScriptDefault/NoRenderScriptDeclaration"))
    runWriteAction {
      projectRule.fixture.tempDirFixture.findOrCreateDir("src/main/rs")
    }
    val processor = RenderScriptDefaultRefactoringProcessor(project, GradleVersion.parse("7.0.0"), GradleVersion.parse("8.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RenderScriptDefault/NoRenderScriptDeclaration"))
  }

  @Test
  fun testNonEmptyRenderScriptDirectory() {
    writeToBuildFile(TestFileName("RenderScriptDefault/NoRenderScriptDeclaration"))
    runWriteAction {
      projectRule.fixture.tempDirFixture.findOrCreateDir("src/main/rs")
      projectRule.fixture.tempDirFixture.createFile("src/main/rs/script.rs", "#pragma version(1)\n")
    }
    val processor = RenderScriptDefaultRefactoringProcessor(project, GradleVersion.parse("7.0.0"), GradleVersion.parse("8.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RenderScriptDefault/RenderScriptDeclarationAdded"))
  }

  @Test
  fun testRenderScriptFalse() {
    writeToBuildFile(TestFileName("RenderScriptDefault/RenderScriptFalse"))
    runWriteAction {
      projectRule.fixture.tempDirFixture.findOrCreateDir("src/main/rs")
      projectRule.fixture.tempDirFixture.createFile("src/main/rs/script.rs", "#pragma version(1)\n")
    }
    val processor = RenderScriptDefaultRefactoringProcessor(project, GradleVersion.parse("7.0.0"), GradleVersion.parse("8.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RenderScriptDefault/RenderScriptFalse"))
  }

  @Test
  fun testRenderScriptTrue() {
    writeToBuildFile(TestFileName("RenderScriptDefault/RenderScriptTrue"))
    runWriteAction {
      projectRule.fixture.tempDirFixture.findOrCreateDir("src/main/rs")
      projectRule.fixture.tempDirFixture.createFile("src/main/rs/script.rs", "#pragma version(1)\n")
    }
    val processor = RenderScriptDefaultRefactoringProcessor(project, GradleVersion.parse("7.0.0"), GradleVersion.parse("8.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RenderScriptDefault/RenderScriptTrue"))
  }

  @Test
  fun testExplicitPropertyFalse() {
    writeToBuildFile(TestFileName("RenderScriptDefault/NoRenderScriptDeclaration"))
    runWriteAction {
      projectRule.fixture.tempDirFixture.findOrCreateDir("src/main/rs")
      projectRule.fixture.tempDirFixture.createFile("src/main/rs/script.rs", "#pragma version(1)\n")
      projectRule.fixture.tempDirFixture.createFile("gradle.properties", "android.defaults.buildfeatures.renderscript=FaLsE")
    }
    val processor = RenderScriptDefaultRefactoringProcessor(project, GradleVersion.parse("7.0.0"), GradleVersion.parse("8.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RenderScriptDefault/NoRenderScriptDeclaration"))
  }

  @Test
  fun testExplicitPropertyTrue() {
    writeToBuildFile(TestFileName("RenderScriptDefault/NoRenderScriptDeclaration"))
    runWriteAction {
      projectRule.fixture.tempDirFixture.findOrCreateDir("src/main/rs")
      projectRule.fixture.tempDirFixture.createFile("src/main/rs/script.rs", "#pragma version(1)\n")
      projectRule.fixture.tempDirFixture.createFile("gradle.properties", "android.defaults.buildfeatures.renderscript=TrUe")
    }
    val processor = RenderScriptDefaultRefactoringProcessor(project, GradleVersion.parse("7.0.0"), GradleVersion.parse("8.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RenderScriptDefault/NoRenderScriptDeclaration"))
  }
}