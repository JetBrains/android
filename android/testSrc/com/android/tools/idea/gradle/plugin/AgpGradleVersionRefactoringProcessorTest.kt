/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.plugin

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.project.sync.setup.post.upgrade.AgpClasspathDependencyRefactoringProcessor
import com.android.tools.idea.gradle.project.sync.setup.post.upgrade.AgpGradleVersionRefactoringProcessor
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.RunsInEdt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@RunsInEdt
class AgpGradleVersionRefactoringProcessorTest : UpgradeGradleFileModelTestCase() {
  private lateinit var wrapperSettingsFile : VirtualFile

  @Before
  fun setUpWrapperSettingsFile() {
    runWriteAction {
      wrapperSettingsFile = projectRule.fixture.tempDirFixture.createFile("gradle/wrapper/gradle-wrapper.properties")
      assertTrue(wrapperSettingsFile.isWritable)
    }
  }

  @Test
  fun testIsAlwaysEnabled() {
    val versions = listOf("1.5.0", "2.2.0", "2.3.2", "3.0.0", "3.3.2", "3.4.2", "3.5.0")
    versions.forEach { current ->
      versions.forEach { new ->
        val processor = AgpClasspathDependencyRefactoringProcessor(project, GradleVersion.parse(current), GradleVersion.parse(new))
        assertTrue(processor.isEnabled)
      }
    }
  }

  // Although the AgpGradleVersionRefactoringProcessor is part of the GradleBuildModel set of RefactoringProcessors, it
  // in fact edits a file (the Gradle Wrapper properties file) which is not strictly part of the build model.  That means it can't
  // be unit tested in quite the same way.

  private fun writeToGradleWrapperPropertiesFile(fileName: TestFileName) {
    val testFile = fileName.toFile(testDataPath, "")
    assertTrue(testFile.exists())
    val virtualTestFile = VfsUtil.findFileByIoFile(testFile, true)
    runWriteAction { VfsUtil.saveText(wrapperSettingsFile, VfsUtilCore.loadText(virtualTestFile!!)) }
  }

  @Test
  fun testOldGradleVersion() {
    writeToGradleWrapperPropertiesFile(TestFileName("AgpGradleVersion/OldGradleVersion"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.1.0"), GradleVersion.parse("6.5"))
    processor.run()

    val expectedText = FileUtil.loadFile(TestFileName("AgpGradleVersion/OldGradleVersionExpected").toFile(testDataPath, ""))
    val actualText = VfsUtilCore.loadText(wrapperSettingsFile)
    assertEquals(expectedText, actualText)
  }

  @Test
  fun testOverrideIsEnabled() {
    writeToGradleWrapperPropertiesFile(TestFileName("AgpGradleVersion/OldGradleVersion"))
    val processor = AgpGradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.1.0"), GradleVersion.parse("6.5"))
    assertTrue(processor.isEnabled)
    processor.isEnabled = false
    processor.run()

    val expectedText = FileUtil.loadFile(TestFileName("AgpGradleVersion/OldGradleVersion").toFile(testDataPath, ""))
    val actualText = VfsUtilCore.loadText(wrapperSettingsFile)
    assertEquals(expectedText, actualText)
  }

  // TODO(b/159420573): test that with a sufficiently new (>= GRADLE_MINIMUM_VERSION) declared version of gradle, this
  //  processor does nothing.  (Need to programmatically write the properties file so that it doesn't fail when
  //  GRADLE_MINIMUM_VERSION changes)
}