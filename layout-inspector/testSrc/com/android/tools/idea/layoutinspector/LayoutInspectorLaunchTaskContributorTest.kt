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
package com.android.tools.idea.layoutinspector

import com.android.ddmlib.IDevice
import com.android.ddmlib.testing.FakeAdbRule
import com.android.fakeadbserver.DeviceState
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.layoutinspector.pipeline.adb.AdbUtils
import com.android.tools.idea.layoutinspector.pipeline.adb.FakeShellCommandHandler
import com.android.tools.idea.layoutinspector.pipeline.adb.findDevice
import com.android.tools.idea.run.AndroidLaunchTasksProvider
import com.android.tools.idea.run.AndroidProcessHandler
import com.android.tools.idea.run.AndroidRemoteDebugProcessHandler
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.DefaultStudioProgramRunner
import com.android.tools.idea.run.LaunchOptions
import com.android.tools.idea.run.tasks.LaunchContext
import com.android.tools.idea.run.tasks.LaunchTask
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.debugger.DebuggerManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.`when`

private const val PROCESS_NAME = "com.example.p1"

class LayoutInspectorLaunchTaskContributorTest {
  private val commandHandler = FakeShellCommandHandler()
  private val projectRule = AndroidProjectRule.inMemory()
  private val adbRule = FakeAdbRule().withDeviceCommandHandler(commandHandler)

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(adbRule)!!

  @Test
  fun testLaunchWithoutDebugAttributes() {
    val (iDevice, task) = createLaunchTask(MODERN_DEVICE, debugAttributes = false)

    // Start the process
    val project = projectRule.project
    val handler = AndroidProcessHandler(project, PROCESS_NAME)
    val status = ProcessHandlerLaunchStatus(handler)
    val launchContext = LaunchContext(project, DefaultRunExecutor(), iDevice, status, mock(), handler, mock())
    task.run(launchContext)
    handler.startNotify()

    // Make sure the debug attributes are untouched.
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(0)

    // Kill process p1 and check that the debug attributes are still untouched.
    handler.killProcess()
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(0)
  }

  @Test
  fun testLaunchWithDebugAttributes() {
    val (iDevice, task) = createLaunchTask(MODERN_DEVICE, debugAttributes = true)

    // Start the process
    val project = projectRule.project
    val handler = AndroidProcessHandler(project, PROCESS_NAME)
    val status = ProcessHandlerLaunchStatus(handler)
    val launchContext = LaunchContext(project, DefaultRunExecutor(), iDevice, status, mock(), handler, mock())
    task.run(launchContext)
    handler.startNotify()

    // Make sure the debug attributes are set.
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isEqualTo(PROCESS_NAME)
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(1)

    // Kill process p1 and check that the debug attributes are reset.
    handler.killProcess()
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isNull()
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(2)
  }

  @Test
  fun testLaunchWithDebugAttributesOnLegacyDevice() {
    val (iDevice, task) = createLaunchTask(LEGACY_DEVICE, debugAttributes = true)

    // Start the process
    val project = projectRule.project
    val handler = AndroidProcessHandler(project, PROCESS_NAME)
    val status = ProcessHandlerLaunchStatus(handler)
    val launchContext = LaunchContext(project, DefaultRunExecutor(), iDevice, status, mock(), handler, mock())
    task.run(launchContext)
    handler.startNotify()

    // Make sure the debug attributes are untouched.
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(0)

    // Kill process p1 and check that the debug attributes are still untouched.
    handler.killProcess()
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(0)
  }

  @Test
  fun testLaunchDebugSessionWithDebugAttributes() {
    val (iDevice, task) = createLaunchTask(MODERN_DEVICE, debugAttributes = true)

    // Start the process
    val project = projectRule.project
    val handler = AndroidProcessHandler(project, PROCESS_NAME)
    val status = ProcessHandlerLaunchStatus(handler)
    val launchContext = LaunchContext(project, DefaultRunExecutor(), iDevice, status, mock(), handler, mock())
    task.run(launchContext)
    handler.startNotify()

    // Make sure the debug attributes are set.
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isEqualTo(PROCESS_NAME)
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(1)

    // Simulate that the process was started in the debugger.
    // The ProcessHandler will be switched See ConnectJavaDebuggerTask.launchDebugger.
    val debugManager = projectRule.mockProjectService(DebuggerManager::class.java)
    `when`(debugManager.getDebugProcess(any(ProcessHandler::class.java))).thenReturn(mock())
    val debugHandler = AndroidRemoteDebugProcessHandler(project)
    debugHandler.startNotify()
    status.processHandler = debugHandler
    handler.killProcess()

    // The debug attributes should still be set.
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isEqualTo(PROCESS_NAME)
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(1)

    // Simulate the debug session ended and check that the debug attributes are reset.
    debugHandler.destroyProcess()
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isNull()
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(2)
  }

  private fun createLaunchTask(
    device: DeviceDescriptor,
    debugAttributes: Boolean
  ): Pair<IDevice, LaunchTask> {
    val processName = "com.example.p1"
    adbRule.attachDevice(
      device.serial, device.manufacturer, device.model, device.version, device.apiLevel.toString(), DeviceState.HostConnectionType.USB)

    val project = projectRule.project
    val ex = DefaultDebugExecutor.getDebugExecutorInstance()
    val runner = DefaultStudioProgramRunner()
    val env = ExecutionEnvironment(ex, runner, mock(), project)
    val applicationIdProvider: ApplicationIdProvider = object : ApplicationIdProvider {
      override fun getPackageName(): String = processName
      override fun getTestPackageName(): String? = null
    }

    val launchOptions = LaunchOptions.builder()
      .setInspectionWithoutActivityRestart(debugAttributes)
      .build()

    val launchTaskProvider = AndroidLaunchTasksProvider(
      mock<AndroidRunConfiguration>(),
      env,
      AndroidFacet.getInstance(projectRule.module)!!,
      applicationIdProvider,
      mock(),
      launchOptions
    )

    val adb = AdbUtils.getAdbFuture(project).get()
    val iDevice = adb.findDevice(device)!!
    val tasks = launchTaskProvider.getTasks(iDevice, mock(), mock())
      .filterIsInstance(LayoutInspectorLaunchTask::class.java)

    // Make sure the LayoutInspectorLaunchTaskContributor is registered.
    assertThat(tasks).hasSize(1)
    return iDevice to tasks.single()
  }
}
