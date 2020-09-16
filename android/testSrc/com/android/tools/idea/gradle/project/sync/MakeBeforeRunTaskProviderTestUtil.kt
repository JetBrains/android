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
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.deployment.DeviceAndSnapshotExecutionTargetProvider
import com.google.common.truth.Truth
import com.intellij.execution.ExecutionTarget
import com.intellij.execution.ExecutionTargetManager
import com.intellij.execution.ExecutionTargetProvider
import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerEx
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import kotlin.test.assertEquals

fun AndroidRunConfiguration.executeMakeBeforeRunStepInTest() {
  val project = project
  val makeBeforeRunTask = beforeRunTasks.filterIsInstance<MakeBeforeRunTask>().single()
  val factory = factory!!
  val runnerAndConfigurationSettings = RunManager.getInstance(project).createConfiguration(this, factory)

  // Set up ExecutionTarget infrastructure.
  ApplicationManager.getApplication().invokeAndWait {
    val provider = ExecutionTargetProvider.EXTENSION_NAME.extensionList.find { it is DeviceAndSnapshotExecutionTargetProvider }
    val settings = RunManagerEx.getInstanceEx(this.project).selectedConfiguration
    val configuration: RunConfiguration = settings!!.configuration
    val targets: List<ExecutionTarget> = provider!!.getTargets(this.project, configuration)
    assertEquals(1, targets.size)
    ExecutionTargetManager.getInstance(this.project).activeTarget = targets[0]
  }

  Truth.assertThat(
    MakeBeforeRunTaskProvider.getProvider(project, MakeBeforeRunTaskProvider.ID)!!
      .executeTask(
        DataContext.EMPTY_CONTEXT,
        this,
        ExecutionEnvironment(
          DefaultRunExecutor.getRunExecutorInstance(),
          ProgramRunner.getRunner(DefaultRunExecutor.EXECUTOR_ID, this)!!,
          runnerAndConfigurationSettings,
          project
        ),
        makeBeforeRunTask
      )
  ).isTrue()
}
