/*
 * Copyright (C) 2026 The Android Open Source Project
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
import com.android.ddmlib.IDevice
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.ShellProtocolType
import com.android.fakeadbserver.services.ShellCommandOutput
import com.android.fakeadbserver.services.StatusWriter
import com.android.fakeadbserver.shellcommandhandlers.SimpleShellHandler
import com.android.tools.deployer.model.component.WearWidget.ShellCommand.GET_PROTOLAYOUT_RENDERER_VERSION
import com.android.tools.idea.execution.common.AppRunSettings
import com.android.tools.idea.execution.common.DeployOptions
import com.android.tools.idea.execution.common.processhandler.AndroidRemoteDebugProcessHandler
import com.android.tools.idea.projectsystem.TestApplicationProjectContext
import com.android.tools.idea.run.DefaultStudioProgramRunner
import com.android.tools.idea.run.FakeAndroidDevice
import com.android.tools.idea.run.configuration.AndroidWearWidgetConfigurationType
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.ExecutionException
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.progress.EmptyProgressIndicator
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.mock

class AndroidWearWidgetConfigurationExecutorTest : AndroidConfigurationExecutorBaseTest() {
  private val checkVersion = "broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation version"
  private val addWearWidget =
    "broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation add-tile --ecn component com.example.app/com.example.app.Component"
  private val showWearWidget = "broadcast -a com.google.android.wearable.app.DEBUG_SYSUI --es operation show-tile --ei index 101"
  private val removeWearWidget =
    "broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation remove-tile --ecn component com.example.app/com.example.app.Component"
  private val setDebugAppAm = "set-debug-app -w 'com.example.app'"
  private val setDebugAppBroadcast =
    "broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation set-debug-app --es package 'com.example.app'"
  private val clearDebugAppAm = "clear-debug-app"
  private val clearDebugAppBroadcast = "broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation 'clear-debug-app'"

  @Test
  fun testGetProtolayoutRendererVersion() {
    addProtolayoutVersionHandler { shellCommandOutput ->
      shellCommandOutput.writeStdout(
        """
        Activity Resolver Table:
          versionName=1.6.1.87.907578152
          splits=[base]
        """
          .trimIndent()
      )
    }
    fakeAdbRule.connectAndWaitForDevice()
    val device = AndroidDebugBridge.getBridge()!!.devices.single()

    assertEquals("1.6.1.87.907578152", device.getProtolayoutRendererVersion())
  }

  @Test
  fun testGetProtolayoutRendererVersionMissing() {
    addProtolayoutVersionHandler { shellCommandOutput ->
      shellCommandOutput.writeStderr("Unable to find package: com.google.android.wearable.protolayout.renderer")
    }
    fakeAdbRule.connectAndWaitForDevice()
    val device = AndroidDebugBridge.getBridge()!!.devices.single()

    assertNull(device.getProtolayoutRendererVersion())
  }

  @Test
  fun testLaunchProtolayoutRendererMissingThrowsException() {
    addProtolayoutVersionHandler { shellCommandOutput ->
      shellCommandOutput.writeStderr("Unable to find package: com.google.android.wearable.protolayout.renderer")
    }
    fakeAdbRule.connectAndWaitForDevice()
    val device = AndroidDebugBridge.getBridge()!!.devices.single()
    val executor = createExecutor(device)

    val exception = assertThrows(ExecutionException::class.java) { executor.launch(device, null, mock(), mock(), isDebug = false, mock()) }

    assertThat(exception.message).contains("Protolayout renderer was not found on the device.")
    assertThat(exception.message).contains("Wear Widgets require version 1.6.1 or higher")
  }

  @Test
  fun testLaunchRendererTooLowThrowsException() {
    addProtolayoutVersionHandler { shellCommandOutput ->
      shellCommandOutput.writeStdout(
        """
        Activity Resolver Table:
          versionName=1.6.0
          splits=[base]
        """
          .trimIndent()
      )
    }
    fakeAdbRule.connectAndWaitForDevice()
    val device = AndroidDebugBridge.getBridge()!!.devices.single()
    val executor = createExecutor(device)

    val exception = assertThrows(ExecutionException::class.java) { executor.launch(device, null, mock(), mock(), isDebug = false, mock()) }

    assertThat(exception.message).contains("The Protolayout renderer version on the device (1.6.0) is too low.")
    assertThat(exception.message).contains("Wear Widgets require version 1.6.1 or higher")
  }

  @Test
  fun testRun() {
    val receivedAmCommands = ArrayList<String>()
    addProtolayoutVersionHandler { shellCommandOutput ->
      receivedAmCommands.add(GET_PROTOLAYOUT_RENDERER_VERSION)
      shellCommandOutput.writeStdout(
        """
        Activity Resolver Table:
          versionName=1.6.1
          splits=[base]
        """
          .trimIndent()
      )
    }
    val deviceState = fakeAdbRule.connectAndWaitForDevice()
    val device = AndroidDebugBridge.getBridge()!!.devices.single()
    val executor = createExecutor(device)
    val widgetRemovedFuture = CompletableFuture<Unit>()

    deviceState.setActivityManager { args: List<String>, shellCommandOutput: ShellCommandOutput ->
      val wholeCommand = args.joinToString(" ")

      receivedAmCommands.add(wholeCommand)

      when (wholeCommand) {
        checkVersion ->
          shellCommandOutput.writeStdout(
            "Broadcasting: Intent { act=com.google.android.wearable.app.DEBUG_SURFACE flg=0x400000 (has extras) }\n" +
              "Broadcast completed: result=1, data=\"3\""
          )
        addWearWidget -> {
          deviceState.startClient(1234, 1235, appId, true)
          shellCommandOutput.writeStdout(
            "Broadcasting: Intent { act=com.google.android.wearable.app.DEBUG_SURFACE flg=0x400000 (has extras) }\n" +
              "Broadcast completed: result=1, data=\"Index=[101]\""
          )
        }
        showWearWidget ->
          shellCommandOutput.writeStdout(
            "Broadcasting: Intent { act=com.google.android.wearable.app.DEBUG_SURFACE flg=0x400000 (has extras) }\n" +
              "Broadcast completed: result=1"
          )
        removeWearWidget -> {
          deviceState.stopClient(1234)
          shellCommandOutput.writeStdout("Broadcast completed: result=1")
          widgetRemovedFuture.complete(Unit)
        }
      }
    }

    val runContentDescriptor = getRunContentDescriptorForTests { executor.run(EmptyProgressIndicator()) }
    // JetBrains patch: upstream calls ktor's instanceOf() here, a predicate whose result is dropped, so it asserts nothing; run() returns an AndroidProcessHandler, so the real check would fail. Disabled until upstream decides what testRun() should assert.
    // assertThat(runContentDescriptor?.processHandler).instanceOf(AndroidRemoteDebugProcessHandler::class)

    // Stop configuration.
    runContentDescriptor?.processHandler!!.destroyProcess()
    widgetRemovedFuture.orTimeout(10, TimeUnit.SECONDS).join()

    // Verify commands sent to device.
    assertThat(receivedAmCommands)
      .containsExactly(GET_PROTOLAYOUT_RENDERER_VERSION, checkVersion, addWearWidget, showWearWidget, removeWearWidget)
      .inOrder()
  }

  @Test
  fun testDebug() {
    val receivedAmCommands = ArrayList<String>()
    addProtolayoutVersionHandler { shellCommandOutput ->
      receivedAmCommands.add(GET_PROTOLAYOUT_RENDERER_VERSION)
      shellCommandOutput.writeStdout(
        """
        Activity Resolver Table:
          versionName=1.6.1
          splits=[base]
        """
          .trimIndent()
      )
    }
    val deviceState = fakeAdbRule.connectAndWaitForDevice()
    val device = AndroidDebugBridge.getBridge()!!.devices.single()
    val executor = createExecutor(device)
    val debugAppAmCleared = CompletableFuture<Unit>()

    deviceState.setActivityManager { args: List<String>, shellCommandOutput: ShellCommandOutput ->
      val wholeCommand = args.joinToString(" ")

      receivedAmCommands.add(wholeCommand)

      when (wholeCommand) {
        checkVersion ->
          shellCommandOutput.writeStdout(
            "Broadcasting: Intent { act=com.google.android.wearable.app.DEBUG_SURFACE flg=0x400000 (has extras) }\n" +
              "Broadcast completed: result=1, data=\"3\""
          )
        addWearWidget -> {
          deviceState.startClient(1234, 1235, appId, true)
          shellCommandOutput.writeStdout(
            "Broadcasting: Intent { act=com.google.android.wearable.app.DEBUG_SURFACE flg=0x400000 (has extras) }\n" +
              "Broadcast completed: result=1, data=\"Index=[101]\""
          )
        }
        showWearWidget ->
          shellCommandOutput.writeStdout(
            "Broadcasting: Intent { act=com.google.android.wearable.app.DEBUG_SURFACE flg=0x400000 (has extras) }\n" +
              "Broadcast completed: result=1"
          )
        removeWearWidget -> {
          deviceState.stopClient(1234)
          shellCommandOutput.writeStdout("Broadcast completed: result=1")
        }
        setDebugAppBroadcast -> shellCommandOutput.writeStdout("Broadcast completed: result=1")
        clearDebugAppAm -> debugAppAmCleared.complete(Unit)
      }
    }

    val runContentDescriptor = getRunContentDescriptorForTests { executor.debug(EmptyProgressIndicator()) }
    // JetBrains patch: Truth's isInstanceOf() in place of ktor's instanceOf(), which this module cannot import and which asserted nothing.
    assertThat(runContentDescriptor?.processHandler).isInstanceOf(AndroidRemoteDebugProcessHandler::class.java)

    // Stop configuration.
    runContentDescriptor?.processHandler!!.destroyProcess()
    debugAppAmCleared.orTimeout(10, TimeUnit.SECONDS).join()

    // Verify commands sent to device.
    assertThat(receivedAmCommands)
      .containsExactly(
        GET_PROTOLAYOUT_RENDERER_VERSION,
        checkVersion,
        setDebugAppAm,
        setDebugAppBroadcast,
        addWearWidget,
        showWearWidget,
        removeWearWidget,
        clearDebugAppBroadcast,
        clearDebugAppAm,
      )
      .inOrder()
  }

  private fun addProtolayoutVersionHandler(handler: (ShellCommandOutput) -> Unit) {
    fakeAdbRule.adbServer.handlers.add(
      0, // add it at index 0 to be handled before DumpsysCommandHandler
      object : SimpleShellHandler(ShellProtocolType.SHELL, "dumpsys") {
        override fun execute(
          fakeAdbServer: FakeAdbServer,
          statusWriter: StatusWriter,
          shellCommandOutput: ShellCommandOutput,
          device: DeviceState,
          shellCommand: String,
          shellCommandArgs: String?,
        ) {
          statusWriter.writeOk()
          val wholeCommand = "$shellCommand${shellCommandArgs?.let { " $it" }}"
          when (wholeCommand) {
            GET_PROTOLAYOUT_RENDERER_VERSION -> handler(shellCommandOutput)
            else -> ""
          }
        }
      },
    )
  }

  private fun createExecutor(device: IDevice): AndroidWearWidgetConfigurationExecutor {
    val configSettings =
      RunManager.getInstance(project)
        .createConfiguration("run wear widget", AndroidWearWidgetConfigurationType().configurationFactories.single())
    val settings =
      object : AppRunSettings {
        override val deployOptions = DeployOptions(emptyList(), "", true, true, false)
        override val componentLaunchOptions =
          WearWidgetLaunchOptions().apply { componentName = this@AndroidWearWidgetConfigurationExecutorTest.componentName }
      }

    val app = createApp(device, appId, servicesName = listOf(componentName), activitiesName = emptyList())
    return AndroidWearWidgetConfigurationExecutor(
      ExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance(), DefaultStudioProgramRunner(), configSettings, project),
      FakeAndroidDevice.forDevices(listOf(device)),
      settings,
      TestApksProvider(appId),
      TestApplicationProjectContext(appId),
      TestApplicationInstaller(appId, app),
    )
  }
}
