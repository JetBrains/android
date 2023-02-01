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

import com.android.ddmlib.CollectingOutputReceiver
import com.android.ddmlib.IDevice
import com.android.tools.idea.execution.common.AndroidExecutionException
import com.android.tools.idea.run.ConsolePrinter
import com.android.tools.idea.run.activity.StartActivityFlagsProvider
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import kotlin.test.fail


@RunWith(JUnit4::class)
class ActivityLaunchTaskTest {
  @Mock lateinit var project: Project
  @Mock lateinit var device: IDevice
  @Mock lateinit var executor: Executor
  @Mock lateinit var printer: ConsolePrinter
  @Mock lateinit var handler: ProcessHandler
  @Mock
  lateinit var indicator: ProgressIndicator
  @Mock
  lateinit var env: ExecutionEnvironment

  init {
    MockitoAnnotations.initMocks(this)
    Mockito.`when`(env.project).thenReturn(project)
    Mockito.`when`(env.executor).thenReturn(executor)
  }

  @Test
  fun successful() {
    val launchTask = TestActivityLaunchTask(
      applicationId = "com.app.debug",
      activity = "com.app.Activity",
      description = "test launching activity"
    ) { }
    launchTask.run(LaunchContext(env, device, printer, handler, indicator))
  }

  @Test
  fun unableToDetermineLaunchActivity() {
    val launchTask = TestActivityLaunchTask(
      applicationId = "com.app.debug",
      activity = null,
      description = "test launching activity"
    ) { }
    try {
      launchTask.run(LaunchContext(env, device, printer, handler, indicator))
      fail("Run should fail")
    }
    catch (e: AndroidExecutionException) {
      assertThat(e.errorId).isEqualTo(ActivityLaunchTask.UNABLE_TO_DETERMINE_LAUNCH_ACTIVITY)
      assertThat(e.message).isEqualTo("Unable to determine activity name")
    }
  }

  @Test
  fun activityDoesNotExist() {
    val launchTask = TestActivityLaunchTask(
      applicationId = "com.app.debug",
      activity = "com.app.Version2Activity",
      description = "test launching activity"
    ) {
      val output = "Activity class {com.app.debug/com.app.Version2Activity} does not exist. Other output".toByteArray(Charsets.UTF_8)
      it.addOutput(output, 0, output.size)
    }
    try {
      launchTask.run(LaunchContext(env, device, printer, handler, indicator))
      fail("Run should fail")
    }
    catch (e: AndroidExecutionException) {
      assertThat(e.errorId).isEqualTo(ActivityLaunchTask.ACTIVITY_DOES_NOT_EXIST)
      assertThat(e.message).isEqualTo("Activity class {com.app.debug/com.app.Version2Activity} does not exist")
    }
  }

  @Test
  fun unrecognizedError() {
    val launchTask = TestActivityLaunchTask(
      applicationId = "com.app.debug",
      activity = "com.app.Version2Activity",
      description = "test launching activity"
    ) { throw ExecutionException("Something bad happened while launching Activity class {com.app.debug/com.app.Version2Activity}.") }
    try {
      launchTask.run(LaunchContext(env, device, printer, handler, indicator))
      fail("Run should fail")
    }
    catch (e: ExecutionException) {
      assertThat(e.message).isEqualTo("Something bad happened while launching Activity class {com.app.debug/com.app.Version2Activity}.")
    }
  }
}

object StubStartActivityFlagsProvider : StartActivityFlagsProvider {
  override fun getFlags(device: IDevice) = ""
}

class TestActivityLaunchTask(
  applicationId: String,
  private val activity: String?,
  private val description: String,
  private val testExecuteShellCommand: (collectingOutputReceiver: CollectingOutputReceiver) -> Unit
) : ActivityLaunchTask(applicationId, StubStartActivityFlagsProvider) {
  override fun getQualifiedActivityName(device: IDevice, printer: ConsolePrinter) = activity
  override fun getId() = "TEST_LAUNCH"
  override fun getDescription() = description

  override fun executeShellCommand(launchContext: LaunchContext,
                                   printer: ConsolePrinter?,
                                   device: IDevice?,
                                   command: String?,
                                   collectingOutputReceiver: CollectingOutputReceiver) {
    testExecuteShellCommand(collectingOutputReceiver)
  }
}