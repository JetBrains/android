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
package com.android.tools.idea.streaming.benchmark

import com.android.mockito.kotlin.whenever
import com.android.tools.idea.downloads.UrlFileCache
import com.android.tools.idea.testing.disposable
import com.android.tools.idea.testing.mockStatic
import com.android.tools.idea.util.StudioPathManager
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.replaceService
import io.ktor.util.encodeBase64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.InputStream
import java.nio.file.Paths

private const val SERIAL_NUMBER = "abc123"
private const val DISABLE_IMMERSIVE_CONFIRMATION_COMMAND = "settings put secure immersive_mode_confirmations confirmed"
private const val START_COMMAND = "am start -n com.android.tools.screensharing.benchmark/.InputEventRenderingActivity -f 65536"

/** Tests the [StreamingBenchmarkerAppInstaller] class. */
@RunWith(JUnit4::class)
class StreamingBenchmarkerAppInstallerTest {
  @get:Rule
  val projectRule = ProjectRule()
  private val adb: StreamingBenchmarkerAppInstaller.AdbWrapper = mock()
  private val urlFileCache: UrlFileCache = mock()
  private val testRootDisposable
    get() = projectRule.disposable

  private lateinit var installer: StreamingBenchmarkerAppInstaller

  @Before
  fun setUp() {
    installer = StreamingBenchmarkerAppInstaller(projectRule.project, SERIAL_NUMBER, adb)
    projectRule.project.replaceService(UrlFileCache::class.java, urlFileCache, testRootDisposable)
  }

  @Test
  fun installationFromDownload() = runTest {
    val downloadPath = Paths.get("/help/i/am/trapped/in/a/unit/test/factory.apk")
    val relativePath = "common/streaming-benchmarker/streaming-benchmarker.apk"
    val basePrebuiltsUrl = "https://android.googlesource.com/platform/prebuilts/tools/+/refs/heads/mirror-goog-studio-main/"
    val apkUrl = UrlFileCache.UrlWithHeaders("$basePrebuiltsUrl$relativePath?format=TEXT")  // Base-64 encoded

    val studioPathManager = mockStatic<StudioPathManager>(testRootDisposable)
    studioPathManager.whenever<Any?> { StudioPathManager.isRunningFromSources() }.thenReturn(false)
    val indicator: ProgressIndicator = mock()
    whenever(urlFileCache.get(eq(apkUrl), any(), eq(indicator), any())).thenReturn(CompletableDeferred(downloadPath))
    whenever(adb.install(SERIAL_NUMBER, downloadPath)).thenReturn(true)

    assertThat(installer.installBenchmarkingApp(indicator)).isTrue()

    inOrder(indicator, urlFileCache, adb) {
      // Download
      verify(indicator).isIndeterminate = true
      verify(indicator).text = "Installing benchmarking app"
      val transformCaptor = argumentCaptor<(InputStream) -> InputStream>()
      @Suppress("DeferredResultUnused")
      verify(urlFileCache).get(eq(apkUrl), any(), eq(indicator), transformCaptor.capture())
      val helloWorld = "Hello World"
      assertThat(String(transformCaptor.firstValue.invoke(helloWorld.encodeBase64().byteInputStream()).readBytes()))
        .isEqualTo(helloWorld)

      // Installation
      verify(indicator).isIndeterminate = true
      verify(indicator).text = "Installing benchmarking app"
      verify(adb).install(SERIAL_NUMBER, downloadPath)
    }
  }

  @Test
  fun installationFromPrebuilts_success() = runTest {
    val prebuiltPath = StudioPathManager.resolvePathFromSourcesRoot(
      "prebuilts/tools/common/streaming-benchmarker/streaming-benchmarker.apk")
    whenever(adb.install(SERIAL_NUMBER, prebuiltPath)).thenReturn(true)
    assertThat(installer.installBenchmarkingApp(null)).isTrue()

    verify(adb).install(SERIAL_NUMBER, prebuiltPath)
  }

  @Test
  fun installationFromPrebuilts_failure() = runTest {
    val prebuiltPath = StudioPathManager.resolvePathFromSourcesRoot(
      "prebuilts/tools/common/streaming-benchmarker/streaming-benchmarker.apk")
    whenever(adb.install(SERIAL_NUMBER, prebuiltPath)).thenReturn(false)

    assertThat(installer.installBenchmarkingApp(null)).isFalse()

    verify(adb).install(SERIAL_NUMBER, prebuiltPath)
  }

  @Test
  fun launchBenchmarkingApp_disableBannerFailure() = runTest {
    whenever(adb.shellCommand(SERIAL_NUMBER, DISABLE_IMMERSIVE_CONFIRMATION_COMMAND)).thenReturn(false)
    whenever(adb.shellCommand(SERIAL_NUMBER, START_COMMAND)).thenReturn(true)

    assertThat(installer.launchBenchmarkingApp(null)).isFalse()
    verify(adb).shellCommand(SERIAL_NUMBER, DISABLE_IMMERSIVE_CONFIRMATION_COMMAND)
    verify(adb, never()).shellCommand(SERIAL_NUMBER, START_COMMAND)
  }

  @Test
  fun launchBenchmarkingApp_launchAppFailure() = runTest {
    whenever(adb.shellCommand(SERIAL_NUMBER, DISABLE_IMMERSIVE_CONFIRMATION_COMMAND)).thenReturn(true)
    whenever(adb.shellCommand(SERIAL_NUMBER, START_COMMAND)).thenReturn(false)

    assertThat(installer.launchBenchmarkingApp(null)).isFalse()
    verify(adb).shellCommand(SERIAL_NUMBER, DISABLE_IMMERSIVE_CONFIRMATION_COMMAND)
    verify(adb).shellCommand(SERIAL_NUMBER, START_COMMAND)
  }

  @Test
  fun launchBenchmarkingApp_success() = runTest {
    whenever(adb.shellCommand(SERIAL_NUMBER, DISABLE_IMMERSIVE_CONFIRMATION_COMMAND)).thenReturn(true)
    whenever(adb.shellCommand(SERIAL_NUMBER, START_COMMAND)).thenReturn(true)

    assertThat(installer.launchBenchmarkingApp(null)).isTrue()
    verify(adb).shellCommand(SERIAL_NUMBER, DISABLE_IMMERSIVE_CONFIRMATION_COMMAND)
    verify(adb).shellCommand(SERIAL_NUMBER, START_COMMAND)
  }

  @Test
  fun uninstallBenchmarkingApp() = runTest {
    installer.uninstallBenchmarkingApp()

    verify(adb).uninstall(SERIAL_NUMBER)
  }
}
