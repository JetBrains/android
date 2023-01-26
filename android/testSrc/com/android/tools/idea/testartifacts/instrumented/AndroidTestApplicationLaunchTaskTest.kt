/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented

import com.android.ddmlib.IDevice
import com.android.ddmlib.testrunner.ITestRunListener
import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler
import com.android.tools.idea.run.ConsolePrinter
import com.android.tools.idea.run.tasks.LaunchContext
import com.android.tools.idea.run.util.LaunchStatus
import com.android.tools.idea.testartifacts.instrumented.configuration.AndroidTestConfiguration
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.quality.Strictness
import java.util.concurrent.ExecutorService

/**
 * Unit tests for [AndroidTestApplicationLaunchTask].
 */
@RunWith(JUnit4::class)
class AndroidTestApplicationLaunchTaskTest {

  @get:Rule val mockitoJunitRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

  @Mock lateinit var mockProcessHandler: AndroidProcessHandler
  @Mock lateinit var mockPrinter: ConsolePrinter
  @Mock lateinit var mockITestRunListener: ITestRunListener
  @Mock lateinit var mockLaunchContext: LaunchContext
  @Mock lateinit var mockLaunchStatus: LaunchStatus

  private val directExecutor: ExecutorService = MoreExecutors.newDirectExecutorService()

  private fun createMockDevice(version: AndroidVersion): IDevice {
    val mockDevice = mock(IDevice::class.java)
    whenever(mockDevice.version).thenReturn(version)
    return mockDevice
  }

  private fun createLaunchTask(): AndroidTestApplicationLaunchTask {
    return AndroidTestApplicationLaunchTask(
      "instrumentationTestRunner",
      "testApplicationId",
      null,
      /*waitForDebugger=*/ false,
      "instrumentationOptions",
      listOf(mockITestRunListener),
      myBackgroundTaskExecutor = directExecutor::submit,
      myAndroidTestConfigurationProvider = { AndroidTestConfiguration() },
    ) {}
  }

  @Test
  fun statusReporterModeRawTextShouldBeUsedInApiLevel25() {
    val mockDevice = createMockDevice(AndroidVersion(25))
    val launchTask = createLaunchTask()

    val runner = launchTask.createRemoteAndroidTestRunner(mockDevice)

    assertThat(runner.amInstrumentCommand).contains("-r")
    assertThat(runner.amInstrumentCommand).doesNotContain("-m")
  }

  @Test
  fun statusReporterModeProtoStdShouldBeUsedInApiLevel26() {
    val mockDevice = createMockDevice(AndroidVersion(26))
    val launchTask = createLaunchTask()

    val runner = launchTask.createRemoteAndroidTestRunner(mockDevice)

    assertThat(runner.amInstrumentCommand).contains("-m")
    assertThat(runner.amInstrumentCommand).doesNotContain("-r")
  }

  @Test
  fun run() {
    val mockDevice = createMockDevice(AndroidVersion(26))
    whenever(mockLaunchContext.device).thenReturn(mockDevice)
    whenever(mockLaunchContext.consolePrinter).thenReturn(mockPrinter)
    whenever(mockLaunchContext.launchStatus).thenReturn(mockLaunchStatus)
    whenever(mockLaunchStatus.processHandler).thenReturn(mockProcessHandler)

    val launchTask = createLaunchTask()
    launchTask.run(mockLaunchContext)

    verify(mockPrinter).stdout(eq("Running tests\n"))
    verify(mockProcessHandler).detachDevice(eq(mockDevice))
  }
}