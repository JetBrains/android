/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.ide.common.repository.GradleVersion.AgpVersion
import com.intellij.testFramework.RunsInEdt
import org.junit.Test

@RunsInEdt
class RemoveImplementationPropertiesRefactoringProcessorTest: UpgradeGradleFileModelTestCase() {
  @Test
  fun testDynamicFeature420Template() {
    writeToBuildFile(TestFileName("RemoveImplementationProperties/DynamicFeature420Template"))
    val processor = RemoveImplementationPropertiesRefactoringProcessor(project, AgpVersion.parse("4.2.0"), AgpVersion.parse("7.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("RemoveImplementationProperties/DynamicFeature420TemplateExpected"))
  }

  @Test
  fun testApplicationEverything() {
    writeToBuildFile(TestFileName("RemoveImplementationProperties/ApplicationEverything"))
    val processor = RemoveImplementationPropertiesRefactoringProcessor(project, AgpVersion.parse("4.2.0"), AgpVersion.parse("7.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("RemoveImplementationProperties/ApplicationEverythingExpected"))
  }

  @Test
  fun testDynamicFeatureEverything() {
    writeToBuildFile(TestFileName("RemoveImplementationProperties/DynamicFeatureEverything"))
    val processor = RemoveImplementationPropertiesRefactoringProcessor(project, AgpVersion.parse("4.2.0"), AgpVersion.parse("7.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("RemoveImplementationProperties/DynamicFeatureEverythingExpected"))
  }

  @Test
  fun testLibraryEverything() {
    writeToBuildFile(TestFileName("RemoveImplementationProperties/LibraryEverything"))
    val processor = RemoveImplementationPropertiesRefactoringProcessor(project, AgpVersion.parse("4.2.0"), AgpVersion.parse("7.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("RemoveImplementationProperties/LibraryEverythingExpected"))
  }

  @Test
  fun testTestEverything() {
    writeToBuildFile(TestFileName("RemoveImplementationProperties/TestEverything"))
    val processor = RemoveImplementationPropertiesRefactoringProcessor(project, AgpVersion.parse("4.2.0"), AgpVersion.parse("7.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("RemoveImplementationProperties/TestEverythingExpected"))
  }

  @Test
  fun testPluginsDslRoot() {
    writeToBuildFile(TestFileName("RemoveImplementationProperties/PluginsDslRoot"))
    val processor = RemoveImplementationPropertiesRefactoringProcessor(project, AgpVersion.parse("4.2.0"), AgpVersion.parse("7.0.0"))
    processor.run()

    verifyFileContents(buildFile, TestFileName("RemoveImplementationProperties/PluginsDslRoot"))
  }
}