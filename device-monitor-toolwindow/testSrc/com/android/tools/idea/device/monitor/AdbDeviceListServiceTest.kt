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
package com.android.tools.idea.device.monitor

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.testing.FakeAdbRule
import com.android.testutils.MockitoKt
import com.android.tools.idea.adb.AdbFileProvider
import com.android.tools.idea.adb.AdbService
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.device.monitor.adbimpl.AdbDeviceListService.Companion.getInstance
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.util.concurrent.Futures
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.mockito.Mockito
import java.io.File
import java.io.FileNotFoundException

class AdbDeviceListServiceTest {

  @get:Rule
  val androidProjectRule = AndroidProjectRule.withSdk()

  @get:Rule
  val adb = FakeAdbRule()

  @JvmField
  @Rule
  val exceptionRule: ExpectedException = ExpectedException.none()

  private val project: Project
    get() = androidProjectRule.project

  @Before
  fun setUp() {
    adb.attachDevice(
      deviceId = "test_device_01", manufacturer = "Google", model = "Pixel 10", release = "8.0", sdk = "31",
      hostConnectionType = com.android.fakeadbserver.DeviceState.HostConnectionType.USB)
  }

  @Test
  fun testInitialDeviceList() = runBlocking(AndroidDispatchers.uiThread) {

    // Prepare
    val service = getInstance(project)

    // Act
    service.start()
    // Assert
    // We should see the connected device immediately after start returns.
    // (FakeAdbRule waits for AndroidDebugBridge to be fully initialized.)
    Assert.assertEquals(service.devices.size, 1)
  }

  @Test
  fun testDebugBridgeListenersRemovedOnDispose() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val service = getInstance(project)
    service.start()
    Assert.assertEquals(2, AndroidDebugBridge.getDebugBridgeChangeListenerCount())
    Assert.assertEquals(1, AndroidDebugBridge.getDeviceChangeListenerCount())

    // Act
    Disposer.dispose(service)

    // Assert
    Assert.assertEquals(1, AndroidDebugBridge.getDebugBridgeChangeListenerCount())
    Assert.assertEquals(0, AndroidDebugBridge.getDeviceChangeListenerCount())
  }

  @Test
  fun testStartAlreadyStartedService() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val service = getInstance(project)

    // Act
    service.start()
    service.start()

    // Assert
    Assert.assertEquals(service.devices.size, 1)
  }

  @Test
  fun testStartServiceFailsIfAdbIsNull() {
    // Prepare
    val adbFileProvider = AdbFileProvider { null }
    project.replaceService(AdbFileProvider::class.java, adbFileProvider, androidProjectRule.testRootDisposable)
    val service = getInstance(project)

    // Act // Assert
    exceptionRule.expect(FileNotFoundException::class.java)
    runBlocking(AndroidDispatchers.uiThread) {
      service.start()
    }
  }

  @Test
  fun testGetDebugBridgeFailure() {
    // Prepare
    val service = getInstance(project)
    val mockAdbService = androidProjectRule.mockService(AdbService::class.java)
    Mockito.`when`(mockAdbService.getDebugBridge(MockitoKt.any(File::class.java))).thenReturn(
      Futures.immediateFailedFuture(RuntimeException("test fail")))

    // Act // Assert
    exceptionRule.expect(RuntimeException::class.java)
    runBlocking(AndroidDispatchers.uiThread) {
      service.start()
    }

  }

  @Test
  fun testGetDebugBridgeFailureNoMessage() {
    // Prepare
    val service = getInstance(project)
    val mockAdbService = androidProjectRule.mockService(AdbService::class.java)
    Mockito.`when`(mockAdbService.getDebugBridge(MockitoKt.any(File::class.java))).thenReturn(
      Futures.immediateFailedFuture(RuntimeException()))

    // Act // Assert
    exceptionRule.expect(RuntimeException::class.java)
    runBlocking(AndroidDispatchers.uiThread) {
      service.start()
    }
  }
}