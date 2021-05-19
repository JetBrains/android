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
import com.android.ide.common.gradle.model.IdeAndroidArtifact
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.run.ConsolePrinter
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.process.ProcessHandler
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations

/**
 * Unit tests for [AndroidTestApplicationLaunchTask].
 */
@RunWith(JUnit4::class)
class AndroidTestApplicationLaunchTaskTest {

  @Mock lateinit var mockAndroidArtifact: IdeAndroidArtifact
  @Mock lateinit var mockProcessHandler: ProcessHandler
  @Mock lateinit var mockPrinter: ConsolePrinter

  @Before
  fun setup() {
    MockitoAnnotations.initMocks(this)
  }

  private fun createMockDevice(version: AndroidVersion): IDevice {
    val mockDevice = mock(IDevice::class.java)
    `when`(mockDevice.version).thenReturn(version)
    return mockDevice
  }

  @Test
  fun statusReporterModeRawTextShouldBeUsedInApiLevel25() {
    val mockDevice = createMockDevice(AndroidVersion(25))
    val launchTask = AndroidTestApplicationLaunchTask.allInPackageTest(
      "instrumentationTestRunner",
      "testApplicationId",
      /*waitForDebugger=*/ false,
      "instrumentationOptions",
      mockAndroidArtifact,
      mockProcessHandler,
      mockPrinter,
      mockDevice,
      "packageName")

    val runner = launchTask.createRemoteAndroidTestRunner(mockDevice)

    assertThat(runner.amInstrumentCommand).contains("-r")
    assertThat(runner.amInstrumentCommand).doesNotContain("-m")
  }

  @Test
  fun statusReporterModeProtoStdShouldBeUsedInApiLevel26() {
    val mockDevice = createMockDevice(AndroidVersion(26))
    val launchTask = AndroidTestApplicationLaunchTask.allInPackageTest(
      "instrumentationTestRunner",
      "testApplicationId",
      /*waitForDebugger=*/ false,
      "instrumentationOptions",
      mockAndroidArtifact,
      mockProcessHandler,
      mockPrinter,
      mockDevice,
      "packageName")

    val runner = launchTask.createRemoteAndroidTestRunner(mockDevice)

    assertThat(runner.amInstrumentCommand).contains("-m")
    assertThat(runner.amInstrumentCommand).doesNotContain("-r")
  }
}