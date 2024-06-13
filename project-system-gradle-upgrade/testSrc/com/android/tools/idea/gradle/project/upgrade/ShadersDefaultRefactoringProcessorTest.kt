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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.buildMainSourceProviderStub
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.junit.Assert.assertEquals
import org.junit.Test

@RunsInEdt
class ShadersDefaultRefactoringProcessorTest : UpgradeGradleFileModelTestCase() {
  override val projectRule = AndroidProjectRule.withAndroidModel(
    AndroidProjectBuilder(includeShadersSources = { true }).withMainSourceProvider { buildMainSourceProviderStub() }
  )

  @Test
  fun testReadMoreUrl() {
    val processor = ShadersDefaultRefactoringProcessor(project, AgpVersion.parse("8.4.0-alpha09"), AgpVersion.parse("8.4.0-alpha10"))
    assertEquals("https://developer.android.com/r/tools/upgrade-assistant/shaders-default", processor.getReadMoreUrl())
  }

  @Test
  fun testNoShadersDirectory() {
    writeToBuildFile(TestFileName("ShadersDefault/NoShadersDeclaration"))
    val processor = ShadersDefaultRefactoringProcessor(project, AgpVersion.parse("8.4.0-alpha09"), AgpVersion.parse("8.4.0-alpha10"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("ShadersDefault/NoShadersDeclaration"))
  }

  @Test
  fun testEmptyShadersDirectory() {
    writeToBuildFile(TestFileName("ShadersDefault/NoShadersDeclaration"))
    runWriteAction {
      projectRule.fixture.tempDirFixture.findOrCreateDir("src/main/shaders")
    }
    val processor = ShadersDefaultRefactoringProcessor(project, AgpVersion.parse("8.4.0-alpha09"), AgpVersion.parse("8.4.0-alpha10"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("ShadersDefault/NoShadersDeclaration"))
  }

  @Test
  fun testNonEmptyShadersDirectory() {
    writeToBuildFile(TestFileName("ShadersDefault/NoShadersDeclaration"))
    runWriteAction {
      projectRule.fixture.tempDirFixture.findOrCreateDir("src/main/shaders")
      projectRule.fixture.tempDirFixture.createFile("src/main/shaders/foo.frag", "foo\n")
    }
    val processor = ShadersDefaultRefactoringProcessor(project, AgpVersion.parse("8.4.0-alpha09"), AgpVersion.parse("8.4.0-alpha10"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("ShadersDefault/ShadersDeclarationAdded"))
  }

  @Test
  fun testShadersFalse() {
    writeToBuildFile(TestFileName("ShadersDefault/ShadersFalse"))
    runWriteAction {
      projectRule.fixture.tempDirFixture.findOrCreateDir("src/main/shaders")
      projectRule.fixture.tempDirFixture.createFile("src/main/shaders/foo.vert", "foo\n")
    }
    val processor = ShadersDefaultRefactoringProcessor(project, AgpVersion.parse("8.4.0-alpha09"), AgpVersion.parse("8.4.0-alpha10"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("ShadersDefault/ShadersFalse"))
  }

  @Test
  fun testShadersTrue() {
    writeToBuildFile(TestFileName("ShadersDefault/ShadersTrue"))
    runWriteAction {
      projectRule.fixture.tempDirFixture.findOrCreateDir("src/main/shaders")
      projectRule.fixture.tempDirFixture.createFile("src/main/shaders/foo.geom", "foo\n")
    }
    val processor = ShadersDefaultRefactoringProcessor(project, AgpVersion.parse("8.4.0-alpha09"), AgpVersion.parse("8.4.0-alpha10"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("ShadersDefault/ShadersTrue"))
  }

  @Test
  fun testExplicitPropertyFalse() {
    writeToBuildFile(TestFileName("ShadersDefault/NoShadersDeclaration"))
    runWriteAction {
      projectRule.fixture.tempDirFixture.findOrCreateDir("src/main/shaders")
      projectRule.fixture.tempDirFixture.createFile("src/main/shaders/foo.comp", "foo\n")
      projectRule.fixture.tempDirFixture.createFile("gradle.properties", "android.defaults.buildfeatures.shaders=FaLsE")
    }
    val processor = ShadersDefaultRefactoringProcessor(project, AgpVersion.parse("8.4.0-alpha09"), AgpVersion.parse("8.4.0-alpha10"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("ShadersDefault/NoShadersDeclaration"))
  }

  @Test
  fun testExplicitPropertyTrue() {
    writeToBuildFile(TestFileName("ShadersDefault/NoShadersDeclaration"))
    runWriteAction {
      projectRule.fixture.tempDirFixture.findOrCreateDir("src/main/shaders")
      projectRule.fixture.tempDirFixture.createFile("src/main/shaders/foo.tesc", "foo\n")
      projectRule.fixture.tempDirFixture.createFile("gradle.properties", "android.defaults.buildfeatures.shaders=TrUe")
    }
    val processor = ShadersDefaultRefactoringProcessor(project, AgpVersion.parse("8.4.0-alpha09"), AgpVersion.parse("8.4.0-alpha10"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("ShadersDefault/NoShadersDeclaration"))
  }

  @Test
  fun testNecessities() {
    val expectedNecessitiesMap = mapOf(
      ("9.0.0-alpha01" to "9.0.0") to AgpUpgradeComponentNecessity.IRRELEVANT_PAST,
      ("7.0.0" to "8.4.0-alpha09") to AgpUpgradeComponentNecessity.IRRELEVANT_FUTURE,
      ("8.4.0-alpha09" to "9.0.0-alpha01") to AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT,
      ("8.4.0-alpha10" to "9.0.0-alpha01") to AgpUpgradeComponentNecessity.MANDATORY_INDEPENDENT,
      ("8.4.0-alpha09" to "8.4.0-alpha10") to AgpUpgradeComponentNecessity.OPTIONAL_CODEPENDENT,
      ("8.4.0-alpha10" to "8.5.0") to AgpUpgradeComponentNecessity.OPTIONAL_INDEPENDENT
      )
    expectedNecessitiesMap.forEach { (t, u) ->
      val processor = ShadersDefaultRefactoringProcessor(project, AgpVersion.parse(t.first), AgpVersion.parse(t.second))
      assertEquals(u, processor.necessity())
    }
  }
}