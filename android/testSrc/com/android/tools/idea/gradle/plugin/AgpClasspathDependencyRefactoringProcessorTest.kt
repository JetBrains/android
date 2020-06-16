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
import com.intellij.testFramework.RunsInEdt
import org.junit.Assert.assertTrue
import org.junit.Test

@RunsInEdt
class AgpClasspathDependencyRefactoringProcessorTest : UpgradeGradleFileModelTestCase() {
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
}