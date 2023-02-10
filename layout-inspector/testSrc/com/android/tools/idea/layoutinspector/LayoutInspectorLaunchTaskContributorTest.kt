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
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.pipeline.adb.AdbUtils
import com.android.tools.idea.layoutinspector.pipeline.adb.FakeShellCommandHandler
import com.android.tools.idea.layoutinspector.pipeline.adb.findDevice
import com.android.tools.idea.layoutinspector.pipeline.appinspection.DebugViewAttributes
import com.android.tools.idea.run.AndroidLaunchTasksProvider
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.AndroidRunConfigurationType
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.DefaultStudioProgramRunner
import com.android.tools.idea.run.LaunchOptions
import com.android.tools.idea.run.tasks.LaunchContext
import com.android.tools.idea.run.tasks.LaunchTask
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.ExecutionManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain


private const val PROCESS_NAME = "com.example.p1"

class LayoutInspectorLaunchTaskContributorTest {
  private val commandHandler = FakeShellCommandHandler()
  private val projectRule = AndroidProjectRule.onDisk()
  private val adbRule = FakeAdbRule().withDeviceCommandHandler(commandHandler)
  private val adbService = AdbServiceRule(projectRule::project, adbRule)
  lateinit var env: ExecutionEnvironment


  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(adbRule).around(adbService)!!

  @Before
  fun resetAttributes() {
    DebugViewAttributes.reset()
    env = ExecutionEnvironmentBuilder.create(
      DefaultRunExecutor.getRunExecutorInstance(),
      AndroidRunConfiguration(projectRule.project, AndroidRunConfigurationType.getInstance().factory)
    ).build()
  }

  @Test
  fun testLaunchWithoutDebugAttributes() {
    val (iDevice, task) = createLaunchTask(MODERN_DEVICE, debugAttributes = false)

    // Start the process
    val project = projectRule.project
    val handler = AndroidProcessHandler(project, PROCESS_NAME)
    val launchContext = LaunchContext(env, iDevice, mock(), handler, mock())
    task.run(launchContext)
    handler.startNotify()

    // Make sure the debug attributes are untouched.
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(0)

    // Kill process p1 and check that the debug attributes are still untouched.
    handler.killProcess()
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(0)
  }

  @Test
  fun testLaunchWithDebugAttributes() = runWithFlagState(false) {
    val (iDevice, task) = createLaunchTask(MODERN_DEVICE, debugAttributes = true)

    // Start the process
    val project = projectRule.project
    val handler = AndroidProcessHandler(project, PROCESS_NAME)
    val launchContext = LaunchContext(env, iDevice, mock(), handler, mock())
    task.run(launchContext)
    project.messageBus.syncPublisher(ExecutionManager.EXECUTION_TOPIC).processStarted(DefaultRunExecutor.EXECUTOR_ID, env, handler)
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
  fun testLaunchWithDebugAttributesAlreadySet() = runWithFlagState(false) {
    commandHandler.debugViewAttributesApplicationPackage = PROCESS_NAME

    val (iDevice, task) = createLaunchTask(MODERN_DEVICE, debugAttributes = true)

    // Start the process
    val project = projectRule.project
    val handler = AndroidProcessHandler(project, PROCESS_NAME)
    val launchContext = LaunchContext(env, iDevice, mock(), handler, mock())
    task.run(launchContext)
    handler.startNotify()

    // Make sure the debug attributes are untouched.
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isEqualTo(PROCESS_NAME)
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(0)

    // Kill process p1 and check that the debug attributes are still untouched.
    handler.killProcess()
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isEqualTo(PROCESS_NAME)
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(0)
  }

  @Test
  fun testLaunchWithDebugAttributesOnLegacyDevice() {
    val (iDevice, task) = createLaunchTask(LEGACY_DEVICE, debugAttributes = true)

    // Start the process
    val project = projectRule.project
    val handler = AndroidProcessHandler(project, PROCESS_NAME)
    val launchContext = LaunchContext(env, iDevice, mock(), handler, mock())
    task.run(launchContext)
    handler.startNotify()

    // Make sure the debug attributes are untouched.
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(0)

    // Kill process p1 and check that the debug attributes are still untouched.
    handler.killProcess()
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(0)
  }

  @Test
  fun testPerDeviceViewDebugAttributesIsNotCleared() = runWithFlagState(true) {
    val (iDevice, task) = createLaunchTask(MODERN_DEVICE, debugAttributes = true)

    // Start the process
    val project = projectRule.project
    val handler = AndroidProcessHandler(project, PROCESS_NAME)
    val launchContext = LaunchContext(env, iDevice, mock(), handler, mock())
    task.run(launchContext)
    project.messageBus.syncPublisher(ExecutionManager.EXECUTION_TOPIC).processStarted(DefaultRunExecutor.EXECUTOR_ID, env, handler)
    handler.startNotify()

    // Make sure the debug attributes are set.
    assertThat(commandHandler.debugViewAttributes).isEqualTo("1")
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(1)

    // Kill process p1 and check that the debug attributes are not reset.
    handler.killProcess()

    assertThat(commandHandler.debugViewAttributes).isEqualTo("1")
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(1)
  }

  private fun runWithFlagState(desiredFlagState: Boolean, task: () -> Unit) {
    val flag = StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_AUTO_CONNECT_TO_FOREGROUND_PROCESS_ENABLED
    val flagPreviousState = flag.get()
    flag.override(desiredFlagState)

    task()

    // restore flag state
    flag.override(flagPreviousState)
  }

  private fun createLaunchTask(
    device: DeviceDescriptor,
    debugAttributes: Boolean
  ): Pair<IDevice, LaunchTask> {
    adbRule.attachDevice(device.serial, device.manufacturer, device.model, device.version, device.apiLevel.toString())

    val project = projectRule.project
    val ex = DefaultDebugExecutor.getDebugExecutorInstance()
    val runner = DefaultStudioProgramRunner()
    val env = ExecutionEnvironment(ex, runner, mock(), project)
    val applicationIdProvider: ApplicationIdProvider = object : ApplicationIdProvider {
      override fun getPackageName(): String = PROCESS_NAME
      override fun getTestPackageName(): String? = null
    }

    val configuration = AndroidRunConfigurationType.getInstance().factory.createTemplateConfiguration(project) as AndroidRunConfiguration

    configuration.INSPECTION_WITHOUT_ACTIVITY_RESTART = debugAttributes

    val launchTaskProvider = AndroidLaunchTasksProvider(
      configuration,
      env,
      AndroidFacet.getInstance(projectRule.module)!!,
      applicationIdProvider,
      mock(),
      LaunchOptions.builder().build()
    )

    val adb = AdbUtils.getAdbFuture(project).get()!!
    val iDevice = adb.findDevice(device)!!
    val tasks = launchTaskProvider.getTasks(iDevice)
      .filterIsInstance(LayoutInspectorLaunchTask::class.java)

    // Make sure the LayoutInspectorLaunchTaskContributor is registered.
    assertThat(tasks).hasSize(1)
    return iDevice to tasks.single()
  }
}
