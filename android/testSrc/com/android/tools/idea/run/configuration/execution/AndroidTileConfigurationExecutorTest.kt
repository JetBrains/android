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
import com.android.testutils.MockitoKt.any
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith

class AndroidTileConfigurationExecutorTest : AndroidConfigurationExecutorBaseTest() {

  //Expected commands
  private val checkVersion = "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation version"
  private val addTile = "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation 'add-tile' --ecn component com.example.app/com.example.app.Component"
  private val showTile = "am broadcast -a com.google.android.wearable.app.DEBUG_SYSUI --es operation show-tile --ei index 101"
  private val setDebugAppAm = "am set-debug-app -w 'com.example.app'"
  private val setDebugAppBroadcast = "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation set-debug-app --ecn component 'com.example.app'"
  private val removeTile = "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation remove-tile --ecn component com.example.app/com.example.app.Component"

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

    val device = getMockDevice(mapOf(
      checkVersion to "Broadcast completed: result=1, data=\"3\"",
      addTile to "Broadcast completed: result=1, Index=[101]",
      // Unsuccessful execution of show tile.
      showTile to "Broadcast completed: result=2"
    ).toCommandHandlers())

    val app = createApp(device, appId, servicesName = listOf(componentName), activitiesName = emptyList())
    val appInstaller = TestApplicationInstaller(appId, app)
    // Mock app installation.
    Mockito.doReturn(appInstaller).`when`(executor).getApplicationInstaller(any())

    // Mock console.
    val console: ConsoleView = Mockito.mock(ConsoleView::class.java)
    Mockito.doReturn(console).`when`(executor).createConsole()

    executor.doOnDevices(listOf(device))

    // Verify commands sent to device.
    val commandsCaptor = ArgumentCaptor.forClass(String::class.java)
    Mockito.verify(device, Mockito.times(3)).executeShellCommand(
      commandsCaptor.capture(),
      any(IShellOutputReceiver::class.java),
      any(),
      any()
    )
    val commands = commandsCaptor.allValues

    // Check version
    assertThat(commands[0]).isEqualTo(checkVersion)
    // Set Tile.
    assertThat(commands[1]).isEqualTo(addTile)
    // Showing Tile.
    assertThat(commands[2]).isEqualTo(showTile)

    // Verify that a warning was raised.
    Mockito.verify(console, Mockito.times(1)).printError("Warning: Launch was successful, but you may need to bring up the tile manually.")
  }

  fun testException() {
    // Use DefaultRunExecutor, equivalent of pressing run button.
    val env = getExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance())

    val executor = Mockito.spy(AndroidTileConfigurationExecutor(env))

    val failedResponse = "Broadcast completed: result=2, data=\"Internal failure.\"\n" +
                         "End of output."

    val device = getMockDevice(mapOf(
      checkVersion to "Broadcast completed: result=1, data=\"3\"",
      addTile to failedResponse
    ).toCommandHandlers())

    val app = createApp(device, appId, servicesName = listOf(componentName), activitiesName = emptyList())
    val appInstaller = TestApplicationInstaller(appId, app) // Mock app installation.
    Mockito.doReturn(appInstaller).`when`(executor).getApplicationInstaller(any())

    val e = assertFailsWith<ExecutionException> { executor.doOnDevices(listOf(device)) }
    assertThat(e).hasMessageThat().contains("Error while setting the tile, message: $failedResponse")
  }

  fun testDebug() {
    // Use DefaultRunExecutor, equivalent of pressing debug button.
    val env = getExecutionEnvironment(DefaultDebugExecutor.getDebugExecutorInstance())

    // Executor we test.
    val executor = Mockito.spy(AndroidTileConfigurationExecutor(env))

    val device = getMockDevice(mapOf(
      checkVersion to "Broadcast completed: result=1, data=\"3\"",
      addTile to "Broadcast completed: result=1, Index=[101]",
      showTile to "Broadcast completed: result=1",
      setDebugAppBroadcast to "Broadcast completed: result=2, data=\"Failed to set up the debug app\""
    ).toCommandHandlers())

    val app = createApp(device, appId, servicesName = listOf(componentName), activitiesName = emptyList())
    val appInstaller = TestApplicationInstaller(appId, app)
    // Mock app installation.
    Mockito.doReturn(appInstaller).`when`(executor).getApplicationInstaller(any())
    // Mock debugSessionStarter.
    Mockito.doReturn(Mockito.mock(DebugSessionStarter::class.java)).`when`(executor).getDebugSessionStarter()

    executor.doOnDevices(listOf(device))

    // Verify commands sent to device.
    val commandsCaptor = ArgumentCaptor.forClass(String::class.java)
    Mockito.verify(device, Mockito.times(5)).executeShellCommand(
      commandsCaptor.capture(),
      any(IShellOutputReceiver::class.java),
      any(),
      any()
    )
    val commands = commandsCaptor.allValues

    // Check version
    assertThat(commands[0]).isEqualTo(checkVersion)
    // Set debug app.
    assertThat(commands[1]).isEqualTo(setDebugAppAm)
    assertThat(commands[2]).isEqualTo(setDebugAppBroadcast)
    // Set Tile.
    assertThat(commands[3]).isEqualTo(addTile)
    // Showing Tile.
    assertThat(commands[4]).isEqualTo(showTile)
  }

  fun testTileProcessHandler() {
    val processHandler = TileProcessHandler(AppComponent.getFQEscapedName(appId, componentName),
                                            Mockito.mock(ConsoleView::class.java))
    val countDownLatch = CountDownLatch(1)
    val device = getMockDevice(mapOf(
      removeTile to { _, _ -> countDownLatch.countDown() }
    ))
    processHandler.addDevice(device)

    processHandler.startNotify()

    processHandler.destroyProcess()

    assertThat(countDownLatch.await(3, TimeUnit.SECONDS)).isTrue()

    // Verify commands sent to device.
    val commandsCaptor = ArgumentCaptor.forClass(String::class.java)
    Mockito.verify(device, Mockito.times(1)).executeShellCommand(
      commandsCaptor.capture(),
      any(IShellOutputReceiver::class.java),
      any(),
      any()
    )
    val commands = commandsCaptor.allValues

    // Unset tile
    assertThat(commands[0]).isEqualTo(removeTile)
  }
}
