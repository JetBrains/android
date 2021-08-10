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
package com.android.tools.idea.explorer.adbimpl;

import static com.android.tools.idea.concurrency.AsyncTestUtils.pumpEventsAndWaitForFuture;
import static com.android.tools.idea.concurrency.AsyncTestUtils.pumpEventsAndWaitForFutureException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.tools.idea.adb.AdbService;
import com.android.tools.idea.testing.IdeComponents;
import com.android.tools.idea.testing.Sdks;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Disposer;
import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.sdk.AndroidSdkUtils;

public class AdbDeviceFileSystemServiceTest extends AndroidTestCase {
  private Supplier<File> adbSupplier = () -> AndroidSdkUtils.getAdb(getProject());
  private IdeComponents ideComponents;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    ideComponents = new IdeComponents(getProject());

    // Setup Android SDK path so that ddmlib can find adb.exe
    //noinspection CodeBlock2Expr
    ApplicationManager.getApplication().runWriteAction(() -> {
      ProjectRootManager.getInstance(getProject()).setProjectSdk(Sdks.createLatestAndroidSdk());
    });
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    // This assumes that ADB is terminated on project close
    assertNull("AndroidDebugBridge should have been terminated", AndroidDebugBridge.getBridge());
  }

  public void testStartService() throws InterruptedException, ExecutionException, TimeoutException {
    // Prepare
    AdbDeviceFileSystemService service = AdbDeviceFileSystemService.getInstance(getProject());

    // Act
    pumpEventsAndWaitForFuture(service.start(adbSupplier));

    // Assert
    // Note: There is not much we can assert on, other than implicitly the fact we
    // reached this statement.
    assertNotNull(pumpEventsAndWaitForFuture(service.getDevices()));
  }

  public void testDebugBridgeListenersRemovedOnDispose() {
    // Prepare
    AdbDeviceFileSystemService service = AdbDeviceFileSystemService.getInstance(getProject());
    pumpEventsAndWaitForFuture(service.start(adbSupplier));
    assertEquals(1, AndroidDebugBridge.getDebugBridgeChangeListenerCount());
    assertEquals(1, AndroidDebugBridge.getDeviceChangeListenerCount());

    // Act
    Disposer.dispose(service);

    // Assert
    assertEquals(0, AndroidDebugBridge.getDebugBridgeChangeListenerCount());
    assertEquals(0, AndroidDebugBridge.getDeviceChangeListenerCount());
  }

  public void testStartAlreadyStartedService() throws InterruptedException, ExecutionException, TimeoutException {
    // Prepare
    AdbDeviceFileSystemService service = AdbDeviceFileSystemService.getInstance(getProject());

    // Act
    service.start(adbSupplier);
    pumpEventsAndWaitForFuture(service.start(adbSupplier));

    // Assert
    // Note: There is not much we can assert on, other than implicitly the fact we
    // reached this statement.
    assertNotNull(pumpEventsAndWaitForFuture(service.getDevices()));
  }

  public void testStartServiceFailsIfAdbIsNull() {
    // Prepare
    AdbDeviceFileSystemService service = AdbDeviceFileSystemService.getInstance(getProject());

    // Act
    Throwable throwable = pumpEventsAndWaitForFutureException(service.start(() -> null));

    // Assert
    assertEquals("java.io.FileNotFoundException: Android Debug Bridge not found.", throwable.getMessage());
  }

  public void testRestartService() throws InterruptedException, ExecutionException, TimeoutException {
    // Prepare
    AdbDeviceFileSystemService service = AdbDeviceFileSystemService.getInstance(getProject());
    pumpEventsAndWaitForFuture(service.start(adbSupplier));

    // Act
    pumpEventsAndWaitForFuture(service.restart(adbSupplier));

    // Assert
    // Note: There is not much we can assert on, other than implicitly the fact we
    // reached this statement.
    assertNotNull(pumpEventsAndWaitForFuture(service.getDevices()));
  }

  public void testRestartNonStartedService() throws InterruptedException, ExecutionException, TimeoutException {
    // Prepare
    AdbDeviceFileSystemService service = AdbDeviceFileSystemService.getInstance(getProject());

    // Act
    pumpEventsAndWaitForFuture(service.restart(adbSupplier));

    // Assert
    // Note: There is not much we can assert on, other than implicitly the fact we
    // reached this statement.
    assertNotNull(pumpEventsAndWaitForFuture(service.getDevices()));
  }

  public void testRestartServiceCantTerminateDdmlib() throws InterruptedException, ExecutionException, TimeoutException {
    // Prepare
    AdbService mockAdbService = ideComponents.mockApplicationService(AdbService.class);
    when(mockAdbService.getDebugBridge(any(File.class))).thenReturn(Futures.immediateFuture(mock(AndroidDebugBridge.class)));
    doThrow(new RuntimeException()).when(mockAdbService).terminateDdmlib();

    AdbDeviceFileSystemService service = AdbDeviceFileSystemService.getInstance(getProject());
    pumpEventsAndWaitForFuture(service.start(adbSupplier));

    // Act
    //noinspection ThrowableNotThrown
    Throwable t = pumpEventsAndWaitForFutureException(service.restart(adbSupplier));

    doNothing().when(mockAdbService).terminateDdmlib();
  }

  public void testGetDebugBridgeFailure() {
    // Prepare
    AdbDeviceFileSystemService service = AdbDeviceFileSystemService.getInstance(getProject());

    AdbService mockAdbService = ideComponents.mockApplicationService(AdbService.class);
    when(mockAdbService.getDebugBridge(any(File.class))).thenReturn(Futures.immediateFailedFuture(new RuntimeException("test fail")));

    // Act
    Throwable t = pumpEventsAndWaitForFutureException(service.start(adbSupplier));

    // Assert
    assertEquals("java.lang.RuntimeException: test fail", t.getMessage());
  }

  public void testGetDebugBridgeFailureNoMessage() {
    // Prepare
    AdbDeviceFileSystemService service = AdbDeviceFileSystemService.getInstance(getProject());

    AdbService mockAdbService = ideComponents.mockApplicationService(AdbService.class);
    when(mockAdbService.getDebugBridge(any(File.class))).thenReturn(Futures.immediateFailedFuture(new RuntimeException()));

    // Act
    //noinspection ThrowableNotThrown
    pumpEventsAndWaitForFutureException(service.start(adbSupplier));
  }
}
