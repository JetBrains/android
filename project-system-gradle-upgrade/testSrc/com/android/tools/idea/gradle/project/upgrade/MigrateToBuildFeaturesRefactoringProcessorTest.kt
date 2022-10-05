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

import com.android.ide.common.repository.GradleVersion.AgpVersion
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.*
import com.google.common.truth.Expect
import com.intellij.openapi.project.Project
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class MigrateToBuildFeaturesRefactoringProcessorTest: UpgradeGradleFileModelTestCase() {
  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  fun MigrateToBuildFeaturesRefactoringProcessor(project: Project, current: AgpVersion, new: AgpVersion) =
    MIGRATE_TO_BUILD_FEATURES_INFO.RefactoringProcessor(project, current, new)

  @Test
  fun testNecessities() {
    val expectedNecessitiesMap = mapOf(
      ("3.5.0" to "3.6.0") to IRRELEVANT_FUTURE,
      ("3.6.0" to "4.0.0") to OPTIONAL_CODEPENDENT,
      ("4.0.0" to "4.1.0") to OPTIONAL_INDEPENDENT,
      ("4.1.0" to "7.0.0") to MANDATORY_INDEPENDENT,
      ("3.6.0" to "7.0.0") to MANDATORY_CODEPENDENT,
      ("7.0.0" to "7.1.0") to IRRELEVANT_PAST
    )
    expectedNecessitiesMap.forEach { (t, u) ->
      val processor = MigrateToBuildFeaturesRefactoringProcessor(project, AgpVersion.parse(t.first), AgpVersion.parse(t.second))
      expect.that(processor.necessity()).isEqualTo(u)
    }
  }

  @Test
  fun testViewBindingEnabledLiteral() {
    writeToBuildFile(TestFileName("MigrateToBuildFeatures/ViewBindingEnabledLiteral"))
    val processor = MigrateToBuildFeaturesRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersion.parse("7.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("MigrateToBuildFeatures/ViewBindingEnabledLiteralExpected"))
  }

  @Test
  fun testViewBindingEnabledReference() {
    writeToBuildFile(TestFileName("MigrateToBuildFeatures/ViewBindingEnabledReference"))
    val processor = MigrateToBuildFeaturesRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersion.parse("7.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("MigrateToBuildFeatures/ViewBindingEnabledReferenceExpected"))
  }

  @Test
  fun testDataBindingEnabledLiteral() {
    writeToBuildFile(TestFileName("MigrateToBuildFeatures/DataBindingEnabledLiteral"))
    val processor = MigrateToBuildFeaturesRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersion.parse("7.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("MigrateToBuildFeatures/DataBindingEnabledLiteralExpected"))
  }

  @Test
  fun testDataBindingEnabledReference() {
    writeToBuildFile(TestFileName("MigrateToBuildFeatures/DataBindingEnabledReference"))
    val processor = MigrateToBuildFeaturesRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersion.parse("7.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("MigrateToBuildFeatures/DataBindingEnabledReferenceExpected"))
  }

  @Test
  fun testBothEnabledLiterals() {
    writeToBuildFile(TestFileName("MigrateToBuildFeatures/BothEnabledLiterals"))
    val processor = MigrateToBuildFeaturesRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersion.parse("7.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("MigrateToBuildFeatures/BothEnabledLiteralsExpected"))
  }

  @Test
  fun testBothEnabledReference() {
    writeToBuildFile(TestFileName("MigrateToBuildFeatures/BothEnabledReference"))
    val processor = MigrateToBuildFeaturesRefactoringProcessor(project, AgpVersion.parse("4.1.0"), AgpVersion.parse("7.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("MigrateToBuildFeatures/BothEnabledReferenceExpected"))
  }
}