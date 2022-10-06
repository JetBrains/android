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

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.argumentCaptor
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.inOrder
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.mockStatic
import com.android.tools.idea.util.StudioPathManager
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.progress.ProgressIndicator
import io.ktor.util.encodeBase64
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.verify
import java.io.InputStream
import java.nio.file.Paths
import kotlin.time.Duration.Companion.hours

private const val SERIAL_NUMBER = "abc123"

/** Tests the [MirroringBenchmarkerAppInstaller] class. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(JUnit4::class)
class MirroringBenchmarkerAppInstallerTest {
  @get:Rule
  val projectRule = AndroidProjectRule.onDisk()
  private val adb: MirroringBenchmarkerAppInstaller.AdbWrapper = mock()
  private val urlFileCache: UrlFileCache = mock()

  lateinit var installer: MirroringBenchmarkerAppInstaller
  @Before
  fun setUp() {
    installer = MirroringBenchmarkerAppInstaller(projectRule.project, SERIAL_NUMBER, adb)
    projectRule.replaceProjectService(UrlFileCache::class.java, urlFileCache)
  }

  @Test
  fun installationFromDownload() = runBlockingTest {
    val downloadPath = Paths.get("/help/i/am/trapped/in/a/unit/test/factory.apk")
    val relativePath = "common/mirroring-benchmarker/mirroring-benchmarker.apk"
    val basePrebuiltsUrl = "https://android.googlesource.com/platform/prebuilts/tools/+/refs/heads/mirror-goog-studio-main/"
    val apkUrl = "$basePrebuiltsUrl$relativePath?format=TEXT"  // Base-64 encoded

    val studioPathManager = mockStatic<StudioPathManager>(projectRule.testRootDisposable)
    studioPathManager.whenever<Any?> { StudioPathManager.isRunningFromSources() }.thenReturn(false)
    val indicator: ProgressIndicator = mock()
    whenever(urlFileCache.get(eq(apkUrl), anyLong(), any(), any())).thenReturn(downloadPath)
    whenever(adb.install(SERIAL_NUMBER, downloadPath)).thenReturn(true)

    assertThat(installer.installBenchmarkingApp(indicator)).isTrue()

    inOrder(indicator, urlFileCache, adb) {
      // Download
      verify(indicator).isIndeterminate = true
      verify(indicator).text = "Installing benchmarking app"
      val transformCaptor: ArgumentCaptor<(InputStream) -> InputStream> = argumentCaptor()
      verify(urlFileCache).get(eq(apkUrl), eq(12.hours.inWholeMilliseconds), eq(indicator), transformCaptor.capture())
      val helloWorld = "Hello World"
      assertThat(String(transformCaptor.value.invoke(helloWorld.encodeBase64().byteInputStream()).readBytes()))
        .isEqualTo(helloWorld)

      // Installation
      verify(indicator).isIndeterminate = true
      verify(indicator).text = "Installing benchmarking app"
      verify(adb).install(SERIAL_NUMBER, downloadPath)
    }
  }

  @Test
  fun installationFromPrebuilts_success() = runBlockingTest {
    val prebuiltPath = StudioPathManager.resolvePathFromSourcesRoot(
      "prebuilts/tools/common/mirroring-benchmarker/mirroring-benchmarker.apk")
    whenever(adb.install(SERIAL_NUMBER, prebuiltPath)).thenReturn(true)
    assertThat(installer.installBenchmarkingApp(null)).isTrue()

    verify(adb).install(SERIAL_NUMBER, prebuiltPath)
  }

  @Test
  fun installationFromPrebuilts_failure() = runBlockingTest {
    val prebuiltPath = StudioPathManager.resolvePathFromSourcesRoot(
      "prebuilts/tools/common/mirroring-benchmarker/mirroring-benchmarker.apk")
    whenever(adb.install(SERIAL_NUMBER, prebuiltPath)).thenReturn(false)

    assertThat(installer.installBenchmarkingApp(null)).isFalse()

    verify(adb).install(SERIAL_NUMBER, prebuiltPath)
  }

  @Test
  fun launchBenchmarkingApp_success() = runBlockingTest {
    val command = "am start -n com.android.tools.screensharing.benchmark/.InputEventRenderingActivity -f 65536"
    whenever(adb.shellCommand(SERIAL_NUMBER, command)).thenReturn(true)

    assertThat(installer.launchBenchmarkingApp(null)).isTrue()
    verify(adb).shellCommand(SERIAL_NUMBER, command)
  }

  @Test
  fun launchBenchmarkingApp_failure() = runBlockingTest {
    val command = "am start -n com.android.tools.screensharing.benchmark/.InputEventRenderingActivity -f 65536"
    whenever(adb.shellCommand(SERIAL_NUMBER, command)).thenReturn(false)

    assertThat(installer.launchBenchmarkingApp(null)).isFalse()
    verify(adb).shellCommand(SERIAL_NUMBER, command)
  }

  @Test
  fun uninstallBenchmarkingApp() = runBlockingTest {
    installer.uninstallBenchmarkingApp()

    verify(adb).uninstall(SERIAL_NUMBER)
  }
}
