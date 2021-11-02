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
package com.android.tools.idea.run.configuration.execution

import com.android.ddmlib.IShellOutputReceiver
import com.android.testutils.MockitoKt
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.AndroidRunConfigurationType
import com.android.tools.idea.run.configuration.AndroidConfigurationProgramRunner
import com.google.common.truth.Truth
import com.intellij.execution.Executor
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import org.mockito.ArgumentCaptor
import org.mockito.Mockito

internal class AndroidActivityConfigurationExecutorTest : AndroidConfigurationExecutorBaseTest() {
  private fun getExecutionEnvironment(executorInstance: Executor): ExecutionEnvironment {
    val configSettings = RunManager.getInstance(project).createConfiguration("run App", AndroidRunConfigurationType().factory)
    val androidTileConfiguration = configSettings.configuration as AndroidRunConfiguration
    androidTileConfiguration.setModule(myModule)
    androidTileConfiguration.setLaunchActivity(componentName)
    return ExecutionEnvironment(executorInstance, AndroidConfigurationProgramRunner(), configSettings, project)
  }

  fun testRun() {
    // Use DefaultRunExecutor, equivalent of pressing run button.
    val env = getExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance())

    (env.runProfile as AndroidRunConfiguration).ACTIVITY_EXTRA_FLAGS = "--user 123"

    val executor = Mockito.spy(AndroidActivityConfigurationExecutor(env))
    val device = getMockDevice()

    val app = createApp(device, appId, servicesName = listOf(), activitiesName = listOf(componentName))
    val appInstaller = TestApplicationInstaller(appId, app)
    // Mock app installation.
    Mockito.doReturn(appInstaller).`when`(executor).getApplicationInstaller()

    executor.doOnDevices(listOf(device))

    // Verify commands sent to device.
    val commandsCaptor = ArgumentCaptor.forClass(String::class.java)
    Mockito.verify(device, Mockito.times(1)).executeShellCommand(
      commandsCaptor.capture(),
      MockitoKt.any(IShellOutputReceiver::class.java),
      MockitoKt.any(),
      MockitoKt.any()
    )
    val commands = commandsCaptor.allValues

    // Start activity.
    Truth.assertThat(commands[0]).isEqualTo(
      "am start -n com.example.app/com.example.app.Component -a android.intent.action.MAIN -c android.intent.category.LAUNCHER --user 123")
  }

  fun testDebug() {
    // Use DefaultRunExecutor, equivalent of pressing debug button.
    val env = getExecutionEnvironment(DefaultDebugExecutor.getDebugExecutorInstance())

    // Executor we test.
    val executor = Mockito.spy(AndroidActivityConfigurationExecutor(env))

    val device = getMockDevice()

    val app = createApp(device, appId, servicesName = listOf(), activitiesName = listOf(componentName))
    val appInstaller = TestApplicationInstaller(appId, app)
    // Mock app installation.
    Mockito.doReturn(appInstaller).`when`(executor).getApplicationInstaller()
    // Mock debugSessionStarter.
    Mockito.doReturn(Mockito.mock(DebugSessionStarter::class.java)).`when`(executor).getDebugSessionStarter()

    executor.doOnDevices(listOf(device))

    // Verify commands sent to device.
    val commandsCaptor = ArgumentCaptor.forClass(String::class.java)
    Mockito.verify(device, Mockito.times(1)).executeShellCommand(
      commandsCaptor.capture(),
      MockitoKt.any(IShellOutputReceiver::class.java),
      MockitoKt.any(),
      MockitoKt.any()
    )
    val commands = commandsCaptor.allValues

    // Start Activity with -D flag.
    Truth.assertThat(commands[0]).isEqualTo(
      "am start -n com.example.app/com.example.app.Component -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -D")
  }
}