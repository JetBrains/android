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
import com.android.tools.deployer.model.component.AppComponent
import com.android.tools.idea.run.configuration.AndroidConfigurationProgramRunner
import com.android.tools.idea.run.configuration.AndroidTileConfiguration
import com.android.tools.idea.run.configuration.AndroidTileConfigurationType
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import kotlin.test.assertFailsWith

class AndroidTileConfigurationExecutorTest : AndroidConfigurationExecutorBaseTest() {

  private fun getExecutionEnvironment(executorInstance: Executor): ExecutionEnvironment {
    val configSettings = RunManager.getInstance(project).createConfiguration(
      "run Tile", AndroidTileConfigurationType().configurationFactories.single())
    val androidTileConfiguration = configSettings.configuration as AndroidTileConfiguration
    androidTileConfiguration.setModule(myModule)
    androidTileConfiguration.componentName = componentName
    return ExecutionEnvironment(executorInstance, AndroidConfigurationProgramRunner(), configSettings, project)
  }

  fun testRun() {
    // Use DefaultRunExecutor, equivalent of pressing run button.
    val env = getExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance())

    val executor = Mockito.spy(AndroidTileConfigurationExecutor(env))

    val device = getMockDevice { request ->
      when (request) {
        "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation 'add-tile' --ecn component com.example.app/com.example.app.Component" ->
          "Broadcast completed: result=1, Index=[1]"
        else -> "Unknown request: $request"
      }
    }

    val app = createApp(device, appId, servicesName = listOf(componentName), activitiesName = emptyList())
    val appInstaller = TestApplicationInstaller(appId, app)
    // Mock app installation.
    Mockito.doReturn(appInstaller).`when`(executor).getApplicationInstaller()

    // Mock console.
    val console: ConsoleView = Mockito.mock(ConsoleView::class.java)
    Mockito.doReturn(console).`when`(executor).createConsole()

    executor.doOnDevices(listOf(device))

    // Verify commands sent to device.
    val commandsCaptor = ArgumentCaptor.forClass(String::class.java)
    Mockito.verify(device, Mockito.times(2)).executeShellCommand(
      commandsCaptor.capture(),
      MockitoKt.any(IShellOutputReceiver::class.java),
      MockitoKt.any(),
      MockitoKt.any()
    )
    val commands = commandsCaptor.allValues

    // Set Tile.
    assertThat(commands[0]).isEqualTo(
      "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation 'add-tile' --ecn component com.example.app/com.example.app.Component")
    // Showing Tile.
    assertThat(commands[1]).isEqualTo("am broadcast -a com.google.android.wearable.app.DEBUG_SYSUI --es operation show-tile --ei index 1")

    // Verify that a warning was raised.
    Mockito.verify(console, Mockito.times(1)).printError("Warning: Launch was successful, but you may need to bring up the tile manually.")
  }

  fun testException() {
    // Use DefaultRunExecutor, equivalent of pressing run button.
    val env = getExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance())

    val executor = Mockito.spy(AndroidTileConfigurationExecutor(env))

    val response = "Broadcast completed: result=2, data=\"Internal failure.\"\n" +
                   "End of output."
    val device = getMockDevice { request ->
      when (request) {
        "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation 'add-tile' --ecn component com.example.app/com.example.app.Component" -> response
        else -> "Unknown request: $request"
      }
    }

    val app = createApp(device, appId, servicesName = listOf(componentName), activitiesName = emptyList())
    val appInstaller = TestApplicationInstaller(appId, app) // Mock app installation.
    Mockito.doReturn(appInstaller).`when`(executor).getApplicationInstaller()

    val e = assertFailsWith<ExecutionException> { executor.doOnDevices(listOf(device)) }
    assertThat(e).hasMessageThat().contains("Error while setting the tile, message: $response")
  }

  fun testDebug() {
    // Use DefaultRunExecutor, equivalent of pressing debug button.
    val env = getExecutionEnvironment(DefaultDebugExecutor.getDebugExecutorInstance())

    // Executor we test.
    val executor = Mockito.spy(AndroidTileConfigurationExecutor(env))

    val device = getMockDevice { request ->
      when (request) {
        // Test TileIndexReceiver
        "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation 'add-tile' --ecn component com.example.app/com.example.app.Component" ->
          "Broadcast completed: result=1, Index=[101]"
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
    Mockito.verify(device, Mockito.times(3)).executeShellCommand(
      commandsCaptor.capture(),
      MockitoKt.any(IShellOutputReceiver::class.java),
      MockitoKt.any(),
      MockitoKt.any()
    )
    val commands = commandsCaptor.allValues

    // Set debug app.
    assertThat(commands[0]).isEqualTo("am set-debug-app -w 'com.example.app'")
    // Set Tile.
    assertThat(commands[1]).isEqualTo(
      "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation 'add-tile' --ecn component com.example.app/com.example.app.Component")
    // Showing Tile.
    assertThat(commands[2]).isEqualTo("am broadcast -a com.google.android.wearable.app.DEBUG_SYSUI --es operation show-tile --ei index 101")
  }

  fun testTileProcessHandler() {
    val processHandler = TileProcessHandler(AppComponent.getFQEscapedName(appId, componentName),
                                            Mockito.mock(ConsoleView::class.java))
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

    // Unset tile
    assertThat(commands[0]).isEqualTo(
      "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation remove-tile --ecn component com.example.app/com.example.app.Component")
  }
}
