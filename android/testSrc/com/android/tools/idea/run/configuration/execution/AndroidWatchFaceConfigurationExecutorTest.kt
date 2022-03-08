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
import com.android.tools.idea.run.configuration.AndroidConfigurationProgramRunner
import com.android.tools.idea.run.configuration.AndroidWatchFaceConfiguration
import com.android.tools.idea.run.configuration.AndroidWatchFaceConfigurationType
import com.google.common.truth.Truth.assertThat
import com.intellij.debugger.DebuggerManager
import com.intellij.debugger.DebuggerManagerEx
import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.testFramework.UsefulTestCase.assertThrows
import com.intellij.testFramework.registerServiceInstance
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AndroidWatchFaceConfigurationExecutorTest : AndroidConfigurationExecutorBaseTest() {
  // Expected commands
  private val checkVersion = "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation version"
  private val setWatchFace = "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation set-watchface --ecn component com.example.app/com.example.app.Component"
  private val showWatchFace = "am broadcast -a com.google.android.wearable.app.DEBUG_SYSUI --es operation show-watchface"
  private val unsetWatchFace = "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation unset-watchface"
  private val setDebugAppAm = "am set-debug-app -w 'com.example.app'"
  private val clearDebugAppAm = "am clear-debug-app"
  private val clearDebugAppBroadcast = "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation 'clear-debug-app'"

  private fun getExecutionEnvironment(executorInstance: Executor): ExecutionEnvironment {
    val configSettings = RunManager.getInstance(project).createConfiguration(
      "run WatchFace", AndroidWatchFaceConfigurationType().configurationFactories.single())
    val androidWatchFaceConfiguration = configSettings.configuration as AndroidWatchFaceConfiguration
    androidWatchFaceConfiguration.setModule(myModule)
    androidWatchFaceConfiguration.componentName = componentName
    // Use debug executor
    return ExecutionEnvironment(executorInstance, AndroidConfigurationProgramRunner(), configSettings, project)
  }

  @Test
  fun testRun() {
    // Use DefaultRunExecutor, equivalent of pressing run button.
    val env = getExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance())

    val executor = Mockito.spy(AndroidWatchFaceConfigurationExecutor(env))

    val device = getMockDevice(mapOf(
      checkVersion to
        "Broadcasting: Intent { act=com.google.android.wearable.app.DEBUG_SURFACE flg=0x400000 (has extras) }\n" +
        "Broadcast completed: result=1, data=\"3\"",
      setWatchFace to
        "Broadcasting: Intent { act=com.google.android.wearable.app.DEBUG_SURFACE flg=0x400000 (has extras) }\n" +
        "Broadcast completed: result=1, data=\"Favorite Id=[2] Runtime=[1]\""
    ).toCommandHandlers())

    val app = createApp(device, appId, servicesName = listOf(componentName), activitiesName = emptyList())
    val appInstaller = TestApplicationInstaller(appId, app)
    // Mock app installation.
    Mockito.doReturn(appInstaller).`when`(executor).getApplicationInstaller(any())

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


    // check WatchFace API version.
    assertThat(commands[0]).isEqualTo(checkVersion)
    // Set WatchFace.
    assertThat(commands[1]).isEqualTo(setWatchFace)
    // Showing WatchFace.
    assertThat(commands[2]).isEqualTo(showWatchFace)
  }

  @Test
  fun testDebug() {
    // Use DefaultRunExecutor, equivalent of pressing debug button.
    val env = getExecutionEnvironment(DefaultDebugExecutor.getDebugExecutorInstance())

    // Executor we test.
    val executor = Mockito.spy(AndroidWatchFaceConfigurationExecutor(env))

    val commandHandlers = mapOf(
      checkVersion to
        "Broadcasting: Intent { act=com.google.android.wearable.app.DEBUG_SURFACE flg=0x400000 (has extras) }\n" +
        "Broadcast completed: result=1, data=\"3\"",
      clearDebugAppBroadcast to ""
    ).toCommandHandlers()

    val runnableClientsService = RunnableClientsService(testRootDisposable)

    val setWatchFaceCommandHandler: CommandHandler = { device, receiver ->
      runnableClientsService.startClient(device, appId)
      receiver.addOutput("Broadcasting: Intent { act=com.google.android.wearable.app.DEBUG_SURFACE flg=0x400000 (has extras) }\n" +
                         "Broadcast completed: result=1, data=\"Favorite Id=[2] Runtime=[1]\"")
    }

    val unsetWatchFaceCommandHandler: CommandHandler = { device, receiver ->
      runnableClientsService.stopClient(device, appId)
      receiver.addOutput("Broadcast completed: result=1")
    }

    val processTerminatedLatch = CountDownLatch(1)
    val clearDebugAppAmCommandHandler: CommandHandler = { device, receiver ->
      receiver.addOutput("")
      processTerminatedLatch.countDown()
    }

    val device = getMockDevice(
      commandHandlers +
      (setWatchFace to setWatchFaceCommandHandler) +
      (unsetWatchFace to unsetWatchFaceCommandHandler) +
      (clearDebugAppAm to clearDebugAppAmCommandHandler)
    )

    val app = createApp(device, appId, servicesName = listOf(componentName), activitiesName = emptyList())
    val appInstaller = TestApplicationInstaller(appId, app)
    // Mock app installation.
    Mockito.doReturn(appInstaller).`when`(executor).getApplicationInstaller(any())

    val runContentDescriptor = executor.doOnDevices(listOf(device)).blockingGet(10, TimeUnit.SECONDS)
    assertThat(runContentDescriptor!!.processHandler).isNotNull()

    // Verify previous app instance is terminated.
    Mockito.verify(executor, Mockito.times(1)).terminatePreviousAppInstance(any())

    // Stop configuration.
    runContentDescriptor.processHandler!!.destroyProcess()
    processTerminatedLatch.await(1, TimeUnit.SECONDS)

    // Verify commands sent to device.
    val commandsCaptor = ArgumentCaptor.forClass(String::class.java)
    Mockito.verify(device, Mockito.times(7)).executeShellCommand(
      commandsCaptor.capture(),
      any(IShellOutputReceiver::class.java),
      any(),
      any()
    )
    val commands = commandsCaptor.allValues

    // check WatchFace API version.
    assertThat(commands[0]).isEqualTo(checkVersion)
    // Set debug app.
    assertThat(commands[1]).isEqualTo(setDebugAppAm)
    // Set WatchFace.
    assertThat(commands[2]).isEqualTo(setWatchFace)
    // Showing WatchFace.
    assertThat(commands[3]).isEqualTo(showWatchFace)
    // Unset watch face
    assertThat(commands[4]).isEqualTo(unsetWatchFace)
    // Clear debug app
    assertThat(commands[5]).isEqualTo(clearDebugAppBroadcast)
    assertThat(commands[6]).isEqualTo(clearDebugAppAm)
  }

  @Test
  fun testAttachingDebuggerFails() {
    // Use DefaultRunExecutor, equivalent of pressing debug button.
    val env = getExecutionEnvironment(DefaultDebugExecutor.getDebugExecutorInstance())

    // Executor we test.
    val executor = Mockito.spy(AndroidWatchFaceConfigurationExecutor(env))
    val debuggerManagerExMock = Mockito.mock(DebuggerManagerEx::class.java)
    project.registerServiceInstance(DebuggerManager::class.java, debuggerManagerExMock)
    Mockito.`when`(debuggerManagerExMock.attachVirtualMachine(any())).thenThrow(ExecutionException("Exception on debug start"))

    val commandHandlers = mapOf(
      checkVersion to
        "Broadcasting: Intent { act=com.google.android.wearable.app.DEBUG_SURFACE flg=0x400000 (has extras) }\n" +
        "Broadcast completed: result=1, data=\"3\""
    ).toCommandHandlers()

    val runnableClientsService = RunnableClientsService(testRootDisposable)

    val setWatchFaceCommandHandler: CommandHandler = { device, receiver ->
      runnableClientsService.startClient(device, appId)
      receiver.addOutput("Broadcasting: Intent { act=com.google.android.wearable.app.DEBUG_SURFACE flg=0x400000 (has extras) }\n" +
                         "Broadcast completed: result=1, data=\"Favorite Id=[2] Runtime=[1]\"")
    }

    val processTerminatedLatch = CountDownLatch(2)
    val unsetWatchFaceCommandHandler: CommandHandler = { device, receiver ->
      runnableClientsService.stopClient(device, appId)
      receiver.addOutput("Broadcast completed: result=1")
      processTerminatedLatch.countDown()
    }

    val device = getMockDevice(
      commandHandlers +
      (setWatchFace to setWatchFaceCommandHandler) +
      (unsetWatchFace to unsetWatchFaceCommandHandler)
    )

    Mockito.`when`(
      device.forceStop(appId)
    ).thenAnswer {
      processTerminatedLatch.countDown()
    }

    val app = createApp(device, appId, servicesName = listOf(componentName), activitiesName = emptyList())
    val appInstaller = TestApplicationInstaller(appId, app)
    // Mock app installation.
    Mockito.doReturn(appInstaller).`when`(executor).getApplicationInstaller(any())

    // We expect the debugger to fail to attach, and we catch the corresponding exception. That happens only in this test as we
    // mocked DebuggerManagerEx to fail above.
    try {
      executor.doOnDevices(listOf(device)).blockingGet(30, TimeUnit.SECONDS)
    } catch (e: Throwable){
      if (e.cause !is ExecutionException || e.cause?.message != "Exception on debug start") {
        throw  e
      }
    }
    Mockito.verify(device, Mockito.atLeastOnce()).forceStop(appId)
  }

  @Test
  fun testWatchFaceProcessHandler() {
    val processHandler = WatchFaceProcessHandler(Mockito.mock(ConsoleView::class.java), false)
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
    Mockito.verify(device, Mockito.times(1)).executeShellCommand(
      commandsCaptor.capture(),
      any(IShellOutputReceiver::class.java),
      any(),
      any()
    )
    val commands = commandsCaptor.allValues

    // Unset watch face
    assertThat(commands[0]).isEqualTo(unsetWatchFace)
  }
}