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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.RunsInEdt
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@RunsInEdt
class AgpClasspathDependencyRefactoringProcessorTest : UpgradeGradleFileModelTestCase() {
  @Test
  fun testIsAlwaysEnabled() {
    val versions = listOf("1.5.0", "2.2.0", "2.3.2", "3.0.0", "3.3.2", "3.4.2", "3.5.0", "4.0.0", "4.1.0", "5.0.0", "5.1.0")
    versions.forEach { current ->
      versions.forEach { new ->
        val currentVersion = GradleVersion.parse(current)
        val newVersion = GradleVersion.parse(new)
        if (newVersion > currentVersion) {
          val processor = AgpClasspathDependencyRefactoringProcessor(project, currentVersion, newVersion)
          assertTrue(processor.isEnabled)
        }
      }
    }
  }

  @Test
  fun testIsAlwaysMandatoryCodependent() {
    val versions = listOf("1.5.0", "2.2.0", "2.3.2", "3.0.0", "3.3.2", "3.4.2", "3.5.0", "4.0.0", "4.1.0", "5.0.0", "5.1.0")
    versions.forEach { current ->
      versions.forEach { new ->
        val currentVersion = GradleVersion.parse(current)
        val newVersion = GradleVersion.parse(new)
        if (newVersion > currentVersion) {
          val processor = AgpClasspathDependencyRefactoringProcessor(project, currentVersion, newVersion)
          assertEquals(processor.necessity(), MANDATORY_CODEPENDENT)
        }
      }
    }
  }

  @Test
  fun testVersionInLiteral() {
    writeToBuildFile(TestFileName("AgpClasspathDependency/VersionInLiteral"))
    val processor = AgpClasspathDependencyRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.1.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("AgpClasspathDependency/VersionInLiteralExpected"))
  }

  @Test
  fun testVersionInInterpolatedVariable() {
    writeToBuildFile(TestFileName("AgpClasspathDependency/VersionInInterpolatedVariable"))
    val processor = AgpClasspathDependencyRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.1.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("AgpClasspathDependency/VersionInInterpolatedVariableExpected"))
  }

  @Test
  fun testOverrideIsEnabled() {
    writeToBuildFile(TestFileName("AgpClasspathDependency/VersionInInterpolatedVariable"))
    val processor = AgpClasspathDependencyRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.1.0"))
    assertTrue(processor.isEnabled)
    processor.isEnabled = false
    processor.run()
    verifyFileContents(buildFile, TestFileName("AgpClasspathDependency/VersionInInterpolatedVariable"))
  }

  @Test
  fun testIsNotNoOpOnVersionInLiteral() {
    writeToBuildFile(TestFileName("AgpClasspathDependency/VersionInLiteral"))
    val processor = AgpClasspathDependencyRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.1.0"))
    assertFalse(processor.isAlwaysNoOpForProject)
  }

  @Test
  fun testIsNotNoOpOnVersionInInterpolatedVariable() {
    writeToBuildFile(TestFileName("AgpClasspathDependency/VersionInInterpolatedVariable"))
    val processor = AgpClasspathDependencyRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.1.0"))
    assertFalse(processor.isAlwaysNoOpForProject)
  }

  @Test
  fun testNonClasspathDependencies() {
    writeToBuildFile(TestFileName("AgpClasspathDependency/BuildSrcDependency"))
    val processor = AgpClasspathDependencyRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.1.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("AgpClasspathDependency/BuildSrcDependency"))
  }

  @Test
  fun testDependenciesInBuildSrc() {
    writeToBuildFile(TestFileName("AgpClasspathDependency/VersionInLiteral"))
    lateinit var buildSrcFile: VirtualFile
    runWriteAction {
      buildSrcFile = projectRule.fixture.tempDirFixture.findOrCreateDir("buildSrc").createChildData(this, buildFileName)
      val testFile = TestFileName("AgpClasspathDependency/BuildSrcDependency").toFile(testDataPath, testDataExtension!!)
      val virtualTestFile = VfsUtil.findFileByIoFile(testFile, true)
      VfsUtil.saveText(buildSrcFile, VfsUtilCore.loadText(virtualTestFile!!))
    }
    val processor = AgpClasspathDependencyRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.1.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("AgpClasspathDependency/VersionInLiteralExpected"))
    verifyFileContents(buildSrcFile, TestFileName("AgpClasspathDependency/BuildSrcDependencyExpected"))
  }

  @Test
  fun testLiteralTooltipsNotNull() {
    writeToBuildFile(TestFileName("AgpClasspathDependency/VersionInLiteral"))
    val processor = AgpClasspathDependencyRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.1.0"))
    val usages = processor.findUsages()
    assertTrue(usages.isNotEmpty())
    usages.forEach { assertNotNull(it.tooltipText) }
  }

  @Test
  fun testInterpolatedVariableTooltipsNotNull() {
    writeToBuildFile(TestFileName("AgpClasspathDependency/VersionInInterpolatedVariable"))
    val processor = AgpClasspathDependencyRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.1.0"))
    val usages = processor.findUsages()
    assertTrue(usages.isNotEmpty())
    usages.forEach { assertNotNull(it.tooltipText) }
  }
}