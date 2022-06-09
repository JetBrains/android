/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.explorer.adbimpl

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.testing.FakeAdbRule
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.adb.AdbFileProvider
import com.android.tools.idea.adb.AdbService
import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileSystemService.Companion.getInstance
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures.immediateFailedFuture
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.UsefulTestCase.assertThrows
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileNotFoundException

class AdbDeviceFileSystemServiceTest {

  @get:Rule
  val androidProjectRule = AndroidProjectRule.withSdk()

  @get:Rule
  val adb = FakeAdbRule()

  private val project: Project
    get() = androidProjectRule.project

  @Before
  fun setUp() {
    adb.attachDevice(
      deviceId = "test_device_01", manufacturer = "Google", model = "Pixel 10", release = "8.0", sdk = "31",
      hostConnectionType = com.android.fakeadbserver.DeviceState.HostConnectionType.USB)
  }

  @Test
  fun initialDeviceList() = runBlocking {
    // Prepare
    val service = getInstance(project)

    // Act
    service.start()

    // Assert
    // We should see the connected device immediately after start returns.
    // (FakeAdbRule waits for AndroidDebugBridge to be fully initialized.)
    assertThat(service.devices).hasSize(1)
  }

  @Test
  fun debugBridgeListenersRemovedOnDispose() = runBlocking {
    // Prepare
    val service = getInstance(project)
    service.start()
    assertEquals(2, AndroidDebugBridge.getDebugBridgeChangeListenerCount())
    assertEquals(1, AndroidDebugBridge.getDeviceChangeListenerCount())

    // Act
    Disposer.dispose(service)

    // Assert
    assertEquals(1, AndroidDebugBridge.getDebugBridgeChangeListenerCount())
    assertEquals(0, AndroidDebugBridge.getDeviceChangeListenerCount())
  }

  @Test
  fun startAlreadyStartedService() = runBlocking {
    // Prepare
    val service = getInstance(project)

    // Act
    service.start()
    service.start()

    // Assert
    assertThat(service.devices).hasSize(1)
  }

  @Test
  fun startServiceFailsIfAdbIsNull() {
    // Prepare
    AdbFileProvider { null }.storeInProject(project)
    val service = getInstance(project)

    // Act
    assertThrows(FileNotFoundException::class.java, "Android Debug Bridge not found.") {
      runBlocking {
        service.start()
      }
    }
  }

  @Test
  fun getDebugBridgeFailure() {
    // Prepare
    val service = getInstance(project)
    val mockAdbService = androidProjectRule.mockService(AdbService::class.java)
    whenever(mockAdbService.getDebugBridge(any(File::class.java))).thenReturn(immediateFailedFuture(RuntimeException("test fail")))

    assertThrows(RuntimeException::class.java, "test fail") {
      runBlocking { service.start() }
    }
  }

  @Test
  fun getDebugBridgeFailureNoMessage() {
    // Prepare
    val service = getInstance(project)
    val mockAdbService = androidProjectRule.mockService(AdbService::class.java)
    whenever(mockAdbService.getDebugBridge(any(File::class.java))).thenReturn(immediateFailedFuture(RuntimeException()))

    // Act
    assertThrows(RuntimeException::class.java) {
      runBlocking { service.start() }
    }
  }
}