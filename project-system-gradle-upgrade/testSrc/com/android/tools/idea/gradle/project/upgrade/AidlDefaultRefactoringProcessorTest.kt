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
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test

@RunsInEdt
class AidlDefaultRefactoringProcessorTest : UpgradeGradleFileModelTestCase() {
  override val projectRule = AndroidProjectRule.withAndroidModel(
    AndroidProjectBuilder(includeAidlSources = { true }).withMainSourceProvider { buildMainSourceProviderStub() }
  )

  @Test
  fun testReadMoreUrl() {
    val processor = AidlDefaultRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.0.0"))
    assertEquals("https://developer.android.com/r/tools/upgrade-assistant/aidl-default", processor.getReadMoreUrl())
  }

  @Test
  fun testNoAidlDirectory() {
    writeToBuildFile(TestFileName("AidlDefault/NoAidlDeclaration"))
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    val processor = AidlDefaultRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("AidlDefault/NoAidlDeclaration"))
  }

  @Test
  fun testEmptyAidlDirectory() {
    writeToBuildFile(TestFileName("AidlDefault/NoAidlDeclaration"))
    runWriteAction {
      projectRule.fixture.tempDirFixture.findOrCreateDir("src/main/aidl")
    }
    val processor = AidlDefaultRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("AidlDefault/NoAidlDeclaration"))
  }

  @Test
  fun testNonEmptyAidlDirectory() {
    writeToBuildFile(TestFileName("AidlDefault/NoAidlDeclaration"))
    runWriteAction {
      projectRule.fixture.tempDirFixture.findOrCreateDir("src/main/aidl")
      projectRule.fixture.tempDirFixture.createFile("src/main/aidl/foo.aidl", "foo\n")
    }
    val processor = AidlDefaultRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("AidlDefault/AidlDeclarationAdded"))
  }

  @Test
  fun testAidlFalse() {
    writeToBuildFile(TestFileName("AidlDefault/AidlFalse"))
    runWriteAction {
      projectRule.fixture.tempDirFixture.findOrCreateDir("src/main/aidl")
      projectRule.fixture.tempDirFixture.createFile("src/main/aidl/foo.aidl", "foo\n")
    }
    val processor = AidlDefaultRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("AidlDefault/AidlFalse"))
  }

  @Test
  fun testAidlTrue() {
    writeToBuildFile(TestFileName("AidlDefault/AidlTrue"))
    runWriteAction {
      projectRule.fixture.tempDirFixture.findOrCreateDir("src/main/aidl")
      projectRule.fixture.tempDirFixture.createFile("src/main/aidl/foo.aidl", "foo\n")
    }
    val processor = AidlDefaultRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("AidlDefault/AidlTrue"))
  }

  @Test
  fun testExplicitPropertyFalse() {
    writeToBuildFile(TestFileName("AidlDefault/NoAidlDeclaration"))
    runWriteAction {
      projectRule.fixture.tempDirFixture.findOrCreateDir("src/main/aidl")
      projectRule.fixture.tempDirFixture.createFile("src/main/aidl/foo.aidl", "foo\n")
      projectRule.fixture.tempDirFixture.createFile("gradle.properties", "android.defaults.buildfeatures.aidl=FaLsE")
    }
    val processor = AidlDefaultRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("AidlDefault/NoAidlDeclaration"))
  }

  @Test
  fun testExplicitPropertyTrue() {
    writeToBuildFile(TestFileName("AidlDefault/NoAidlDeclaration"))
    runWriteAction {
      projectRule.fixture.tempDirFixture.findOrCreateDir("src/main/aidl")
      projectRule.fixture.tempDirFixture.createFile("src/main/aidl/foo.aidl", "foo\n")
      projectRule.fixture.tempDirFixture.createFile("gradle.properties", "android.defaults.buildfeatures.aidl=TrUe")
    }
    val processor = AidlDefaultRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("AidlDefault/NoAidlDeclaration"))
  }
}