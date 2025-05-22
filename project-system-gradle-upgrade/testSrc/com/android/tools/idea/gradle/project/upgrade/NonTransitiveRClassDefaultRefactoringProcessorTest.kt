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
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.RunsInEdt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@RunsInEdt
class NonTransitiveRClassDefaultRefactoringProcessorTest : UpgradeGradleFileModelTestCase() {
  private lateinit var gradlePropertiesFile : VirtualFile

  @Before
  fun setUpGradlePropertiesFile() {
    runWriteAction {
      gradlePropertiesFile = projectRule.fixture.tempDirFixture.createFile("gradle.properties")
      assertTrue(gradlePropertiesFile.isWritable)
    }
  }

  @Test
  fun testReadMoreUrl() {
    val processor = NonTransitiveRClassDefaultRefactoringProcessor(project, AgpVersion.parse("7.3.0"), AgpVersion.parse("7.4.0"))
    assertEquals("https://developer.android.com/r/tools/upgrade-assistant/non-transitive-r-class-default", processor.getReadMoreUrl())
  }

  @Test
  fun testIsDisabledFor740() {
    val processor = NonTransitiveRClassDefaultRefactoringProcessor(project, AgpVersion.parse("7.3.0"), AgpVersion.parse("7.4.0"))
    assertFalse(processor.isEnabled)
  }

  @Test
  fun testIsEnabledFor800Alpha08() {
    val processor = NonTransitiveRClassDefaultRefactoringProcessor(project, AgpVersion.parse("7.3.0"), AgpVersion.parse("8.0.0-alpha08"))
    assertTrue(processor.isEnabled)
  }

  @Test
  fun testIsEnabledFor800Beta01() {
    val processor = NonTransitiveRClassDefaultRefactoringProcessor(project, AgpVersion.parse("7.3.0"), AgpVersion.parse("8.0.0-beta01"))
    assertTrue(processor.isEnabled)
  }

  @Test
  fun testIsEnabledFrom740() {
    val processor = NonTransitiveRClassDefaultRefactoringProcessor(project, AgpVersion.parse("7.4.0"), AgpVersion.parse("8.0.0"))
    assertTrue(processor.isEnabled)
  }

  @Test
  fun testIsDisabledFrom800Beta01() {
    val processor = NonTransitiveRClassDefaultRefactoringProcessor(project, AgpVersion.parse("8.0.0-beta01"), AgpVersion.parse("8.0.0"))
    assertFalse(processor.isEnabled)
  }

  @Test
  fun testIsEnabledFor800Release() {
    val processor = NonTransitiveRClassDefaultRefactoringProcessor(project, AgpVersion.parse("7.3.0"), AgpVersion.parse("8.0.0"))
    assertTrue(processor.isEnabled)
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
      val processor = NonTransitiveRClassDefaultRefactoringProcessor(project, AgpVersion.parse(t.first), AgpVersion.parse(t.second))
      assertEquals("${t.first} to ${t.second}", u, processor.necessity())
    }
  }

  @Test
  fun testEmptyProperties() {
    writeToGradlePropertiesFile(TestFileName("NonTransitiveRClassDefault/Empty"))
    val processor = NonTransitiveRClassDefaultRefactoringProcessor(project, AgpVersion.parse("7.3.0"), AgpVersion.parse("8.0.0"))
    processor.run()
    verifyGradlePropertiesFileContents(gradlePropertiesFile, TestFileName("NonTransitiveRClassDefault/EmptyExpected"))
  }
  @Test
  fun testFalse() {
    writeToGradlePropertiesFile(TestFileName("NonTransitiveRClassDefault/False"))
    val processor = NonTransitiveRClassDefaultRefactoringProcessor(project, AgpVersion.parse("7.3.0"), AgpVersion.parse("8.0.0"))
    processor.run()
    verifyGradlePropertiesFileContents(gradlePropertiesFile, TestFileName("NonTransitiveRClassDefault/False"))
  }

  @Test
  fun testTrue() {
    writeToGradlePropertiesFile(TestFileName("NonTransitiveRClassDefault/True"))
    val processor = NonTransitiveRClassDefaultRefactoringProcessor(project, AgpVersion.parse("7.3.0"), AgpVersion.parse("8.0.0"))
    processor.run()
    verifyGradlePropertiesFileContents(gradlePropertiesFile, TestFileName("NonTransitiveRClassDefault/True"))
  }

  @Test
  fun testMissingGradleProperties() {
    runWriteAction { gradlePropertiesFile.delete(this) }
    val processor = NonTransitiveRClassDefaultRefactoringProcessor(project, AgpVersion.parse("7.3.0"), AgpVersion.parse("8.0.0"))
    processor.run()
    val newGradlePropertiesFile = projectRule.fixture.tempDirFixture.getFile("gradle.properties")
    assertNotNull(newGradlePropertiesFile)
    verifyGradlePropertiesFileContents(newGradlePropertiesFile!!, TestFileName("NonTransitiveRClassDefault/False"))
  }

  private fun writeToGradlePropertiesFile(fileName: TestFileName) {
    val testFile = fileName.toFile(testDataPath, "")
    assertTrue(testFile.exists())
    val virtualTestFile = VfsUtil.findFileByIoFile(testFile, true)
    runWriteAction { VfsUtil.saveText(gradlePropertiesFile, VfsUtilCore.loadText(virtualTestFile!!)) }
  }

  private fun verifyGradlePropertiesFileContents(gradlePropertiesFile: VirtualFile, testFile: TestFileName) {
    val expectedText = FileUtil.loadFile(testFile.toFile(testDataPath, ""))
    val actualText = VfsUtil.loadText(gradlePropertiesFile)
    assertEquals(expectedText, actualText)
  }
}