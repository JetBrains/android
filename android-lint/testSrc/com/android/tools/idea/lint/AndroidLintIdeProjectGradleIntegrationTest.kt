/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.lint

import com.android.tools.idea.lint.common.LintIdeClient
import com.android.tools.idea.lint.common.LintIgnoredResult
import com.android.tools.idea.lint.common.LintResult
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.module.ModuleManager
import com.intellij.testFramework.RunsInEdt
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.io.File

@RunsInEdt
class AndroidLintIdeProjectGradleIntegrationTest : GradleIntegrationTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels().onEdt()

  @get:Rule
  var expect = Expect.createAndEnableStackTrace()

  @Test
  @Ignore //TODO(b/231836975): Enable and finish when fixed and does not crash anymore.
  fun test() {
    val result: LintResult = LintIgnoredResult()
    prepareGradleProject(TestProjectPaths.TRANSITIVE_DEPENDENCIES, "p")
    openPreparedProject("p") { project ->
      val client: LintIdeClient = AndroidLintIdeClient(project, result)
      val projects = AndroidLintIdeProject.create(client, null, *ModuleManager.getInstance(project).modules)
      assertThat(projects).isNotEmpty()
    }
  }

  override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath
  override fun getTestDataDirectoryWorkspaceRelativePath(): String = TestProjectPaths.TEST_DATA_PATH
  override fun getAdditionalRepos(): Collection<File> = listOf()
}