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
import com.android.tools.idea.gradle.project.sync.setup.post.upgrade.CompileRuntimeConfigurationRefactoringProcessor
import com.intellij.testFramework.RunsInEdt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@RunsInEdt
class CompileRuntimeConfigurationRefactoringProcessorTest : UpgradeGradleFileModelTestCase() {
  @Test
  fun testIsDisabledForUpgradeToOldAgp() {
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, GradleVersion.parse("2.3.2"), GradleVersion.parse("3.0.0"))
    assertFalse(processor.isEnabled)
  }

  @Test
  fun testIsEnabledForUpgradeToAgp35() {
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, GradleVersion.parse("2.3.2"), GradleVersion.parse("3.5.0"))
    assertTrue(processor.isEnabled)
  }

  @Test
  fun testIsEnabledForUpgradeToAgp4() {
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.2.0"))
    assertTrue(processor.isEnabled)
  }

  @Test
  fun testIsEnabledForUpgradeToAgp5() {
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("5.0.0"))
    assertTrue(processor.isEnabled)
  }

  @Test
  fun testIsDisabledForUpgradeFromAgp5() {
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, GradleVersion.parse("5.0.0"), GradleVersion.parse("5.1.0"))
    assertFalse(processor.isEnabled)
  }

  @Test
  fun testSimpleApplication() {
    writeToBuildFile(TestFileName("CompileRuntimeConfiguration/SimpleApplication"))
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("5.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("CompileRuntimeConfiguration/SimpleApplicationExpected"))
  }

  @Test
  fun testApplicationWithDynamicFeatures() {
    writeToBuildFile(TestFileName("CompileRuntimeConfiguration/ApplicationWithDynamicFeatures"))
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("5.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("CompileRuntimeConfiguration/ApplicationWithDynamicFeaturesExpected"))
  }

  @Test
  fun testSimpleDynamicFeature() {
    writeToBuildFile(TestFileName("CompileRuntimeConfiguration/SimpleDynamicFeature"))
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("5.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("CompileRuntimeConfiguration/SimpleDynamicFeatureExpected"))
  }

  @Test
  fun testSimpleLibrary() {
    writeToBuildFile(TestFileName("CompileRuntimeConfiguration/SimpleLibrary"))
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("5.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("CompileRuntimeConfiguration/SimpleLibraryExpected"))
  }

  @Test
  fun testMapNotationDependency() {
    writeToBuildFile(TestFileName("CompileRuntimeConfiguration/MapNotationDependency"))
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("5.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("CompileRuntimeConfiguration/MapNotationDependencyExpected"))
  }

  @Test
  fun testSimpleJavaApplication() {
    writeToBuildFile(TestFileName("CompileRuntimeConfiguration/SimpleJavaApplication"))
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("5.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("CompileRuntimeConfiguration/SimpleJavaApplicationExpected"))
  }

  @Test
  fun testSimpleJavaLibrary() {
    writeToBuildFile(TestFileName("CompileRuntimeConfiguration/SimpleJavaLibrary"))
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("5.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("CompileRuntimeConfiguration/SimpleJavaLibraryExpected"))
  }

  @Test
  fun testUnknownPlugin() {
    writeToBuildFile(TestFileName("CompileRuntimeConfiguration/UnknownPlugin"))
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("5.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("CompileRuntimeConfiguration/UnknownPlugin"))
  }
}