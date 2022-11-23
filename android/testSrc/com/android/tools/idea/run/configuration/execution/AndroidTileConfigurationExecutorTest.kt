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


import com.android.ddmlib.AndroidDebugBridge
import com.android.fakeadbserver.services.ServiceOutput
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.whenever
import com.android.tools.deployer.DeployerException
import com.android.tools.deployer.model.App
import com.android.tools.deployer.model.component.AppComponent
import com.android.tools.idea.execution.common.AppRunSettings
import com.android.tools.idea.execution.common.DeployOptions
import com.android.tools.idea.execution.common.processhandler.AndroidRemoteDebugProcessHandler
import com.android.tools.idea.run.configuration.AndroidConfigurationProgramRunner
import com.android.tools.idea.run.configuration.AndroidTileConfigurationType
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.invokeLater
import com.intellij.util.ExceptionUtil
import io.ktor.util.reflect.instanceOf
import org.junit.Test
import org.mockito.Mockito
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith
import kotlin.test.fail

class AndroidTileConfigurationExecutorTest : AndroidConfigurationExecutorBaseTest() {

  //Expected am commands
  private val forceStop = "force-stop com.example.app"
  private val checkVersion = "broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation version"
  private val addTile = "broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation 'add-tile' --ecn component com.example.app/com.example.app.Component"
  private val showTile = "broadcast -a com.google.android.wearable.app.DEBUG_SYSUI --es operation show-tile --ei index 101"
  private val setDebugAppAm = "set-debug-app -w 'com.example.app'"
  private val setDebugAppBroadcast = "broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation set-debug-app --es package 'com.example.app'"
  private val removeTile = "broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation remove-tile --ecn component com.example.app/com.example.app.Component"
  private val clearDebugAppAm = "clear-debug-app"
  private val clearDebugAppBroadcast = "broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation 'clear-debug-app'"

  private fun getExecutionEnvironment(executorInstance: Executor): ExecutionEnvironment {
    val configSettings = RunManager.getInstance(project).createConfiguration(
      "run Tile", AndroidTileConfigurationType().configurationFactories.single())
    return ExecutionEnvironment(executorInstance, AndroidConfigurationProgramRunner(), configSettings, project)
  }

  @Test
  fun testRun() {
    // Use DefaultRunExecutor, equivalent of pressing run button.
    val env = getExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance())

    val deviceState = fakeAdbRule.connectAndWaitForDevice()
    val receivedAmCommands = ArrayList<String>()

    deviceState.setActivityManager { args: List<String>, serviceOutput: ServiceOutput ->
      val wholeCommand = args.joinToString(" ")

      receivedAmCommands.add(wholeCommand)

      when (wholeCommand) {
        checkVersion -> serviceOutput.writeStdout(
          "Broadcasting: Intent { act=com.google.android.wearable.app.DEBUG_SURFACE flg=0x400000 (has extras) }\n" +
          "Broadcast completed: result=1, data=\"3\"")
        addTile -> serviceOutput.writeStdout(
          "Broadcasting: Intent { act=com.google.android.wearable.app.DEBUG_SURFACE flg=0x400000 (has extras) }\n" +
          "Broadcast completed: result=1, data=\"Index=[101]\"")
        showTile -> serviceOutput.writeStdout(
          "Broadcasting: Intent { act=com.google.android.wearable.app.DEBUG_SURFACE flg=0x400000 (has extras) }\n" +
          // Unsuccessful execution of show tile.
          "Broadcast completed: result=2")
      }
    }

    val device = AndroidDebugBridge.getBridge()!!.devices.single()

    val deployTarget = TestDeployTarget(device)
    val settings = object : AppRunSettings {
      override val deployOptions = DeployOptions(emptyList(), "", true, true)
      override val componentLaunchOptions = TileLaunchOptions().apply {
        componentName = this@AndroidTileConfigurationExecutorTest.componentName
      }
      override val module = myModule
    }

    val executor = Mockito.spy(
      AndroidTileConfigurationExecutor(env, deployTarget, settings, TestApplicationIdProvider(appId), TestApksProvider(appId)))

    val app = createApp(device, appId, servicesName = listOf(componentName), activitiesName = emptyList())
    val appInstaller = TestApplicationInstaller(appId, app)
    // Mock app installation.
    Mockito.doReturn(appInstaller).whenever(executor).getApplicationDeployer(any())

    val runContentDescriptor = getRunContentDescriptorForTests { executor.run().blockingGet(10, TimeUnit.SECONDS)!! }

    // Verify commands sent to device.

    // Force stop
    assertThat(receivedAmCommands[0]).isEqualTo(forceStop)
    // Check version
    assertThat(receivedAmCommands[1]).isEqualTo(checkVersion)
    // Set Tile.
    assertThat(receivedAmCommands[2]).isEqualTo(addTile)
    // Showing Tile.
    assertThat(receivedAmCommands[3]).isEqualTo(showTile)

    // Verify that a warning was raised in console.
    val consoleViewImpl = runContentDescriptor.executionConsole as ConsoleViewImpl
    // Print deferred text
    val consoleOutputPromise = CompletableFuture<String>()
    invokeLater {
      consoleViewImpl.getComponent()
      consoleViewImpl.flushDeferredText()
      consoleOutputPromise.complete(consoleViewImpl.editor.document.text)
    }
    val consoleOutput = consoleOutputPromise.get(10, TimeUnit.SECONDS)
    assertThat(consoleOutput)
      .contains("Warning: Launch was successful, but you may need to bring up the tile manually.")
  }

  @Test
  fun testException() {
    // Use DefaultRunExecutor, equivalent of pressing run button.
    val env = getExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance())

    val failedResponse = "Broadcast completed: result=2, data=\"Internal failure.\"\n" +
                         "End of output."

    val deviceState = fakeAdbRule.connectAndWaitForDevice()
    val receivedAmCommands = ArrayList<String>()

    deviceState.setActivityManager { args: List<String>, serviceOutput: ServiceOutput ->
      val wholeCommand = args.joinToString(" ")

      receivedAmCommands.add(wholeCommand)

      when (wholeCommand) {
        checkVersion -> serviceOutput.writeStdout(
          "Broadcasting: Intent { act=com.google.android.wearable.app.DEBUG_SURFACE flg=0x400000 (has extras) }\n" +
          "Broadcast completed: result=1, data=\"3\"")
        addTile -> serviceOutput.writeStdout(failedResponse)
      }
    }

    val device = AndroidDebugBridge.getBridge()!!.devices.single()

    val deployTarget = TestDeployTarget(device)
    val settings = object : AppRunSettings {
      override val deployOptions = DeployOptions(emptyList(), "", true, true)
      override val componentLaunchOptions = TileLaunchOptions().apply {
        componentName = this@AndroidTileConfigurationExecutorTest.componentName
      }
      override val module = myModule
    }

    val executor = Mockito.spy(
      AndroidTileConfigurationExecutor(env, deployTarget, settings, TestApplicationIdProvider(appId), TestApksProvider(appId)))

    val app = createApp(device, appId, servicesName = listOf(componentName), activitiesName = emptyList())
    val appInstaller = TestApplicationInstaller(appId, app) // Mock app installation.
    Mockito.doReturn(appInstaller).whenever(executor).getApplicationDeployer(any())

    val e = assertFailsWith<Throwable> { executor.debug().blockingGet(10, TimeUnit.SECONDS) }.let {
      ExceptionUtil.findCause(it, ExecutionException::class.java)
    }
    assertThat(e).hasMessageThat().contains("Error while setting the tile, message: $failedResponse")
  }

  @Test
  fun testComponentActivationException() {
    // Use DefaultRunExecutor, equivalent of pressing run button.
    val env = getExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance())

    val failedResponse = "Component not found."

    val deviceState = fakeAdbRule.connectAndWaitForDevice()
    val receivedAmCommands = ArrayList<String>()

    deviceState.setActivityManager { args: List<String>, serviceOutput: ServiceOutput ->
      val wholeCommand = args.joinToString(" ")

      receivedAmCommands.add(wholeCommand)

      when (wholeCommand) {
        checkVersion -> serviceOutput.writeStdout(
          "Broadcasting: Intent { act=com.google.android.wearable.app.DEBUG_SURFACE flg=0x400000 (has extras) }\n" +
          "Broadcast completed: result=1, data=\"3\"")
      }
    }

    val device = AndroidDebugBridge.getBridge()!!.devices.single()

    val deployTarget = TestDeployTarget(device)
    val settings = object : AppRunSettings {
      override val deployOptions = DeployOptions(emptyList(), "", true, true)
      override val componentLaunchOptions = TileLaunchOptions().apply {
        componentName = this@AndroidTileConfigurationExecutorTest.componentName
      }
      override val module = myModule
    }

    val executor = Mockito.spy(
      AndroidTileConfigurationExecutor(env, deployTarget, settings, TestApplicationIdProvider(appId), TestApksProvider(appId)))

    val app = Mockito.mock(App::class.java)
    Mockito.doThrow(DeployerException.componentActivationException(failedResponse))
      .whenever(app).activateComponent(any(), any(), any(AppComponent.Mode::class.java), any())
    val appInstaller = TestApplicationInstaller(appId, app) // Mock app installation.
    Mockito.doReturn(appInstaller).whenever(executor).getApplicationDeployer(any())

    val e = assertFailsWith<Throwable> { executor.run().blockingGet(10, TimeUnit.SECONDS) }.let {
      ExceptionUtil.findCause(it, ExecutionException::class.java)
    }

    assertThat(e).hasMessageThat().contains("Error while setting the tile, message: $failedResponse")
  }

  @Test
  fun testDebug() {
    // Use DefaultRunExecutor, equivalent of pressing debug button.
    val env = getExecutionEnvironment(DefaultDebugExecutor.getDebugExecutorInstance())

    val processTerminatedLatch = CountDownLatch(2)

    val deviceState = fakeAdbRule.connectAndWaitForDevice()
    val receivedAmCommands = ArrayList<String>()

    deviceState.setActivityManager { args: List<String>, serviceOutput: ServiceOutput ->
      val wholeCommand = args.joinToString(" ")

      receivedAmCommands.add(wholeCommand)

      when (wholeCommand) {
        checkVersion -> serviceOutput.writeStdout(
          "Broadcasting: Intent { act=com.google.android.wearable.app.DEBUG_SURFACE flg=0x400000 (has extras) }\n" +
          "Broadcast completed: result=1, data=\"3\"")
        addTile -> {
          deviceState.startClient(1234, 1235, appId, true)
          serviceOutput.writeStdout("Broadcast completed: result=1, data=\"Index=[101]\"")
        }
        setDebugAppBroadcast -> serviceOutput.writeStdout("Broadcast completed: result=2, data=\"Failed to set up the debug app\"")
        removeTile -> {
          deviceState.stopClient(1234)
          serviceOutput.writeStdout("Broadcast completed: result=1")
        }
        clearDebugAppBroadcast -> processTerminatedLatch.countDown()
        clearDebugAppAm -> processTerminatedLatch.countDown()
      }
    }

    val device = AndroidDebugBridge.getBridge()!!.devices.single()

    val deployTarget = TestDeployTarget(device)
    val settings = object : AppRunSettings {
      override val deployOptions = DeployOptions(emptyList(), "", true, true)
      override val componentLaunchOptions = TileLaunchOptions().apply {
        componentName = this@AndroidTileConfigurationExecutorTest.componentName
      }
      override val module = myModule
    }

    val executor = Mockito.spy(
      AndroidTileConfigurationExecutor(env, deployTarget, settings, TestApplicationIdProvider(appId), TestApksProvider(appId)))

    val app = createApp(device, appId, servicesName = listOf(componentName), activitiesName = emptyList())
    val appInstaller = TestApplicationInstaller(appId, app)
    // Mock app installation.
    Mockito.doReturn(appInstaller).whenever(executor).getApplicationDeployer(any())

    val runContentDescriptor = getRunContentDescriptorForTests { executor.debug().blockingGet(10, TimeUnit.SECONDS)!! }
    assertThat(runContentDescriptor.processHandler).instanceOf(AndroidRemoteDebugProcessHandler::class)

    // Stop configuration.
    runContentDescriptor.processHandler!!.destroyProcess()
    if (!processTerminatedLatch.await(10, TimeUnit.SECONDS)) {
      fail("process is not terminated")
    }

    // Verify commands sent to device.

    // force stop
    assertThat(receivedAmCommands[0]).isEqualTo(forceStop)
    // Check version
    assertThat(receivedAmCommands[1]).isEqualTo(checkVersion)
    // Set debug app.
    assertThat(receivedAmCommands[2]).isEqualTo(setDebugAppAm)
    assertThat(receivedAmCommands[3]).isEqualTo(setDebugAppBroadcast)
    // Set Tile.
    assertThat(receivedAmCommands[4]).isEqualTo(addTile)
    // Showing Tile.
    assertThat(receivedAmCommands[5]).isEqualTo(showTile)
    // Unset tile
    assertThat(receivedAmCommands[6]).isEqualTo(removeTile)
    // Clear debug app
    assertThat(receivedAmCommands[7]).isEqualTo(clearDebugAppBroadcast)
    assertThat(receivedAmCommands[8]).isEqualTo(clearDebugAppAm)
  }
}
