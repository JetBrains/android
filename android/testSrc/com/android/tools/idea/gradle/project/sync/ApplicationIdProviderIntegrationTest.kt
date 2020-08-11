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

import com.android.tools.idea.gradle.run.MakeBeforeRunTask
import com.android.tools.idea.gradle.run.MakeBeforeRunTaskProvider
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.deployment.DeviceAndSnapshotExecutionTargetProvider
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.ExecutionTarget
import com.intellij.execution.ExecutionTargetManager
import com.intellij.execution.ExecutionTargetProvider
import com.intellij.execution.RunManager
import com.intellij.execution.RunManager.Companion.getInstance
import com.intellij.execution.RunManagerEx
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.io.File
import kotlin.test.assertEquals

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
      executeMakeBeforeRunStep(runConfiguration)
      val applicationId = project.getProjectSystem().getApplicationIdProvider(runConfiguration)?.packageName
      assertThat(applicationId).isEqualTo("one.name.debug")
    }
  }

  private fun executeMakeBeforeRunStep(runConfiguration: AndroidRunConfiguration) {
    val project = runConfiguration.project
    val makeBeforeRunTask = runConfiguration.beforeRunTasks.filterIsInstance<MakeBeforeRunTask>().single()
    val factory = runConfiguration.factory!!
    val runnerAndConfigurationSettings = getInstance(project).createConfiguration(runConfiguration, factory)

    // Set up ExecutionTarget infrastructure.
    ApplicationManager.getApplication().invokeAndWait {
      val provider = ExecutionTargetProvider.EXTENSION_NAME.extensionList.find { it is DeviceAndSnapshotExecutionTargetProvider }
      val settings = RunManagerEx.getInstanceEx(runConfiguration.project).selectedConfiguration
      val configuration: RunConfiguration = settings!!.configuration
      val targets: List<ExecutionTarget> = provider!!.getTargets(runConfiguration.project, configuration)
      assertEquals(1, targets.size)
      ExecutionTargetManager.getInstance(runConfiguration.project).activeTarget = targets[0]
    }

    assertThat(
      MakeBeforeRunTaskProvider.getProvider(project, MakeBeforeRunTaskProvider.ID)!!
        .executeTask(
          DataContext.EMPTY_CONTEXT,
          runConfiguration,
          ExecutionEnvironment(
            DefaultRunExecutor.getRunExecutorInstance(),
            ProgramRunner.getRunner(DefaultRunExecutor.EXECUTOR_ID, runConfiguration)!!,
            runnerAndConfigurationSettings,
            project
          ),
          makeBeforeRunTask
        )
    ).isTrue()
  }

  override fun getName(): String = testName.methodName
  override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath
  override fun getTestDataDirectoryWorkspaceRelativePath(): String = TestProjectPaths.TEST_DATA_PATH
  override fun getAdditionalRepos(): Collection<File> = listOf()
}
