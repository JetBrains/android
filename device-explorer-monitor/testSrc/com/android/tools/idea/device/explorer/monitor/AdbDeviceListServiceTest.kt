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
package com.android.tools.idea.device.explorer.monitor

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.ddmlib.testing.FakeAdbRule
import com.android.fakeadbserver.DeviceState
import com.android.testutils.MockitoKt
import com.android.tools.idea.FutureValuesTracker
import com.android.tools.idea.adb.AdbFileProvider
import com.android.tools.idea.adb.AdbService
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.device.explorer.monitor.adbimpl.AdbDeviceService
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.runBlocking
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

  private lateinit var device1: DeviceState

  @Before
  fun setUp() {
    device1 = adb.attachDevice(
      deviceId = "test_device_01", manufacturer = "Google", model = "Pixel 10", release = "8.0", sdk = "31",
      hostConnectionType = DeviceState.HostConnectionType.USB)
  }

  @Test
  fun testFindingDeviceBeforeServiceStarts() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val service = AdbDeviceService(project)

    // Act
    service.start()

    // Assert
    // We should see the connected device immediately after start returns.
    // (FakeAdbRule waits for AndroidDebugBridge to be fully initialized.)
    assertThat(service.getIDeviceFromSerialNumber(device1.deviceId)).isNotNull()
    Disposer.dispose(service)
  }

  @Test
  fun testFindingDeviceAfterServiceStarts() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val service = AdbDeviceService(project)
    val tracker = FutureValuesTracker<IDevice?>()
    val listener = object : DeviceServiceListener {
      override fun deviceProcessListUpdated(device: IDevice) {
        tracker.produce(device)
      }
    }

    // Act
    service.addListener(listener)
    service.start()
    val device2 = adb.attachDevice(
      deviceId = "test_device_02", manufacturer = "Google", model = "Pixel 10", release = "8.0", sdk = "31",
      hostConnectionType = DeviceState.HostConnectionType.USB)

    // Assert
    assertThat(service.getIDeviceFromSerialNumber(device2.deviceId)).isNotNull()
    Disposer.dispose(service)
  }

  @Test
  fun testNotFindingDevice() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val service = AdbDeviceService(project)

    // Act
    service.start()

    // Assert
    assertThat(service.getIDeviceFromSerialNumber("Fake ID")).isNull()
    Disposer.dispose(service)
  }

  @Test
  fun testNotFindingDeviceWithNullSerialNumber() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val service = AdbDeviceService(project)

    // Act
    service.start()

    // Assert
    assertThat(service.getIDeviceFromSerialNumber(null)).isNull()
    Disposer.dispose(service)
  }

  @Test
  fun testDebugBridgeListenersRemovedOnDispose() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val service = AdbDeviceService(project)
    service.start()
    assertThat(AndroidDebugBridge.getDebugBridgeChangeListenerCount()).isEqualTo(2)
    assertThat(AndroidDebugBridge.getDeviceChangeListenerCount()).isEqualTo(1)

    // Act
    Disposer.dispose(service)

    // Assert
    assertThat(AndroidDebugBridge.getDebugBridgeChangeListenerCount()).isEqualTo(1)
    assertThat(AndroidDebugBridge.getDeviceChangeListenerCount()).isEqualTo(0)
    Disposer.dispose(service)
  }

  @Test
  fun testStartAlreadyStartedService() = runBlocking(AndroidDispatchers.uiThread) {
    // Prepare
    val service = AdbDeviceService(project)

    // Act
    service.start()
    service.start()

    // Assert
    assertThat(service.getIDeviceFromSerialNumber(device1.deviceId)).isNotNull()
    Disposer.dispose(service)
  }

  @Test
  fun testStartServiceFailsIfAdbIsNull() {
    // Prepare
    val adbFileProvider = AdbFileProvider { null }
    project.replaceService(AdbFileProvider::class.java, adbFileProvider, androidProjectRule.testRootDisposable)
    val service = AdbDeviceService(project)

    // Act // Assert
    exceptionRule.expect(FileNotFoundException::class.java)
    runBlocking(AndroidDispatchers.uiThread) {
      try {
        service.start()
      } catch (e: Exception) {
        throw e
      } finally {
        Disposer.dispose(service)
      }
    }
  }

  @Test
  fun testGetDebugBridgeFailure() {
    // Prepare
    val service = AdbDeviceService(project)
    val mockAdbService = androidProjectRule.mockService(AdbService::class.java)
    Mockito.`when`(mockAdbService.getDebugBridge(MockitoKt.any(File::class.java))).thenReturn(
      Futures.immediateFailedFuture(RuntimeException("test fail")))

    // Act // Assert
    exceptionRule.expect(RuntimeException::class.java)
    runBlocking(AndroidDispatchers.uiThread) {
      try {
        service.start()
      } catch (e: Exception) {
        throw e
      } finally {
        Disposer.dispose(service)
      }
    }
  }

  @Test
  fun testGetDebugBridgeFailureNoMessage() {
    // Prepare
    val service = AdbDeviceService(project)
    val mockAdbService = androidProjectRule.mockService(AdbService::class.java)
    Mockito.`when`(mockAdbService.getDebugBridge(MockitoKt.any(File::class.java))).thenReturn(
      Futures.immediateFailedFuture(RuntimeException()))

    // Act // Assert
    exceptionRule.expect(RuntimeException::class.java)
    runBlocking(AndroidDispatchers.uiThread) {
      try {
        service.start()
      } catch (e: Exception) {
        throw e
      } finally {
        Disposer.dispose(service)
      }
    }
  }
}