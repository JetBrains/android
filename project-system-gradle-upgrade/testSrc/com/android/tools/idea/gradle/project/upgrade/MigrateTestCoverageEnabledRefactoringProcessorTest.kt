/*
 * Copyright (C) 2024 The Android Open Source Project
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
import com.google.common.truth.Expect
import com.intellij.openapi.project.Project
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class MigrateTestCoverageEnabledRefactoringProcessorTest : UpgradeGradleFileModelTestCase() {
  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  private fun createProcessor(project: Project,
                              current: AgpVersion,
                              new: AgpVersion) = MIGRATE_TEST_COVERAGE_ENABLED_TO_UNIT_AND_ANDROID_COVERAGE.RefactoringProcessor(project,
                                                                                                                                 current,
                                                                                                                                 new)

  @Test
  fun testNecessities() {
    val expectedNecessitiesMap = mapOf(("3.5.0" to "7.2.0") to AgpUpgradeComponentNecessity.IRRELEVANT_FUTURE,
                                       ("7.2.0" to "7.3.0") to AgpUpgradeComponentNecessity.OPTIONAL_CODEPENDENT,
                                       ("7.3.0" to "8.2.0") to AgpUpgradeComponentNecessity.OPTIONAL_INDEPENDENT,
                                       ("8.2.0" to "9.0.0") to AgpUpgradeComponentNecessity.MANDATORY_INDEPENDENT,
                                       ("3.5.0" to "9.1.0") to AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT,
                                       ("9.0.0" to "9.1.0") to AgpUpgradeComponentNecessity.IRRELEVANT_PAST,
                                       )
    expectedNecessitiesMap.forEach { (t, u) ->
      val processor = createProcessor(project, AgpVersion.parse(t.first), AgpVersion.parse(t.second))
      expect.that(processor.necessity()).isEqualTo(u)
    }
  }

  @Test
  fun testLiteral() {
    verifyWitExpectedFile("MigrateTestCoverageEnabled/Literal")
  }

  @Test
  fun testReference() {
    verifyWitExpectedFile("MigrateTestCoverageEnabled/Reference")
  }

  private fun verifyWitExpectedFile(projectPath: String) {
    writeToBuildFile(TestFileName(projectPath))
    createProcessor(project, AgpVersion.parse("7.2.0"), AgpVersion.parse("7.3.0")).run()
    verifyFileContents(buildFile, TestFileName("${projectPath}Expected"))
  }
}