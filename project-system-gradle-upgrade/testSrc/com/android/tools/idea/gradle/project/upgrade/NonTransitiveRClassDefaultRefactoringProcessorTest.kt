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
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.RunsInEdt
import org.junit.Assert
import org.junit.Before
import org.junit.Test

@RunsInEdt
class NonTransitiveRClassDefaultRefactoringProcessorTest : UpgradeGradleFileModelTestCase() {
  private lateinit var gradlePropertiesFile : VirtualFile

  @Before
  fun setUpGradlePropertiesFile() {
    runWriteAction {
      gradlePropertiesFile = projectRule.fixture.tempDirFixture.createFile("gradle.properties")
      Assert.assertTrue(gradlePropertiesFile.isWritable)
    }
  }

  @Test
  fun testIsDisabledFor740() {
    val processor = NonTransitiveRClassDefaultRefactoringProcessor(project, GradleVersion.parse("7.3.0"), GradleVersion.parse("7.4.0"))
    Assert.assertFalse(processor.isEnabled)
  }

  @Test
  fun testIsEnabledFor800Beta01() {
    val processor = NonTransitiveRClassDefaultRefactoringProcessor(project, GradleVersion.parse("7.3.0"), GradleVersion.parse("8.0.0-beta01"))
    Assert.assertTrue(processor.isEnabled)
  }

  @Test
  fun testIsEnabledFrom740() {
    val processor = NonTransitiveRClassDefaultRefactoringProcessor(project, GradleVersion.parse("7.4.0"), GradleVersion.parse("8.0.0"))
    Assert.assertTrue(processor.isEnabled)
  }

  @Test
  fun testIsDisabledFrom800Beta01() {
    val processor = NonTransitiveRClassDefaultRefactoringProcessor(project, GradleVersion.parse("8.0.0-beta01"), GradleVersion.parse("8.0.0"))
    Assert.assertFalse(processor.isEnabled)
  }

  @Test
  fun testIsEnabledFor800Release() {
    val processor = NonTransitiveRClassDefaultRefactoringProcessor(project, GradleVersion.parse("7.3.0"), GradleVersion.parse("8.0.0"))
    Assert.assertTrue(processor.isEnabled)
  }

  @Test
  fun testNecessities() {
    val expectedNecessitiesMap = mapOf(
      ("7.3.0" to "7.4.0") to AgpUpgradeComponentNecessity.IRRELEVANT_FUTURE,
      ("7.3.0" to "8.0.0-alpha01") to AgpUpgradeComponentNecessity.IRRELEVANT_FUTURE,
      ("7.3.0" to "8.0.0-beta01") to AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT,
      ("7.3.0" to "8.0.0-rc01") to AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT,
      ("7.3.0" to "8.0.0") to AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT,
      ("8.0.0-alpha01" to "8.0.0") to AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT,
      ("8.0.0-beta01" to "8.0.0") to AgpUpgradeComponentNecessity.IRRELEVANT_PAST,
      ("8.0.0-rc01" to "8.0.0") to AgpUpgradeComponentNecessity.IRRELEVANT_PAST
    )
    expectedNecessitiesMap.forEach { (t, u) ->
      val processor = NonTransitiveRClassDefaultRefactoringProcessor(project, GradleVersion.parse(t.first), GradleVersion.parse(t.second))
      Assert.assertEquals("${t.first} to ${t.second}", u, processor.necessity())
    }
  }

  @Test
  fun testEmptyProperties() {
    writeToGradlePropertiesFile(TestFileName("NonTransitiveRClassDefault/Empty"))
    val processor = NonTransitiveRClassDefaultRefactoringProcessor(project, GradleVersion.parse("7.3.0"), GradleVersion.parse("8.0.0"))
    processor.run()
    verifyGradlePropertiesFileContents(gradlePropertiesFile, TestFileName("NonTransitiveRClassDefault/EmptyExpected"))
  }
  @Test
  fun testFalse() {
    writeToGradlePropertiesFile(TestFileName("NonTransitiveRClassDefault/False"))
    val processor = NonTransitiveRClassDefaultRefactoringProcessor(project, GradleVersion.parse("7.3.0"), GradleVersion.parse("8.0.0"))
    processor.run()
    verifyGradlePropertiesFileContents(gradlePropertiesFile, TestFileName("NonTransitiveRClassDefault/False"))
  }

  @Test
  fun testTrue() {
    writeToGradlePropertiesFile(TestFileName("NonTransitiveRClassDefault/True"))
    val processor = NonTransitiveRClassDefaultRefactoringProcessor(project, GradleVersion.parse("7.3.0"), GradleVersion.parse("8.0.0"))
    processor.run()
    verifyGradlePropertiesFileContents(gradlePropertiesFile, TestFileName("NonTransitiveRClassDefault/True"))
  }

  @Test
  fun testMissingGradleProperties() {
    runWriteAction { gradlePropertiesFile.delete(this) }
    val processor = NonTransitiveRClassDefaultRefactoringProcessor(project, GradleVersion.parse("7.3.0"), GradleVersion.parse("8.0.0"))
    processor.run()
    val newGradlePropertiesFile = projectRule.fixture.tempDirFixture.getFile("gradle.properties")
    Assert.assertNotNull(newGradlePropertiesFile)
    verifyGradlePropertiesFileContents(newGradlePropertiesFile!!, TestFileName("NonTransitiveRClassDefault/False"))
  }

  private fun writeToGradlePropertiesFile(fileName: TestFileName) {
    val testFile = fileName.toFile(testDataPath, "")
    Assert.assertTrue(testFile.exists())
    val virtualTestFile = VfsUtil.findFileByIoFile(testFile, true)
    runWriteAction { VfsUtil.saveText(gradlePropertiesFile, VfsUtilCore.loadText(virtualTestFile!!)) }
  }

  private fun verifyGradlePropertiesFileContents(gradlePropertiesFile: VirtualFile, testFile: TestFileName) {
    val expectedText = FileUtil.loadFile(testFile.toFile(testDataPath, ""))
    val actualText = VfsUtil.loadText(gradlePropertiesFile)
    Assert.assertEquals(expectedText, actualText)
  }
}