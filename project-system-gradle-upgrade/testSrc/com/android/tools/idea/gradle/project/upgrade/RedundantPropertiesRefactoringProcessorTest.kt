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
class RedundantPropertiesRefactoringProcessorTest: UpgradeGradleFileModelTestCase() {
  @Test
  fun testBuildToolsVersion41To42() {
    writeToBuildFile(TestFileName("RedundantProperties/BuildToolsVersion41"))
    val processor = RedundantPropertiesRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersion.parse("4.2.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RedundantProperties/BuildToolsVersion41To42Expected"))
  }

  @Test
  fun testBuildToolsVersion41To70() {
    writeToBuildFile(TestFileName("RedundantProperties/BuildToolsVersion41"))
    val processor = RedundantPropertiesRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersion.parse("7.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RedundantProperties/BuildToolsVersion41To70Expected"))
  }

  @Test
  fun testBuildToolsVersion41To71() {
    writeToBuildFile(TestFileName("RedundantProperties/BuildToolsVersion41"))
    val processor = RedundantPropertiesRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersion.parse("7.1.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RedundantProperties/BuildToolsVersion41To71Expected"))
  }

  @Test
  fun testBuildToolsVersion71To71() {
    writeToBuildFile(TestFileName("RedundantProperties/BuildToolsVersion71"))
    val processor = RedundantPropertiesRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersion.parse("7.1.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RedundantProperties/BuildToolsVersion71To71Expected"))
  }

  @Test
  fun testBuildToolsVersionVariable() {
    writeToBuildFile(TestFileName("RedundantProperties/BuildToolsVersionVariable"))
    val processor = RedundantPropertiesRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersion.parse("7.1.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RedundantProperties/BuildToolsVersionVariableExpected"))
  }

  @Test
  fun testBuildToolsVersionInterpolation() {
    writeToBuildFile(TestFileName("RedundantProperties/BuildToolsVersionInterpolation"))
    val processor = RedundantPropertiesRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersion.parse("7.1.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RedundantProperties/BuildToolsVersionInterpolationExpected"))
  }

  @Test
  fun testBuildToolsVersionUnresolvedVariable() {
    writeToBuildFile(TestFileName("RedundantProperties/BuildToolsVersionUnresolvedVariable"))
    val processor = RedundantPropertiesRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersion.parse("7.1.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RedundantProperties/BuildToolsVersionUnresolvedVariableExpected"))
  }
}