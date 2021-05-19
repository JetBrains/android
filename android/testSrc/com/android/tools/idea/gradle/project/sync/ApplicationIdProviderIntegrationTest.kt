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
package com.android.tools.idea.gradle.project.sync

import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.RunManager
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.io.File

class ApplicationIdProviderIntegrationTest : GradleIntegrationTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels()

  @get:Rule
  var testName = TestName()

  @Test
  fun testApplicationIdBeforeBuild() {
    prepareGradleProject(TestProjectPaths.APPLICATION_ID_SUFFIX, "project")
    openPreparedProject("project") { project ->
      val runConfiguration = RunManager.getInstance(project).allConfigurationsList.filterIsInstance<AndroidRunConfiguration>().single()
      val applicationId = project.getProjectSystem().getApplicationIdProvider(runConfiguration)?.packageName
      // Falls back to package name since build is never run.
      assertThat(applicationId).isEqualTo("one.name")
    }
  }

  @Test
  fun testApplicationIdAfterBuild() {
    prepareGradleProject(TestProjectPaths.APPLICATION_ID_SUFFIX, "project")
    openPreparedProject("project") { project ->
      val runConfiguration = RunManager.getInstance(project).allConfigurationsList.filterIsInstance<AndroidRunConfiguration>().single()
      runConfiguration.executeMakeBeforeRunStepInTest()
      val applicationId = project.getProjectSystem().getApplicationIdProvider(runConfiguration)?.packageName
      assertThat(applicationId).isEqualTo("one.name.debug")
    }
  }

  override fun getName(): String = testName.methodName
  override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath
  override fun getTestDataDirectoryAdtIdeaRelativePath(): String = TestProjectPaths.TEST_DATA_PATH
  override fun getAdditionalRepos(): Collection<File> = listOf()
}
