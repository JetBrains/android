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
import com.android.tools.idea.adb.AdbService
import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileSystemService.Companion.getInstance
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFuture
import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileSystemService.start
import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileSystemService.devices
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFutureException
import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileSystemService.restart
import org.jetbrains.android.sdk.AndroidSdkUtils
import com.android.tools.idea.testing.IdeComponents
import kotlin.Throws
import java.lang.Runnable
import com.intellij.openapi.roots.ProjectRootManager
import com.android.tools.idea.testing.Sdks
import java.lang.InterruptedException
import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileSystemService
import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileSystem
import com.google.common.util.concurrent.Futures
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import org.jetbrains.android.AndroidTestCase
import org.mockito.Mockito
import org.mockito.ArgumentMatchers
import java.io.File
import java.lang.Exception
import java.lang.RuntimeException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException
import java.util.function.Supplier

class AdbDeviceFileSystemServiceTest : AndroidTestCase() {
  private val adbSupplier = Supplier { AndroidSdkUtils.getAdb(project) }
  private var ideComponents: IdeComponents? = null
  @Throws(Exception::class)
  public override fun setUp() {
    super.setUp()
    ideComponents = IdeComponents(project)

    // Setup Android SDK path so that ddmlib can find adb.exe
    ApplicationManager.getApplication()
      .runWriteAction { ProjectRootManager.getInstance(project).projectSdk = Sdks.createLatestAndroidSdk() }
  }

  @Throws(Exception::class)
  override fun tearDown() {
    super.tearDown()
    // This assumes that ADB is terminated on project close
    assertNull("AndroidDebugBridge should have been terminated", AndroidDebugBridge.getBridge())
  }

  @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
  fun testStartService() {
    // Prepare
    val service = getInstance(project)

    // Act
    pumpEventsAndWaitForFuture(service.start(adbSupplier))

    // Assert
    // Note: There is not much we can assert on, other than implicitly the fact we
    // reached this statement.
    assertNotNull(pumpEventsAndWaitForFuture(service.devices))
  }

  fun testDebugBridgeListenersRemovedOnDispose() {
    // Prepare
    val service = getInstance(project)
    pumpEventsAndWaitForFuture(service.start(adbSupplier))
    assertEquals(1, AndroidDebugBridge.getDebugBridgeChangeListenerCount())
    assertEquals(1, AndroidDebugBridge.getDeviceChangeListenerCount())

    // Act
    Disposer.dispose(service)

    // Assert
    assertEquals(0, AndroidDebugBridge.getDebugBridgeChangeListenerCount())
    assertEquals(0, AndroidDebugBridge.getDeviceChangeListenerCount())
  }

  @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
  fun testStartAlreadyStartedService() {
    // Prepare
    val service = getInstance(project)

    // Act
    service.start(adbSupplier)
    pumpEventsAndWaitForFuture(service.start(adbSupplier))

    // Assert
    // Note: There is not much we can assert on, other than implicitly the fact we
    // reached this statement.
    assertNotNull(pumpEventsAndWaitForFuture(service.devices))
  }

  fun testStartServiceFailsIfAdbIsNull() {
    // Prepare
    val service = getInstance(project)

    // Act
    val throwable = pumpEventsAndWaitForFutureException(service.start { null })

    // Assert
    assertEquals("java.io.FileNotFoundException: Android Debug Bridge not found.", throwable.message)
  }

  @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
  fun testRestartService() {
    // Prepare
    val service = getInstance(project)
    pumpEventsAndWaitForFuture(service.start(adbSupplier))

    // Act
    pumpEventsAndWaitForFuture(service.restart(adbSupplier))

    // Assert
    // Note: There is not much we can assert on, other than implicitly the fact we
    // reached this statement.
    assertNotNull(pumpEventsAndWaitForFuture(service.devices))
  }

  @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
  fun testRestartNonStartedService() {
    // Prepare
    val service = getInstance(project)

    // Act
    pumpEventsAndWaitForFuture(service.restart(adbSupplier))

    // Assert
    // Note: There is not much we can assert on, other than implicitly the fact we
    // reached this statement.
    assertNotNull(pumpEventsAndWaitForFuture(service.devices))
  }

  @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
  fun testRestartServiceCantTerminateDdmlib() {
    // Prepare
    val mockAdbService = ideComponents!!.mockApplicationService(
      AdbService::class.java
    )
    Mockito.`when`(
      mockAdbService.getDebugBridge(
        ArgumentMatchers.any(
          File::class.java
        )
      )
    ).thenReturn(
      Futures.immediateFuture(
        Mockito.mock(
          AndroidDebugBridge::class.java
        )
      )
    )
    Mockito.doThrow(RuntimeException()).`when`(mockAdbService).terminateDdmlib()
    val service = getInstance(project)
    pumpEventsAndWaitForFuture(service.start(adbSupplier))

    // Act
    val t = pumpEventsAndWaitForFutureException(service.restart(adbSupplier))
    Mockito.doNothing().`when`(mockAdbService).terminateDdmlib()
  }

  fun testGetDebugBridgeFailure() {
    // Prepare
    val service = getInstance(project)
    val mockAdbService = ideComponents!!.mockApplicationService(
      AdbService::class.java
    )
    Mockito.`when`(
      mockAdbService.getDebugBridge(
        ArgumentMatchers.any(
          File::class.java
        )
      )
    ).thenReturn(Futures.immediateFailedFuture(RuntimeException("test fail")))

    // Act
    val t = pumpEventsAndWaitForFutureException(service.start(adbSupplier))

    // Assert
    assertEquals("java.lang.RuntimeException: test fail", t.message)
  }

  fun testGetDebugBridgeFailureNoMessage() {
    // Prepare
    val service = getInstance(project)
    val mockAdbService = ideComponents!!.mockApplicationService(
      AdbService::class.java
    )
    Mockito.`when`(
      mockAdbService.getDebugBridge(
        ArgumentMatchers.any(
          File::class.java
        )
      )
    ).thenReturn(Futures.immediateFailedFuture(RuntimeException()))

    // Act
    pumpEventsAndWaitForFutureException(service.start(adbSupplier))
  }
}