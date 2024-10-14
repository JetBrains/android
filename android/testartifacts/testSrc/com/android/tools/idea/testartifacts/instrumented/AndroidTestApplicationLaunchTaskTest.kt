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
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.AndroidTestSuiteView
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ProjectRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

/**
 * Unit tests for [AndroidTestApplicationLaunchTask].
 */
@RunWith(JUnit4::class)
class AndroidTestApplicationLaunchTaskTest {

  @get:Rule
  val mockitoJunitRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)
  @get:Rule
  val projectRule = ProjectRule()

  @Mock
  lateinit var mockProcessHandler: AndroidProcessHandler

  @Mock
  lateinit var mockConsole: AndroidTestSuiteView

  private fun createMockDevice(version: AndroidVersion): IDevice {
    val mockDevice = mock(IDevice::class.java)
    whenever(mockDevice.version).thenReturn(version)
    return mockDevice
  }

  private fun createLaunchTask(): AndroidTestApplicationLaunchTask {
    return AndroidTestApplicationLaunchTask(
      null,
      "instrumentationTestRunner",
      "testApplicationId",
      null,
      /*waitForDebugger=*/ false,
      "instrumentationOptions"
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
    whenever(mockDevice.serialNumber).thenReturn("1234")

    val launchTask = createLaunchTask()
    launchTask.run(mockDevice, mockConsole, mockProcessHandler)

    verify(mockConsole).print(eq("Running tests\n"), any())
    verify(mockDevice).forceStop(eq("testApplicationId"))
  }
}