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
import com.android.testutils.TestResources
import com.android.tools.deployer.model.component.Complication
import com.android.tools.idea.execution.common.AppRunSettings
import com.android.tools.idea.execution.common.DeployOptions
import com.android.tools.idea.run.ApkInfo
import com.android.tools.idea.run.configuration.AndroidComplicationConfiguration
import com.android.tools.idea.run.configuration.AndroidComplicationConfigurationType
import com.android.tools.idea.run.configuration.AndroidConfigurationProgramRunner
import com.android.tools.idea.run.configuration.ComplicationSlot
import com.android.tools.idea.run.configuration.ComplicationWatchFaceInfo
import com.android.tools.idea.run.configuration.getComplicationSourceTypes
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.ExecutionException
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.util.ExceptionUtil
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.doReturn
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith


class AndroidComplicationConfigurationExecutorTest : AndroidConfigurationExecutorBaseTest() {
  private object TestWatchFaceInfo : ComplicationWatchFaceInfo {
    override val complicationSlots: List<ComplicationSlot> = emptyList()
    override val apk: String = "/path/to/watchface.apk"
    override val appId: String = "com.example.watchface"
    override val watchFaceFQName: String = "com.example.watchface.MyWatchFace"
  }

  // Expected am commands
  private val forceStop = "force-stop com.example.app"
  private val checkVersion = "broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation version"
  private val setComplicationSlot1 = "broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation set-complication" +
                                     " --ecn component 'com.example.app/com.example.app.Component'" +
                                     " --ecn watchface 'com.example.watchface/com.example.watchface.MyWatchFace'" +
                                     " --ei slot 1 --ei type 3"
  private val setComplicationSlot3 = "broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation set-complication" +
                                     " --ecn component 'com.example.app/com.example.app.Component'" +
                                     " --ecn watchface 'com.example.watchface/com.example.watchface.MyWatchFace'" +
                                     " --ei slot 3 --ei type 5"
  private val showWatchFace = "broadcast -a com.google.android.wearable.app.DEBUG_SYSUI --es operation show-watchface"
  private val setDebugAppAm = "set-debug-app -w 'com.example.app'"
  private val setDebugAppBroadcast = "broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation set-debug-app --es package 'com.example.app'"
  private val unsetComplication = "broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation unset-complication --ecn component com.example.app/com.example.app.Component"
  private val unsetWatchFace = "broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation unset-watchface"
  private val clearDebugAppAm = "clear-debug-app"
  private val clearDebugAppBroadcast = "broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation 'clear-debug-app'"

  private val runner = object : AndroidConfigurationProgramRunner() {
    override fun canRunWithMultipleDevices(executorId: String) = true
    override val supportedConfigurationTypeIds: List<String>
      get() = listOf(AndroidComplicationConfigurationType().id)
  }

  @Test
  fun test() {
    val configSettings = RunManager.getInstance(project).createConfiguration(
      "run complication", AndroidComplicationConfigurationType().configurationFactories.single())
    // Use run executor
    val env = ExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance(), runner, configSettings,
                                   project)

    val deviceState = fakeAdbRule.connectAndWaitForDevice()
    val receivedAmCommands = ArrayList<String>()

    deviceState.setActivityManager { args: List<String>, serviceOutput: ServiceOutput ->
      val wholeCommand = args.joinToString(" ")

      receivedAmCommands.add(wholeCommand)

      when (wholeCommand) {
        checkVersion -> serviceOutput.writeStdout(
          "Broadcasting: Intent { act=com.google.android.wearable.app.DEBUG_SURFACE flg=0x400000 (has extras) }\n" +
          "Broadcast completed: result=1, data=\"2\"")
        setComplicationSlot1 -> serviceOutput.writeStdout("Broadcast completed: result=1")
        setComplicationSlot3 -> serviceOutput.writeStdout("Broadcast completed: result=1")
        showWatchFace -> serviceOutput.writeStdout("Broadcast completed: result=1")
      }
    }

    val device = AndroidDebugBridge.getBridge()!!.devices.single()

    val app = createApp(device, appId, servicesName = listOf(componentName), activitiesName = emptyList())
    val watchFaceApp = createApp(device, TestWatchFaceInfo.appId, servicesName = listOf(TestWatchFaceInfo.watchFaceFQName),
                                 activitiesName = emptyList())

    val settings = object : AppRunSettings {
      override val deployOptions = DeployOptions(emptyList(), "", true, true)
      override val componentLaunchOptions = ComplicationLaunchOptions().apply {
        watchFaceInfo = TestWatchFaceInfo
        componentName = this@AndroidComplicationConfigurationExecutorTest.componentName
        chosenSlots = listOf(
          AndroidComplicationConfiguration.ChosenSlot(1, Complication.ComplicationType.SHORT_TEXT),
          AndroidComplicationConfiguration.ChosenSlot(3, Complication.ComplicationType.RANGED_VALUE)
        )
      }
      override val module = myModule
    }
    val executor = Mockito.spy(
      AndroidComplicationConfigurationExecutor(env, TestDeployTarget(device), settings, TestApplicationIdProvider(appId),
                                               TestApksProvider(appId)))
    // Mock installation that returns app.
    val appInstaller = TestApplicationInstaller(
      hashMapOf(
        Pair(appId, app),
        Pair(TestWatchFaceInfo.appId, watchFaceApp)
      )
    )
    doReturn(appInstaller).whenever(executor).getApplicationDeployer(any())

    // Mock the binary xml extraction.
    doReturn(listOf("RANGED_VALUE", "SHORT_TEXT", "ICON")).whenever(executor).getComplicationSourceTypes(any())

    val runContentDescriptor = getRunContentDescriptorForTests {
      executor.run(EmptyProgressIndicator()).blockingGet(10, TimeUnit.SECONDS)!!
    }

    // Verify commands sent to device.

    // Check version
    assertThat(receivedAmCommands[0]).isEqualTo(forceStop)
    // Check version
    assertThat(receivedAmCommands[1]).isEqualTo(checkVersion)
    // ChosenSlot(1, Complication.ComplicationType.SHORT_TEXT).
    assertThat(receivedAmCommands[2]).isEqualTo(setComplicationSlot1)
    // ChosenSlot(3, Complication.ComplicationType.RANGED_VALUE).
    assertThat(receivedAmCommands[3]).isEqualTo(setComplicationSlot3)
    // Show watch face.
    assertThat(receivedAmCommands[4]).isEqualTo(showWatchFace)

    // Verify that a warning was raised.
    val consoleViewImpl = runContentDescriptor.executionConsole as ConsoleViewImpl
    // Print deferred text
    val consoleOutputPromise = CompletableFuture<String>()
    invokeLater {
      consoleViewImpl.component
      consoleViewImpl.flushDeferredText()
      consoleOutputPromise.complete(consoleViewImpl.editor.document.text)
    }
    val consoleOutput = consoleOutputPromise.get(10, TimeUnit.SECONDS)
    assertThat(consoleOutput)
      .contains("Warning: The chosen Wear device may kill background services if they take too long to respond, which can " +
                "affect debugging. To avoid this, please update the Wear OS app on your device to the latest version.")
  }

  @Test
  fun testDebug() {
    val configSettings = RunManager.getInstance(project).createConfiguration(
      "run complication", AndroidComplicationConfigurationType().configurationFactories.single())

    // Use DefaultDebugExecutor executor.
    val env = ExecutionEnvironment(DefaultDebugExecutor.getDebugExecutorInstance(), runner, configSettings,
                                   project)

    val processTerminatedLatch = CountDownLatch(1)

    val deviceState = fakeAdbRule.connectAndWaitForDevice()
    val receivedAmCommands = ArrayList<String>()

    deviceState.setActivityManager { args: List<String>, serviceOutput: ServiceOutput ->
      val wholeCommand = args.joinToString(" ")

      receivedAmCommands.add(wholeCommand)

      when (wholeCommand) {
        checkVersion -> serviceOutput.writeStdout(
          "Broadcasting: Intent { act=com.google.android.wearable.app.DEBUG_SURFACE flg=0x400000 (has extras) }\n" +
          "Broadcast completed: result=1, data=\"2\"")
        setComplicationSlot1 -> {
          deviceState.startClient(1234, 1235, appId, true)
          serviceOutput.writeStdout("Broadcast completed: result=1")
        }
        setComplicationSlot3 -> serviceOutput.writeStdout("Broadcast completed: result=1")
        showWatchFace -> serviceOutput.writeStdout("Broadcast completed: result=1")
        unsetWatchFace -> {
          deviceState.stopClient(1234)
          serviceOutput.writeStdout("Broadcast completed: result=1")
        }

        clearDebugAppAm -> processTerminatedLatch.countDown()
      }
    }

    val device = AndroidDebugBridge.getBridge()!!.devices.single()

    val app = createApp(device, appId, servicesName = listOf(componentName), activitiesName = emptyList())
    val watchFaceApp = createApp(device, TestWatchFaceInfo.appId, servicesName = listOf(TestWatchFaceInfo.watchFaceFQName),
                                 activitiesName = emptyList())

    val settings = object : AppRunSettings {
      override val deployOptions = DeployOptions(emptyList(), "", true, true)
      override val componentLaunchOptions = ComplicationLaunchOptions().apply {
        watchFaceInfo = TestWatchFaceInfo
        componentName = this@AndroidComplicationConfigurationExecutorTest.componentName
        chosenSlots = listOf(
          AndroidComplicationConfiguration.ChosenSlot(1, Complication.ComplicationType.SHORT_TEXT),
          AndroidComplicationConfiguration.ChosenSlot(3, Complication.ComplicationType.RANGED_VALUE)
        )
      }
      override val module = myModule
    }

    val executor = Mockito.spy(
      AndroidComplicationConfigurationExecutor(env, TestDeployTarget(device), settings, TestApplicationIdProvider(appId),
                                               TestApksProvider(appId)))

    // Mock installation that returns app.
    val appInstaller = TestApplicationInstaller(
      hashMapOf(
        Pair(appId, app),
        Pair(TestWatchFaceInfo.appId, watchFaceApp)
      )
    )
    doReturn(appInstaller).whenever(executor).getApplicationDeployer(any())

    // Mock the binary xml extraction.
    doReturn(listOf("RANGED_VALUE", "SHORT_TEXT", "ICON")).whenever(executor).getComplicationSourceTypes(any())

    val runContentDescriptor = getRunContentDescriptorForTests {
      executor.debug(EmptyProgressIndicator()).blockingGet(10, TimeUnit.SECONDS)!!
    }

    // Stop configuration.
    runContentDescriptor.processHandler!!.destroyProcess()
    processTerminatedLatch.await(1, TimeUnit.SECONDS)

    // Verify receivedAmCommands sent to device.

    //Force stop
    assertThat(receivedAmCommands[0]).isEqualTo(forceStop)
    // Check version
    assertThat(receivedAmCommands[1]).isEqualTo(checkVersion)
    // Set debug app.
    assertThat(receivedAmCommands[2]).isEqualTo(setDebugAppAm)
    assertThat(receivedAmCommands[3]).isEqualTo(setDebugAppBroadcast)
    // ChosenSlot(1, Complication.ComplicationType.SHORT_TEXT).
    assertThat(receivedAmCommands[4]).isEqualTo(setComplicationSlot1)
    // Set debug app.
    assertThat(receivedAmCommands[5]).isEqualTo(setDebugAppAm)
    assertThat(receivedAmCommands[6]).isEqualTo(setDebugAppBroadcast)
    // ChosenSlot(3, Complication.ComplicationType.RANGED_VALUE).
    assertThat(receivedAmCommands[7]).isEqualTo(setComplicationSlot3)
    // Show watch face
    assertThat(receivedAmCommands[8]).isEqualTo(showWatchFace)
    // Unset complication
    assertThat(receivedAmCommands[9]).isEqualTo(unsetComplication)
    // Unset debug watchFace
    assertThat(receivedAmCommands[10]).isEqualTo(unsetWatchFace)
    // Clear debug app
    assertThat(receivedAmCommands[11]).isEqualTo(clearDebugAppBroadcast)
    assertThat(receivedAmCommands[12]).isEqualTo(clearDebugAppAm)
  }

  @Test
  fun testWatchFaceWarning() {
    val configSettings = RunManager.getInstance(project).createConfiguration(
      "run complication", AndroidComplicationConfigurationType().configurationFactories.single())
    // Use run executor
    val env = ExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance(), runner, configSettings, project)

    val deviceState = fakeAdbRule.connectAndWaitForDevice()
    val receivedAmCommands = ArrayList<String>()

    deviceState.setActivityManager { args: List<String>, serviceOutput: ServiceOutput ->
      val wholeCommand = args.joinToString(" ")

      receivedAmCommands.add(wholeCommand)

      when (wholeCommand) {
        checkVersion -> serviceOutput.writeStdout(
          "Broadcasting: Intent { act=com.google.android.wearable.app.DEBUG_SURFACE flg=0x400000 (has extras) }\n" +
          "Broadcast completed: result=1, data=\"2\"")
        setComplicationSlot1 -> serviceOutput.writeStdout("Broadcast completed: result=1")
        setComplicationSlot3 -> serviceOutput.writeStdout("Broadcast completed: result=1")
        // Unsuccessful show watchface case.
        showWatchFace -> serviceOutput.writeStdout("Broadcast completed: result=2")
      }
    }

    val device = AndroidDebugBridge.getBridge()!!.devices.single()

    val app = createApp(device, appId, servicesName = listOf(componentName), activitiesName = emptyList())
    val watchFaceApp = createApp(device, TestWatchFaceInfo.appId, servicesName = listOf(TestWatchFaceInfo.watchFaceFQName),
                                 activitiesName = emptyList())

    val settings = object : AppRunSettings {
      override val deployOptions = DeployOptions(emptyList(), "", true, true)
      override val componentLaunchOptions = ComplicationLaunchOptions().apply {
        watchFaceInfo = TestWatchFaceInfo
        componentName = this@AndroidComplicationConfigurationExecutorTest.componentName
        chosenSlots = listOf(AndroidComplicationConfiguration.ChosenSlot(1, Complication.ComplicationType.SHORT_TEXT))
      }
      override val module = myModule
    }

    val executor = Mockito.spy(
      AndroidComplicationConfigurationExecutor(env, TestDeployTarget(device), settings, TestApplicationIdProvider(appId),
                                               TestApksProvider(appId)))
    // Mock installation that returns app.
    val appInstaller = TestApplicationInstaller(
      hashMapOf(
        Pair(appId, app),
        Pair(TestWatchFaceInfo.appId, watchFaceApp)
      )
    )
    doReturn(appInstaller).whenever(executor).getApplicationDeployer(any())

    // Mock the binary xml extraction.
    doReturn(listOf("RANGED_VALUE", "SHORT_TEXT", "ICON")).whenever(executor).getComplicationSourceTypes(any())

    val runContentDescriptor = getRunContentDescriptorForTests {
      executor.run(EmptyProgressIndicator()).blockingGet(10, TimeUnit.SECONDS)!!
    }

    // Verify that a warning was raised in console.
    val consoleViewImpl = runContentDescriptor.executionConsole as ConsoleViewImpl
    // Print differed test
    val consoleOutputPromise = CompletableFuture<String>()
    runInEdt {
      // Initialize editor.
      consoleViewImpl.component
      consoleViewImpl.flushDeferredText()
      consoleOutputPromise.complete(consoleViewImpl.editor.document.text)
    }
    val consoleOutput = consoleOutputPromise.get(10, TimeUnit.SECONDS)
    assertThat(consoleOutput)
      .contains("Warning: Launch was successful, but you may need to bring up the watch face manually")
  }

  @Test
  fun testComponentActivationException() {
    val configSettings = RunManager.getInstance(project).createConfiguration(
      "run complication", AndroidComplicationConfigurationType().configurationFactories.single())
    // Use run executor
    val env = Mockito.spy(ExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance(), runner, configSettings, project))
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
        // Unsuccessful result
        setComplicationSlot1 -> serviceOutput.writeStdout(failedResponse)
      }
    }

    val device = AndroidDebugBridge.getBridge()!!.devices.single()

    val settings = object : AppRunSettings {
      override val deployOptions = DeployOptions(emptyList(), "", true, true)
      override val componentLaunchOptions = ComplicationLaunchOptions().apply {
        watchFaceInfo = TestWatchFaceInfo
        componentName = this@AndroidComplicationConfigurationExecutorTest.componentName
        chosenSlots = listOf(AndroidComplicationConfiguration.ChosenSlot(1, Complication.ComplicationType.SHORT_TEXT))
      }
      override val module = myModule
    }

    val executor = Mockito.spy(
      AndroidComplicationConfigurationExecutor(env, TestDeployTarget(device), settings, TestApplicationIdProvider(appId),
                                               TestApksProvider(appId)))
    doReturn(emptyList<String>()).whenever(executor).getComplicationSourceTypes(any())
    doReturn(listOf("SHORT_TEXT", "ICON")).whenever(executor).getComplicationSourceTypes(any())

    val app = createApp(device, appId, servicesName = listOf(componentName), activitiesName = emptyList())
    val watchFaceApp = createApp(device, TestWatchFaceInfo.appId, servicesName = listOf(TestWatchFaceInfo.watchFaceFQName),
                                 activitiesName = emptyList())
    val appInstaller = TestApplicationInstaller(
      hashMapOf(
        Pair(appId, app),
        Pair(TestWatchFaceInfo.appId, watchFaceApp)
      )) // Mock app installation.
    doReturn(appInstaller).whenever(executor).getApplicationDeployer(any())

    val e = assertFailsWith<Throwable> { executor.run(EmptyProgressIndicator()).blockingGet(10, TimeUnit.SECONDS) }.let {
      ExceptionUtil.findCause(it, ExecutionException::class.java)
    }
    assertThat(e).hasMessageThat().contains("Error while launching complication, message: $failedResponse")
  }

  @Test
  fun testGetComplicationSourceTypes() {
    val types = getComplicationSourceTypes(
      listOf(ApkInfo(TestResources.getFile("/WearableTestApk.apk"), "com.example.android.wearable.watchface")),
      "com.example.android.wearable.watchface.provider.IncrementingNumberComplicationProviderService")
    assertThat(types).isEqualTo(listOf("SHORT_TEXT", "LONG_TEXT"))
  }
}
