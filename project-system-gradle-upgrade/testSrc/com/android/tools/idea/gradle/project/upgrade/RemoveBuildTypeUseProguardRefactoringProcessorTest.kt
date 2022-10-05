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
import com.google.common.truth.Expect
import com.intellij.openapi.project.Project
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class RemoveBuildTypeUseProguardRefactoringProcessorTest : UpgradeGradleFileModelTestCase() {
  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  fun RemoveBuildTypeUseProguardRefactoringProcessor(project: Project, current: AgpVersion, new: AgpVersion) =
    REMOVE_BUILD_TYPE_USE_PROGUARD_INFO.RefactoringProcessor(project, current, new)

  @Test
  fun testNecessities() {
    val expectedNecessitiesMap = mapOf(
      ("3.3.0" to "3.4.0") to AgpUpgradeComponentNecessity.IRRELEVANT_FUTURE,
      ("3.3.0" to "3.5.0") to AgpUpgradeComponentNecessity.OPTIONAL_CODEPENDENT,
      ("3.6.0" to "4.2.0") to AgpUpgradeComponentNecessity.OPTIONAL_INDEPENDENT,
      ("4.2.0" to "7.0.0") to AgpUpgradeComponentNecessity.MANDATORY_INDEPENDENT,
      ("3.3.0" to "7.0.0") to AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT,
      ("7.0.0" to "7.1.0") to AgpUpgradeComponentNecessity.IRRELEVANT_PAST
    )
    expectedNecessitiesMap.forEach { (t, u) ->
      val processor = RemoveBuildTypeUseProguardRefactoringProcessor(project, AgpVersion.parse(t.first), AgpVersion.parse(t.second))
      expect.that(processor.necessity()).isEqualTo(u)
    }
  }

  @Test
  fun testRemoveUseProguardOneBuildType() {
    writeToBuildFile(TestFileName("RemoveBuildTypeUseProguard/OneBuildType"))
    val processor = RemoveBuildTypeUseProguardRefactoringProcessor(project, AgpVersion.parse("4.2.0"), AgpVersion.parse("7.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RemoveBuildTypeUseProguard/OneBuildTypeExpected"))
  }

  @Test
  fun testRemoveUseProguardTwoBuildTypes() {
    writeToBuildFile(TestFileName("RemoveBuildTypeUseProguard/TwoBuildTypes"))
    val processor = RemoveBuildTypeUseProguardRefactoringProcessor(project, AgpVersion.parse("4.2.0"), AgpVersion.parse("7.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RemoveBuildTypeUseProguard/TwoBuildTypesExpected"))
  }
}