/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device.benchmark

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.StudioPathManager
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.verify

private const val SERIAL_NUMBER = "abc123"

/** Tests the [MirroringBenchmarkerAppInstaller] class. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(JUnit4::class)
class MirroringBenchmarkerAppInstallerTest {
  @get:Rule
  val projectRule = AndroidProjectRule.onDisk()
  private val adb: MirroringBenchmarkerAppInstaller.AdbWrapper = mock()

  lateinit var installer: MirroringBenchmarkerAppInstaller
  @Before
  fun setUp() {
    installer = MirroringBenchmarkerAppInstaller(projectRule.project, SERIAL_NUMBER, adb)
  }

  @Test
  fun installationFromPrebuilts_success() = runBlockingTest {
    val prebuiltPath = StudioPathManager.resolvePathFromSourcesRoot(
      "prebuilts/tools/common/mirroring-benchmarker/mirroring-benchmarker.apk")
    whenever(adb.install(SERIAL_NUMBER, prebuiltPath)).thenReturn(true)
    assertThat(installer.installBenchmarkingApp()).isTrue()

    verify(adb).install(SERIAL_NUMBER, prebuiltPath)
  }

  @Test
  fun installationFromPrebuilts_failure() = runBlockingTest {
    val prebuiltPath = StudioPathManager.resolvePathFromSourcesRoot(
      "prebuilts/tools/common/mirroring-benchmarker/mirroring-benchmarker.apk")
    whenever(adb.install(SERIAL_NUMBER, prebuiltPath)).thenReturn(false)

    assertThat(installer.installBenchmarkingApp()).isFalse()

    verify(adb).install(SERIAL_NUMBER, prebuiltPath)
  }

  @Test
  fun launchBenchmarkingApp_success() = runBlockingTest {
    val command = "am start -n com.android.tools.screensharing.benchmark/.InputEventRenderingActivity -f 65536"
    whenever(adb.shellCommand(SERIAL_NUMBER, command)).thenReturn(true)

    assertThat(installer.launchBenchmarkingApp()).isTrue()
    verify(adb).shellCommand(SERIAL_NUMBER, command)
  }

  @Test
  fun launchBenchmarkingApp_failure() = runBlockingTest {
    val command = "am start -n com.android.tools.screensharing.benchmark/.InputEventRenderingActivity -f 65536"
    whenever(adb.shellCommand(SERIAL_NUMBER, command)).thenReturn(false)

    assertThat(installer.launchBenchmarkingApp()).isFalse()
    verify(adb).shellCommand(SERIAL_NUMBER, command)
  }

  @Test
  fun uninstallBenchmarkingApp() = runBlockingTest {
    installer.uninstallBenchmarkingApp()

    verify(adb).uninstall(SERIAL_NUMBER)
  }
}
