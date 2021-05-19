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
package com.android.tools.idea.compose.integration

import ANDROIDX_SNAPSHOT_REPO_PATH
import SIMPLE_COMPOSE_PROJECT_PATH
import TEST_DATA_PATH
import com.android.testutils.TestUtils
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.io.File

class StudioComposeBuildCheck : GradleIntegrationTest {
  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels().onEdt()

  @get:Rule
  var testName = TestName()

  /**
   * Checks the rendering of the default `@Preview` in the Compose template.
   */
  @Test
  fun testComposeProject() {
    prepareGradleProject(SIMPLE_COMPOSE_PROJECT_PATH, "SimpleComposeProject")
    openPreparedProject("SimpleComposeProject") {}
  }

  override fun getName(): String = testName.methodName
  override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath
  override fun getTestDataDirectoryWorkspaceRelativePath(): String = TEST_DATA_PATH
  override fun getAdditionalRepos(): Collection<File> = listOf(
    TestUtils.getWorkspaceFile(ANDROIDX_SNAPSHOT_REPO_PATH)
  )

}