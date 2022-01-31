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
import com.android.tools.idea.run.configuration.AndroidConfigurationProgramRunner
import com.android.tools.idea.run.configuration.AndroidWatchFaceConfiguration
import com.android.tools.idea.run.configuration.AndroidWatchFaceConfigurationType
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.Executor
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import org.mockito.ArgumentCaptor
import org.mockito.Mockito

class AndroidWatchFaceConfigurationExecutorTest : AndroidConfigurationExecutorBaseTest() {

  private fun getExecutionEnvironment(executorInstance: Executor): ExecutionEnvironment {
    val configSettings = RunManager.getInstance(project).createConfiguration(
      "run WatchFace", AndroidWatchFaceConfigurationType().configurationFactories.single())
    val androidWatchFaceConfiguration = configSettings.configuration as AndroidWatchFaceConfiguration
    androidWatchFaceConfiguration.setModule(myModule)
    androidWatchFaceConfiguration.componentName = componentName
    // Use debug executor
    return ExecutionEnvironment(executorInstance, AndroidConfigurationProgramRunner(), configSettings, project)
  }

  fun testRun() {
    // Use DefaultRunExecutor, equivalent of pressing run button.
    val env = getExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance())

    val executor = Mockito.spy(AndroidWatchFaceConfigurationExecutor(env))

    val device = getMockDevice { request: String ->
      when {
        request.contains("DEBUG_SURFACE --es operation version") ->
          "Broadcasting: Intent { act=com.google.android.wearable.app.DEBUG_SURFACE flg=0x400000 (has extras) }\n" +
          "Broadcast completed: result=1, data=\"3\""
        request.contains("DEBUG_SURFACE --es operation set-watchface") ->
          "Broadcasting: Intent { act=com.google.android.wearable.app.DEBUG_SURFACE flg=0x400000 (has extras) }\n" +
          "Broadcast completed: result=1, data=\"Favorite Id=[2] Runtime=[1]\""
        else -> "Unknown request: $request"
      }
    }
    val app = createApp(device, appId, servicesName = listOf(componentName), activitiesName = emptyList())
    val appInstaller = TestApplicationInstaller(appId, app)
    // Mock app installation.
    Mockito.doReturn(appInstaller).`when`(executor).getApplicationInstaller()

    executor.doOnDevices(listOf(device))

    // Verify commands sent to device.
    val commandsCaptor = ArgumentCaptor.forClass(String::class.java)
    Mockito.verify(device, Mockito.times(3)).executeShellCommand(
      commandsCaptor.capture(),
      MockitoKt.any(IShellOutputReceiver::class.java),
      MockitoKt.any(),
      MockitoKt.any()
    )
    val commands = commandsCaptor.allValues


    // check WatchFace API version.
    assertThat(commands[0]).isEqualTo("am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation version")
    // Set WatchFace.
    assertThat(commands[1]).isEqualTo("am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation set-watchface --ecn component com.example.app/com.example.app.Component")
    // Showing WatchFace.
    assertThat(commands[2]).isEqualTo("am broadcast -a com.google.android.wearable.app.DEBUG_SYSUI --es operation show-watchface")
  }

  fun testDebug() {
    // Use DefaultRunExecutor, equivalent of pressing debug button.
    val env = getExecutionEnvironment(DefaultDebugExecutor.getDebugExecutorInstance())

    // Executor we test.
    val executor = Mockito.spy(AndroidWatchFaceConfigurationExecutor(env))

    val device = getMockDevice { request: String ->
      when {
        request.contains("DEBUG_SURFACE --es operation version") ->
          "Broadcasting: Intent { act=com.google.android.wearable.app.DEBUG_SURFACE flg=0x400000 (has extras) }\n" +
          "Broadcast completed: result=1, data=\"3\""
        request.contains("DEBUG_SURFACE --es operation set-watchface") ->
          "Broadcasting: Intent { act=com.google.android.wearable.app.DEBUG_SURFACE flg=0x400000 (has extras) }\n" +
          "Broadcast completed: result=1, data=\"Favorite Id=[2] Runtime=[1]\""
        else -> "Unknown request: $request"
      }
    }
    val app = createApp(device, appId, servicesName = listOf(componentName), activitiesName = emptyList())
    val appInstaller = TestApplicationInstaller(appId, app)
    // Mock app installation.
    Mockito.doReturn(appInstaller).`when`(executor).getApplicationInstaller()
    // Mock debugSessionStarter.
    Mockito.doReturn(Mockito.mock(DebugSessionStarter::class.java)).`when`(executor).getDebugSessionStarter()

    executor.doOnDevices(listOf(device))

    // Verify commands sent to device.
    val commandsCaptor = ArgumentCaptor.forClass(String::class.java)
    Mockito.verify(device, Mockito.times(4)).executeShellCommand(
      commandsCaptor.capture(),
      MockitoKt.any(IShellOutputReceiver::class.java),
      MockitoKt.any(),
      MockitoKt.any()
    )
    val commands = commandsCaptor.allValues

    // check WatchFace API version.
    assertThat(commands[0]).isEqualTo("am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation version")
    // Set debug app.
    assertThat(commands[1]).isEqualTo("am set-debug-app -w 'com.example.app'")
    // Set WatchFace.
    assertThat(commands[2]).isEqualTo(
      "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation set-watchface --ecn component com.example.app/com.example.app.Component")
    // Showing WatchFace.
    assertThat(commands[3]).isEqualTo("am broadcast -a com.google.android.wearable.app.DEBUG_SYSUI --es operation show-watchface")
  }

  fun testWatchFaceProcessHandler() {
    val processHandler = WatchFaceProcessHandler(Mockito.mock(ConsoleView::class.java))
    val device = getMockDevice()
    processHandler.addDevice(device)

    processHandler.startNotify()

    processHandler.destroyProcess()

    // Verify commands sent to device.
    val commandsCaptor = ArgumentCaptor.forClass(String::class.java)
    Mockito.verify(device, Mockito.times(1)).executeShellCommand(
      commandsCaptor.capture(),
      MockitoKt.any(IShellOutputReceiver::class.java),
      MockitoKt.any(),
      MockitoKt.any()
    )
    val commands = commandsCaptor.allValues

    // Unset watch face
    assertThat(commands[0]).isEqualTo("am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation unset-watchface")
  }
}