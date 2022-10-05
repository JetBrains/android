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
class RemoveSourceSetJniRefactoringProcessorTest: UpgradeGradleFileModelTestCase() {
  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  fun RemoveSourceSetJniRefactoringProcessor(project: Project, current: AgpVersion, new: AgpVersion) =
    REMOVE_SOURCE_SET_JNI_INFO.RefactoringProcessor(project, current, new)

  @Test
  fun testNecessities() {
    val expectedNecessitiesMap = mapOf(
      ("3.5.0" to "3.6.0") to IRRELEVANT_FUTURE,
      ("3.6.0" to "7.0.0-alpha10") to OPTIONAL_CODEPENDENT,
      ("7.0.0-alpha07" to "7.0.0-alpha10") to OPTIONAL_INDEPENDENT,
      ("7.0.0-alpha07" to "8.0.0") to MANDATORY_INDEPENDENT,
      ("3.6.0" to "8.0.0") to MANDATORY_CODEPENDENT,
      ("8.0.0" to "8.1.0") to IRRELEVANT_PAST
    )
    expectedNecessitiesMap.forEach { (t, u) ->
      val processor = RemoveSourceSetJniRefactoringProcessor(project, AgpVersion.parse(t.first), AgpVersion.parse(t.second))
      expect.that(processor.necessity()).isEqualTo(u)
    }
  }

  @Test
  fun testRemoveJniSingleBlock() {
    writeToBuildFile(TestFileName("RemoveSourceSetJni/SingleBlock"))
    val processor = RemoveSourceSetJniRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RemoveSourceSetJni/SingleBlockExpected"))
  }

  @Test
  fun testRemoveJniSingleStatement() {
    writeToBuildFile(TestFileName("RemoveSourceSetJni/SingleStatement"))
    val processor = RemoveSourceSetJniRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RemoveSourceSetJni/SingleStatementExpected"))
  }

  @Test
  fun testRemoveJniBlockAndStatement() {
    writeToBuildFile(TestFileName("RemoveSourceSetJni/BlockAndStatement"))
    val processor = RemoveSourceSetJniRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RemoveSourceSetJni/BlockAndStatementExpected"))
  }

  @Test
  fun testRemoveJniStatementAndBlock() {
    writeToBuildFile(TestFileName("RemoveSourceSetJni/StatementAndBlock"))
    val processor = RemoveSourceSetJniRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.0.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("RemoveSourceSetJni/StatementAndBlockExpected"))
  }

}