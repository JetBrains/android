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
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.adb.AdbService
import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileSystemService.Companion.getInstance
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.util.concurrent.Futures.immediateFailedFuture
import com.google.common.util.concurrent.Futures.immediateFuture
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.UsefulTestCase.assertThrows
import kotlinx.coroutines.runBlocking
import org.jetbrains.android.sdk.AndroidSdkUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.io.FileNotFoundException
import java.util.function.Supplier

class AdbDeviceFileSystemServiceTest {

  @get:Rule
  val androidProjectRule = AndroidProjectRule.withSdk()

  private val project: Project
    get() = androidProjectRule.project

  private val adbSupplier = Supplier { AndroidSdkUtils.getAdb(project) }

  @Test
  fun startService() = runBlocking {
    // Prepare
    val service = getInstance(project)

    // Act
    service.start(adbSupplier)

    // Assert
    // Note: There is not much we can assert on, other than implicitly the fact we
    // reached this statement.
    assertNotNull(service.devices)
  }

  @Test
  fun debugBridgeListenersRemovedOnDispose() = runBlocking {
    // Prepare
    val service = getInstance(project)
    service.start(adbSupplier)
    assertEquals(1, AndroidDebugBridge.getDebugBridgeChangeListenerCount())
    assertEquals(1, AndroidDebugBridge.getDeviceChangeListenerCount())

    // Act
    Disposer.dispose(service)

    // Assert
    assertEquals(0, AndroidDebugBridge.getDebugBridgeChangeListenerCount())
    assertEquals(0, AndroidDebugBridge.getDeviceChangeListenerCount())
  }

  @Test
  fun startAlreadyStartedService() = runBlocking {
    // Prepare
    val service = getInstance(project)

    // Act
    service.start(adbSupplier)
    service.start(adbSupplier)

    // Assert
    // Note: There is not much we can assert on, other than implicitly the fact we
    // reached this statement.
    assertNotNull(service.devices)
  }

  @Test
  fun startServiceFailsIfAdbIsNull() {
    // Prepare
    val service = getInstance(project)

    // Act
    assertThrows(FileNotFoundException::class.java, "Android Debug Bridge not found.") {
      runBlocking {
        service.start { null }
      }
    }
  }

  @Test
  fun restartService() = runBlocking {
    // Prepare
    val service = getInstance(project)
    service.start(adbSupplier)

    // Act
    service.restart(adbSupplier)

    // Assert
    // Note: There is not much we can assert on, other than implicitly the fact we
    // reached this statement.
    assertNotNull(service.devices)
  }

  @Test
  fun restartNonStartedService() = runBlocking {
    // Prepare
    val service = getInstance(project)

    // Act
    service.restart(adbSupplier)

    // Assert
    // Note: There is not much we can assert on, other than implicitly the fact we
    // reached this statement.
    assertNotNull(service.devices)
  }

  @Test
  fun restartServiceCantTerminateDdmlib() = runBlocking {
    // Prepare
    val mockAdbService = androidProjectRule.mockService(AdbService::class.java)
    `when`(mockAdbService.getDebugBridge(any())).thenReturn(immediateFuture(mock()))

    Mockito.doThrow(RuntimeException()).`when`(mockAdbService).terminateDdmlib()
    val service = getInstance(project)
    service.start(adbSupplier)

    // Act
    try {
      service.restart(adbSupplier)
    } catch(e: RuntimeException) {
      // expected
    }
    Mockito.doNothing().`when`(mockAdbService).terminateDdmlib()
  }

  @Test
  fun getDebugBridgeFailure() {
    // Prepare
    val service = getInstance(project)
    val mockAdbService = androidProjectRule.mockService(AdbService::class.java)
    `when`(mockAdbService.getDebugBridge(any())).thenReturn(immediateFailedFuture(RuntimeException("test fail")))

    assertThrows(RuntimeException::class.java, "test fail") {
      runBlocking { service.start(adbSupplier) }
    }
  }

  @Test
  fun getDebugBridgeFailureNoMessage() {
    // Prepare
    val service = getInstance(project)
    val mockAdbService = androidProjectRule.mockService(AdbService::class.java)
    `when`(mockAdbService.getDebugBridge(any())).thenReturn(immediateFailedFuture(RuntimeException()))

    // Act
    assertThrows(RuntimeException::class.java) {
      runBlocking { service.start(adbSupplier) }
    }
  }
}