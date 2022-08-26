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

import com.android.ide.common.repository.GradleVersion
import com.intellij.openapi.project.Project
import com.intellij.testFramework.RunsInEdt
import org.junit.Test

@RunsInEdt
class RewriteDeprecatedOperatorsRefactoringProcessorTest: UpgradeGradleFileModelTestCase() {
  private fun rewriteDeprecatedOperatorsRefactoringProcessor(project: Project, current: GradleVersion, new: GradleVersion) =
    REWRITE_DEPRECATED_OPERATORS.RefactoringProcessor(project, current, new)

  @Test
  fun testBuildToolsVersion() {
    writeToBuildFile(TestFileName("RewriteDeprecatedOperators/BuildToolsVersion"))
    val processor = rewriteDeprecatedOperatorsRefactoringProcessor(project, GradleVersion.parse("4.2.0"), GradleVersion.parse("9.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RewriteDeprecatedOperators/BuildToolsVersionExpected"))
  }

  // TODO(b/205806471) @Test
  fun testCompileSdkVersion() {
    writeToBuildFile(TestFileName("RewriteDeprecatedOperators/CompileSdkVersion"))
    val processor = rewriteDeprecatedOperatorsRefactoringProcessor(project, GradleVersion.parse("4.2.0"), GradleVersion.parse("9.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RewriteDeprecatedOperators/CompileSdkVersionExpected"))
  }

  // TODO(b/205806471) @Test
  fun testCompileSdkVersionPreview() {
    writeToBuildFile(TestFileName("RewriteDeprecatedOperators/CompileSdkVersionPreview"))
    val processor = rewriteDeprecatedOperatorsRefactoringProcessor(project, GradleVersion.parse("4.2.0"), GradleVersion.parse("9.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RewriteDeprecatedOperators/CompileSdkVersionPreviewExpected"))
  }

  // TODO(b/205806471) @Test
  fun testCompileSdkVersionVariable() {
    writeToBuildFile(TestFileName("RewriteDeprecatedOperators/CompileSdkVersionVariable"))
    val processor = rewriteDeprecatedOperatorsRefactoringProcessor(project, GradleVersion.parse("4.2.0"), GradleVersion.parse("9.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RewriteDeprecatedOperators/CompileSdkVersionVariableExpected"))
  }

  @Test
  fun testFlavorDimensions() {
    writeToBuildFile(TestFileName("RewriteDeprecatedOperators/FlavorDimensions"))
    val processor = rewriteDeprecatedOperatorsRefactoringProcessor(project, GradleVersion.parse("4.2.0"), GradleVersion.parse("9.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RewriteDeprecatedOperators/FlavorDimensionsExpected"))
  }

  // TODO(b/205806471) @Test
  fun testMaxSdkVersion() {
    writeToBuildFile(TestFileName("RewriteDeprecatedOperators/MaxSdkVersion"))
    val processor = rewriteDeprecatedOperatorsRefactoringProcessor(project, GradleVersion.parse("4.2.0"), GradleVersion.parse("9.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RewriteDeprecatedOperators/MaxSdkVersionExpected"))
  }

  // TODO(b/205806471) @Test
  fun testMinSdkVersionPreview() {
    writeToBuildFile(TestFileName("RewriteDeprecatedOperators/MinSdkVersionPreview"))
    val processor = rewriteDeprecatedOperatorsRefactoringProcessor(project, GradleVersion.parse("4.2.0"), GradleVersion.parse("9.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RewriteDeprecatedOperators/MinSdkVersionPreviewExpected"))
  }

  // TODO(b/205806471) @Test
  fun testMinSdkVersion() {
    writeToBuildFile(TestFileName("RewriteDeprecatedOperators/MinSdkVersion"))
    val processor = rewriteDeprecatedOperatorsRefactoringProcessor(project, GradleVersion.parse("4.2.0"), GradleVersion.parse("9.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RewriteDeprecatedOperators/MinSdkVersionExpected"))
  }

  @Test
  fun testTestInstrumentationRunnerArgument() {
    writeToBuildFile(TestFileName("RewriteDeprecatedOperators/TestInstrumentationRunnerArgument"))
    val processor = rewriteDeprecatedOperatorsRefactoringProcessor(project, GradleVersion.parse("4.2.0"), GradleVersion.parse("9.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RewriteDeprecatedOperators/TestInstrumentationRunnerArgumentExpected"))
  }

  @Test
  fun testTestInstrumentationRunnerArguments() {
    writeToBuildFile(TestFileName("RewriteDeprecatedOperators/TestInstrumentationRunnerArguments"))
    val processor = rewriteDeprecatedOperatorsRefactoringProcessor(project, GradleVersion.parse("4.2.0"), GradleVersion.parse("9.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RewriteDeprecatedOperators/TestInstrumentationRunnerArgumentsExpected"))
  }

  @Test
  fun testSetDimension() {
    writeToBuildFile(TestFileName("RewriteDeprecatedOperators/SetDimension"))
    val processor = rewriteDeprecatedOperatorsRefactoringProcessor(project, GradleVersion.parse("4.2.0"), GradleVersion.parse("9.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RewriteDeprecatedOperators/SetDimensionExpected"))
  }

  @Test
  fun testSetTestInstrumentationRunnerArguments() {
    writeToBuildFile(TestFileName("RewriteDeprecatedOperators/SetTestInstrumentationRunnerArguments"))
    val processor = rewriteDeprecatedOperatorsRefactoringProcessor(project, GradleVersion.parse("4.2.0"), GradleVersion.parse("9.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RewriteDeprecatedOperators/SetTestInstrumentationRunnerArgumentsExpected"))
  }

  @Test
  fun testSetManifestPlaceholders() {
    writeToBuildFile(TestFileName("RewriteDeprecatedOperators/SetManifestPlaceholders"))
    val processor = rewriteDeprecatedOperatorsRefactoringProcessor(project, GradleVersion.parse("4.2.0"), GradleVersion.parse("9.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RewriteDeprecatedOperators/SetManifestPlaceholdersExpected"))
  }

  @Test
  fun testSetMatchingFallbacks() {
    writeToBuildFile(TestFileName("RewriteDeprecatedOperators/SetMatchingFallbacks"))
    val processor = rewriteDeprecatedOperatorsRefactoringProcessor(project, GradleVersion.parse("4.2.0"), GradleVersion.parse("9.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RewriteDeprecatedOperators/SetMatchingFallbacksExpected"))
  }

  @Test
  fun testSetTestFunctionalTest() {
    writeToBuildFile(TestFileName("RewriteDeprecatedOperators/SetTestFunctionalTest"))
    val processor = rewriteDeprecatedOperatorsRefactoringProcessor(project, GradleVersion.parse("4.2.0"), GradleVersion.parse("9.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RewriteDeprecatedOperators/SetTestFunctionalTestExpected"))
  }

  @Test
  fun testSetTestHandleProfiling() {
    writeToBuildFile(TestFileName("RewriteDeprecatedOperators/SetTestHandleProfiling"))
    val processor = rewriteDeprecatedOperatorsRefactoringProcessor(project, GradleVersion.parse("4.2.0"), GradleVersion.parse("9.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RewriteDeprecatedOperators/SetTestHandleProfilingExpected"))
  }

  @Test
  fun testResConfigs() {
    writeToBuildFile(TestFileName("RewriteDeprecatedOperators/ResConfigs"))
    val processor = rewriteDeprecatedOperatorsRefactoringProcessor(project, GradleVersion.parse("4.2.0"), GradleVersion.parse("9.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RewriteDeprecatedOperators/ResConfigsExpected"))
  }

  // TODO(b/205806471) @Test
  fun testTargetSdkVersion() {
    writeToBuildFile(TestFileName("RewriteDeprecatedOperators/TargetSdkVersion"))
    val processor = rewriteDeprecatedOperatorsRefactoringProcessor(project, GradleVersion.parse("4.2.0"), GradleVersion.parse("9.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RewriteDeprecatedOperators/TargetSdkVersionExpected"))
  }

  // TODO(b/205806471) @Test
  fun testTargetSdkVersionPreview() {
    writeToBuildFile(TestFileName("RewriteDeprecatedOperators/TargetSdkVersionPreview"))
    val processor = rewriteDeprecatedOperatorsRefactoringProcessor(project, GradleVersion.parse("4.2.0"), GradleVersion.parse("9.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RewriteDeprecatedOperators/TargetSdkVersionPreviewExpected"))
  }

  @Test
  fun testAlphabeticalOrder() {
    writeToBuildFile(TestFileName("RewriteDeprecatedOperators/AlphabeticalOrder"))
    val processor = rewriteDeprecatedOperatorsRefactoringProcessor(project, GradleVersion.parse("4.2.0"), GradleVersion.parse("9.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RewriteDeprecatedOperators/AlphabeticalOrderExpected"))
  }

  @Test
  fun testReverseAlphabeticalOrder() {
    writeToBuildFile(TestFileName("RewriteDeprecatedOperators/ReverseAlphabeticalOrder"))
    val processor = rewriteDeprecatedOperatorsRefactoringProcessor(project, GradleVersion.parse("4.2.0"), GradleVersion.parse("9.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RewriteDeprecatedOperators/ReverseAlphabeticalOrderExpected"))
  }
}