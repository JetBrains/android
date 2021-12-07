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
import com.android.tools.idea.testing.IdeComponents
import com.android.tools.idea.testing.Sdks
import com.google.common.util.concurrent.Futures.immediateFailedFuture
import com.google.common.util.concurrent.Futures.immediateFuture
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.runBlocking
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.sdk.AndroidSdkUtils
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.io.FileNotFoundException
import java.util.function.Supplier

class AdbDeviceFileSystemServiceTest : AndroidTestCase() {
  private val adbSupplier = Supplier { AndroidSdkUtils.getAdb(project) }
  private lateinit var ideComponents: IdeComponents

  public override fun setUp() {
    super.setUp()
    ideComponents = IdeComponents(project)

    // Setup Android SDK path so that ddmlib can find adb.exe
    ApplicationManager.getApplication().runWriteAction {
      ProjectRootManager.getInstance(project).projectSdk = Sdks.createLatestAndroidSdk()
    }
  }

  override fun tearDown() {
    super.tearDown()
    // This assumes that ADB is terminated on project close
    assertNull("AndroidDebugBridge should have been terminated", AndroidDebugBridge.getBridge())
  }

  fun testStartService() = runBlocking {
    // Prepare
    val service = getInstance(project)

    // Act
    service.start(adbSupplier)

    // Assert
    // Note: There is not much we can assert on, other than implicitly the fact we
    // reached this statement.
    assertNotNull(service.devices)
  }

  fun testDebugBridgeListenersRemovedOnDispose() = runBlocking {
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

  fun testStartAlreadyStartedService() = runBlocking {
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

  fun testStartServiceFailsIfAdbIsNull() {
    // Prepare
    val service = getInstance(project)

    // Act
    assertThrows(FileNotFoundException::class.java, "Android Debug Bridge not found.") {
      runBlocking {
        service.start { null }
      }
    }
  }

  fun testRestartService() = runBlocking {
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

  fun testRestartNonStartedService() = runBlocking {
    // Prepare
    val service = getInstance(project)

    // Act
    service.restart(adbSupplier)

    // Assert
    // Note: There is not much we can assert on, other than implicitly the fact we
    // reached this statement.
    assertNotNull(service.devices)
  }

  fun testRestartServiceCantTerminateDdmlib() = runBlocking {
    // Prepare
    val mockAdbService = ideComponents.mockApplicationService(AdbService::class.java)
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

  fun testGetDebugBridgeFailure() {
    // Prepare
    val service = getInstance(project)
    val mockAdbService = ideComponents.mockApplicationService(AdbService::class.java)
    `when`(mockAdbService.getDebugBridge(any())).thenReturn(immediateFailedFuture(RuntimeException("test fail")))

    assertThrows(RuntimeException::class.java, "test fail") {
      runBlocking { service.start(adbSupplier) }
    }
  }

  fun testGetDebugBridgeFailureNoMessage() {
    // Prepare
    val service = getInstance(project)
    val mockAdbService = ideComponents.mockApplicationService(AdbService::class.java)
    `when`(mockAdbService.getDebugBridge(any())).thenReturn(immediateFailedFuture(RuntimeException()))

    // Act
    assertThrows(RuntimeException::class.java) {
      runBlocking { service.start(adbSupplier) }
    }
  }
}