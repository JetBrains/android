/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.run.tasks

import com.android.ddmlib.IDevice
import com.android.tools.idea.run.ConsolePrinter
import com.android.tools.idea.run.activity.StartActivityFlagsProvider
import com.android.tools.idea.run.tasks.ActivityLaunchTask.UNKNOWN_ACTIVITY_LAUNCH_TASK_ERROR
import com.android.tools.idea.run.util.LaunchStatus
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.Executor
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.util.concurrent.TimeUnit


@RunWith(JUnit4::class)
class ActivityLaunchTaskTest {
  @Mock lateinit var project: Project
  @Mock lateinit var device: IDevice
  @Mock lateinit var executor: Executor
  @Mock lateinit var launchStatus: LaunchStatus
  @Mock lateinit var printer: ConsolePrinter
  @Mock lateinit var handler: ProcessHandler
  @Mock lateinit var indicator: ProgressIndicator

  init {
    MockitoAnnotations.initMocks(this)
  }

  @Test
  fun successful() {
    val launchTask = TestActivityLaunchTask(
      applicationId = "com.app.debug",
      activity = "com.app.Activity",
      description = "test launching activity"
    ) { true }
    val result = launchTask.run(LaunchContext(project, executor, device, launchStatus, printer, handler, indicator))
    assertThat(result.result).isEqualTo(LaunchResult.Result.SUCCESS)
    assertThat(result.errorId).isEmpty()
    assertThat(result.message).isEmpty()
  }

  @Test
  fun unableToDetermineLaunchActivity() {
    val launchTask = TestActivityLaunchTask(
      applicationId = "com.app.debug",
      activity = null,
      description = "test launching activity"
    ) { false }
    val result = launchTask.run(LaunchContext(project, executor, device, launchStatus, printer, handler, indicator))
    assertThat(result.result).isEqualTo(LaunchResult.Result.ERROR)
    assertThat(result.errorId).isEqualTo(ActivityLaunchTask.UNABLE_TO_DETERMINE_LAUNCH_ACTIVITY)
    assertThat(result.message).isEqualTo("Error test launching activity")
  }

  @Test
  fun activityDoesNotExist() {
    val launchTask = TestActivityLaunchTask(
      applicationId = "com.app.debug",
      activity = "com.app.Version2Activity",
      description = "test launching activity"
    ) { printer ->
      printer.stderr("Activity class {com.app.debug/com.app.Version2Activity} does not exist.")
      false
    }
    val result = launchTask.run(LaunchContext(project, executor, device, launchStatus, printer, handler, indicator))
    assertThat(result.result).isEqualTo(LaunchResult.Result.ERROR)
    assertThat(result.errorId).isEqualTo(ActivityLaunchTask.ACTIVITY_DOES_NOT_EXIST)
    assertThat(result.message).isEqualTo("Error test launching activity")
  }

  @Test
  fun unrecognizedError() {
    val launchTask = TestActivityLaunchTask(
      applicationId = "com.app.debug",
      activity = "com.app.Version2Activity",
      description = "test launching activity"
    ) { printer ->
      printer.stderr("Something bad happened while launching Activity class {com.app.debug/com.app.Version2Activity}.")
      false
    }
    val result = launchTask.run(LaunchContext(project, executor, device, launchStatus, printer, handler, indicator))
    assertThat(result.result).isEqualTo(LaunchResult.Result.ERROR)
    assertThat(result.errorId).isEqualTo(UNKNOWN_ACTIVITY_LAUNCH_TASK_ERROR)
    assertThat(result.message).isEqualTo("Error test launching activity")
  }
}

object StubStartActivityFlagsProvider : StartActivityFlagsProvider {
  override fun getFlags(device: IDevice) = ""
}

class TestActivityLaunchTask(
  applicationId: String,
  private val activity: String?,
  private val description: String,
  private val executeShellCommand: (printer: ConsolePrinter) -> Boolean
) : ActivityLaunchTask(applicationId, StubStartActivityFlagsProvider) {
  override fun getQualifiedActivityName(device: IDevice, printer: ConsolePrinter) = activity
  override fun getId() = "TEST_LAUNCH"
  override fun getDescription() = description
  override fun executeShellCommand(command: String,
                                   device: IDevice,
                                   launchStatus: LaunchStatus,
                                   printer: ConsolePrinter,
                                   timeout: Long,
                                   timeoutUnit: TimeUnit): Boolean {
    return executeShellCommand(printer)
  }
}