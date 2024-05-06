/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.run.configuration

import com.android.tools.idea.execution.common.DeployableToDevice
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.project.Project
import org.junit.Rule
import org.junit.Test

class AndroidTileConfigurationTest {

  @get:Rule
  var androidProjectRule = AndroidProjectRule.inMemory().onEdt()

  val project: Project
    get() = androidProjectRule.project

  @Test
  fun testProgramRunnerAvailable() {
    val configSettings = RunManager.getInstance(project).createConfiguration(
      "run tile", AndroidTileConfigurationType().configurationFactories.single())

    val runnerForRun = ProgramRunner.getRunner(DefaultRunExecutor.EXECUTOR_ID, configSettings.configuration)
    assertThat(runnerForRun).isNotNull()

    val runnerForDebug = ProgramRunner.getRunner(DefaultDebugExecutor.EXECUTOR_ID, configSettings.configuration)
    assertThat(runnerForDebug).isNotNull()
  }

  @Test
  fun testDeploysToLocalDevice() {
    val configSettings = RunManager.getInstance(project).createConfiguration(
      "run tile", AndroidTileConfigurationType().configurationFactories.single())

    assertThat(DeployableToDevice.deploysToLocalDevice(configSettings.configuration)).isTrue()
  }
}