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
import com.android.testutils.TestResources
import com.android.tools.deployer.model.component.AppComponent
import com.android.tools.deployer.model.component.Complication.ComplicationType.RANGED_VALUE
import com.android.tools.deployer.model.component.Complication.ComplicationType.SHORT_TEXT
import com.android.tools.deployer.model.component.Complication.ComplicationType.LONG_TEXT
import com.android.tools.idea.run.configuration.AndroidComplicationConfiguration
import com.android.tools.idea.run.configuration.AndroidComplicationConfigurationType
import com.android.tools.idea.run.configuration.AndroidConfigurationProgramRunner
import com.android.tools.idea.run.configuration.ComplicationSlot
import com.android.tools.idea.run.configuration.ComplicationWatchFaceInfo
import com.android.tools.deployer.model.component.Complication
import com.android.tools.idea.run.ApkInfo
import com.android.tools.idea.run.configuration.getComplicationSourceTypes
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.times
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


class AndroidComplicationConfigurationExecutorTest : AndroidConfigurationExecutorBaseTest() {

  private object TestWatchFaceInfo : ComplicationWatchFaceInfo {
    override val complicationSlots: List<ComplicationSlot> = emptyList()
    override val apk: String = "/path/to/watchface.apk"
    override val appId: String = "com.example.watchface"
    override val watchFaceFQName: String = "com.example.watchface.MyWatchFace"
  }

  // Expected commands
  private val checkVersion = "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation version"
  private val setComplicationSlot1 = "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation set-complication" +
                                     " --ecn component 'com.example.app/com.example.app.Component'" +
                                     " --ecn watchface 'com.example.watchface/com.example.watchface.MyWatchFace'" +
                                     " --ei slot 1 --ei type 3"
  private val setComplicationSlot3 = "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation set-complication" +
                                     " --ecn component 'com.example.app/com.example.app.Component'" +
                                     " --ecn watchface 'com.example.watchface/com.example.watchface.MyWatchFace'" +
                                     " --ei slot 3 --ei type 5"
  private val setWatchFace = "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE" +
                             " --es operation set-watchface" +
                             " --ecn component com.example.watchface/com.example.watchface.MyWatchFace"
  private val showWatchFace = "am broadcast -a com.google.android.wearable.app.DEBUG_SYSUI --es operation show-watchface"
  private val setDebugAppAm = "am set-debug-app -w 'com.example.app'"
  private val setDebugAppBroadcast = "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation set-debug-app --es package 'com.example.app'"
  private val unsetComplication = "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation unset-complication --ecn component com.example.app/com.example.app.Component"
  private val unsetWatchFace = "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation unset-watchface"

  fun test() {
    val configSettings = RunManager.getInstance(project).createConfiguration(
      "run tile", AndroidComplicationConfigurationType().configurationFactories.single())
    val androidComplicationConfiguration = configSettings.configuration as AndroidComplicationConfiguration
    androidComplicationConfiguration.watchFaceInfo = TestWatchFaceInfo
    androidComplicationConfiguration.setModule(myModule)
    androidComplicationConfiguration.componentName = componentName
    androidComplicationConfiguration.chosenSlots = listOf(
      AndroidComplicationConfiguration.ChosenSlot(1, Complication.ComplicationType.SHORT_TEXT),
      AndroidComplicationConfiguration.ChosenSlot(3, Complication.ComplicationType.RANGED_VALUE)
    )
    // Use run executor
    val env = ExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance(), AndroidConfigurationProgramRunner(), configSettings,
                                   project)

    val device = getMockDevice(mapOf(
      checkVersion to "Broadcast completed: result=1, data=\"2\"",
      setComplicationSlot1 to "Broadcast completed: result=1",
      setComplicationSlot3 to "Broadcast completed: result=1",
      setWatchFace to "Broadcast completed: result=1",
      showWatchFace to "Broadcast completed: result=1").toCommandHandlers()
    )

    val app = createApp(device, appId, servicesName = listOf(componentName), activitiesName = emptyList())
    val watchFaceApp = createApp(device, TestWatchFaceInfo.appId, servicesName = listOf(TestWatchFaceInfo.watchFaceFQName),
                                 activitiesName = emptyList())

    val executor = Mockito.spy(AndroidComplicationConfigurationExecutor(env))
    // Mock installation that returns app.
    val appInstaller = TestApplicationInstaller(
      hashMapOf(
        Pair(appId, app),
        Pair(TestWatchFaceInfo.appId, watchFaceApp)
      )
    )
    doReturn(appInstaller).`when`(executor).getApplicationInstaller(any())

    // Mock the binary xml extraction.
    doReturn(listOf(RANGED_VALUE, SHORT_TEXT, LONG_TEXT)).`when`(executor).getComplicationSourceTypes(any())

    val runContentDescriptor = executor.doOnDevices(listOf(device)).blockingGet(1000)!!

    // Verify commands sent to device.
    val commandsCaptor = ArgumentCaptor.forClass(String::class.java)
    Mockito.verify(device, times(5)).executeShellCommand(
      commandsCaptor.capture(),
      any(IShellOutputReceiver::class.java),
      any(),
      any()
    )
    val commands = commandsCaptor.allValues
    // Check version
    assertThat(commands[0]).isEqualTo(checkVersion)
    // ChosenSlot(1, Complication.ComplicationType.SHORT_TEXT).
    assertThat(commands[1]).isEqualTo(setComplicationSlot1)
    // ChosenSlot(3, Complication.ComplicationType.RANGED_VALUE).
    assertThat(commands[2]).isEqualTo(setComplicationSlot3)
    // Set watch face.
    assertThat(commands[3]).isEqualTo(setWatchFace)
    // Show watch face.
    assertThat(commands[4]).isEqualTo(showWatchFace)

    // Verify that a warning was raised.
    val consoleViewImpl = runContentDescriptor.executionConsole as ConsoleViewImpl
    // Print deferred text
    consoleViewImpl.getComponent()
    consoleViewImpl.flushDeferredText()
    val consoleOutput = consoleViewImpl.editor.document.text
    assertThat(consoleOutput)
      .contains("Warning: The chosen Wear device may kill background services if they take too long to respond, which can " +
                "affect debugging. To avoid this, please update the Wear OS app on your device to the latest version.")
  }

  fun testDebug() {
    val configSettings = RunManager.getInstance(project).createConfiguration(
      "run tile", AndroidComplicationConfigurationType().configurationFactories.single())
    val androidComplicationConfiguration = configSettings.configuration as AndroidComplicationConfiguration
    androidComplicationConfiguration.watchFaceInfo = TestWatchFaceInfo
    androidComplicationConfiguration.setModule(myModule)
    androidComplicationConfiguration.componentName = componentName
    androidComplicationConfiguration.chosenSlots = listOf(
      AndroidComplicationConfiguration.ChosenSlot(1, Complication.ComplicationType.SHORT_TEXT),
      AndroidComplicationConfiguration.ChosenSlot(3, Complication.ComplicationType.RANGED_VALUE)
    )
    // Use DefaultDebugExecutor executor.
    val env = ExecutionEnvironment(DefaultDebugExecutor.getDebugExecutorInstance(), AndroidConfigurationProgramRunner(), configSettings,
                                   project)

    val commandHandlers: MutableMap<Command, CommandHandler> = mapOf(
      checkVersion to "Broadcast completed: result=1, data=\"2\"",
      setComplicationSlot1 to "Broadcast completed: result=1",
      setComplicationSlot3 to "Broadcast completed: result=1",
      showWatchFace to "Broadcast completed: result=1",
      setDebugAppAm to "Broadcast completed: result=1",
      setDebugAppBroadcast to "Broadcast completed: result=1"
    ).toCommandHandlers()

    val runnableClientsService = RunnableClientsService(testRootDisposable)

    val setWatchFaceCommandHandler: CommandHandler = { device, receiver ->
      runnableClientsService.startClient(device, appId)
      receiver.addOutput("Broadcast completed: result=1")
    }

    val unsetWatchFaceCommandHandler: CommandHandler = { device, receiver ->
      runnableClientsService.stopClient(device, appId)
      receiver.addOutput("Broadcast completed: result=1")
    }

    val device = getMockDevice(
      commandHandlers +
      (setWatchFace to setWatchFaceCommandHandler) +
      (unsetWatchFace to unsetWatchFaceCommandHandler)
    )

    val app = createApp(device, appId, servicesName = listOf(componentName), activitiesName = emptyList())
    val watchFaceApp = createApp(device, TestWatchFaceInfo.appId, servicesName = listOf(TestWatchFaceInfo.watchFaceFQName),
                                 activitiesName = emptyList())

    val executor = Mockito.spy(AndroidComplicationConfigurationExecutor(env))

    // Mock installation that returns app.
    val appInstaller = TestApplicationInstaller(
      hashMapOf(
        Pair(appId, app),
        Pair(TestWatchFaceInfo.appId, watchFaceApp)
      )
    )
    doReturn(appInstaller).`when`(executor).getApplicationInstaller(any())

    // Mock the binary xml extraction.
    doReturn(listOf(RANGED_VALUE, SHORT_TEXT, LONG_TEXT)).`when`(executor).getComplicationSourceTypes(any())

    val runContentDescriptor = executor.doOnDevices(listOf(device)).blockingGet(1000)
    assertThat(runContentDescriptor!!.processHandler).isNotNull()

    // Stop configuration.
    runContentDescriptor.processHandler!!.destroyProcess()
    runContentDescriptor.processHandler!!.waitFor()

    // Verify commands sent to device.
    val commandsCaptor = ArgumentCaptor.forClass(String::class.java)
    Mockito.verify(device, times(11)).executeShellCommand(
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
    // ChosenSlot(1, Complication.ComplicationType.SHORT_TEXT).
    assertThat(commands[3]).isEqualTo(setComplicationSlot1)
    // Set debug app.
    assertThat(commands[4]).isEqualTo(setDebugAppAm)
    assertThat(commands[5]).isEqualTo(setDebugAppBroadcast)
    // ChosenSlot(3, Complication.ComplicationType.RANGED_VALUE).
    assertThat(commands[6]).isEqualTo(setComplicationSlot3)
    // Set watch face.
    assertThat(commands[7]).isEqualTo(setWatchFace)
    // Show watch face
    assertThat(commands[8]).isEqualTo(showWatchFace)
    // Unset complication
    assertThat(commands[9]).isEqualTo(unsetComplication)
    // Unset debug watchFace
    assertThat(commands[10]).isEqualTo(unsetWatchFace)
  }

  fun testWatchFaceWarning() {
    val configSettings = RunManager.getInstance(project).createConfiguration(
      "run tile", AndroidComplicationConfigurationType().configurationFactories.single())
    val androidComplicationConfiguration = configSettings.configuration as AndroidComplicationConfiguration
    androidComplicationConfiguration.watchFaceInfo = TestWatchFaceInfo
    androidComplicationConfiguration.setModule(myModule)
    androidComplicationConfiguration.componentName = componentName
    androidComplicationConfiguration.chosenSlots = listOf(
      AndroidComplicationConfiguration.ChosenSlot(1, Complication.ComplicationType.SHORT_TEXT),
    )
    // Use run executor
    val env = ExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance(), AndroidConfigurationProgramRunner(), configSettings,
                                   project)

    val device = getMockDevice(mapOf(
      checkVersion to "Broadcast completed: result=1, data=\"2\"",
      setComplicationSlot1 to "Broadcast completed: result=1",
      setComplicationSlot3 to "Broadcast completed: result=1",
      setWatchFace to "Broadcast completed: result=1",
      // Unsuccessful show watchface case.
      showWatchFace to "Broadcast completed: result=2"
    ).toCommandHandlers())

    val app = createApp(device, appId, servicesName = listOf(componentName), activitiesName = emptyList())
    val watchFaceApp = createApp(device, TestWatchFaceInfo.appId, servicesName = listOf(TestWatchFaceInfo.watchFaceFQName),
                                 activitiesName = emptyList())

    val executor = Mockito.spy(AndroidComplicationConfigurationExecutor(env))
    // Mock installation that returns app.
    val appInstaller = TestApplicationInstaller(
      hashMapOf(
        Pair(appId, app),
        Pair(TestWatchFaceInfo.appId, watchFaceApp)
      )
    )
    doReturn(appInstaller).`when`(executor).getApplicationInstaller(any())

    // Mock the binary xml extraction.
    doReturn(listOf(RANGED_VALUE, SHORT_TEXT, LONG_TEXT)).`when`(executor).getComplicationSourceTypes(any())

    val runContentDescriptor = executor.doOnDevices(listOf(device)).blockingGet(1000)!!

    // Verify that a warning was raised in console.
    val consoleViewImpl = runContentDescriptor.executionConsole as ConsoleViewImpl
    // Print differed test
    consoleViewImpl.getComponent()
    consoleViewImpl.flushDeferredText()

    val consoleOutput = consoleViewImpl.editor.document.text
    assertThat(consoleOutput)
      .contains("Warning: Launch was successful, but you may need to bring up the watch face manually")
  }

  fun testComplicationProcessHandler() {
    val processHandler = ComplicationProcessHandler(AppComponent.getFQEscapedName(appId, componentName),
                                                    Mockito.mock(ConsoleView::class.java))
    val countDownLatch = CountDownLatch(1)
    val device = getMockDevice(mapOf(
      unsetWatchFace to { _, _ -> countDownLatch.countDown() }
    ))
    processHandler.addDevice(device)

    processHandler.startNotify()

    processHandler.destroyProcess()

    assertThat(countDownLatch.await(3, TimeUnit.SECONDS)).isTrue()

    // Verify commands sent to device.
    val commandsCaptor = ArgumentCaptor.forClass(String::class.java)
    Mockito.verify(device, times(2)).executeShellCommand(
      commandsCaptor.capture(),
      any(IShellOutputReceiver::class.java),
      any(),
      any()
    )
    val commands = commandsCaptor.allValues

    // Unset complication
    assertThat(commands[0]).isEqualTo(unsetComplication)
    // Unset debug watchFace
    assertThat(commands[1]).isEqualTo(unsetWatchFace)
  }

  fun testGetComplicationSourceTypes() {
    val types = getComplicationSourceTypes(
      listOf(ApkInfo(TestResources.getFile("/WearableTestApk.apk"), "com.example.android.wearable.watchface")),
      "com.example.android.wearable.watchface.provider.IncrementingNumberComplicationProviderService")
    assertThat(types).isEqualTo(listOf(SHORT_TEXT, LONG_TEXT))
  }
}
