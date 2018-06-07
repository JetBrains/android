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

import com.android.tools.idea.adb.AdbService;
import com.android.tools.idea.concurrent.EdtExecutor;
import com.android.tools.idea.util.FutureUtils;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.ProjectRootManager;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.ide.PooledThreadExecutor;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class AdbDeviceFileSystemServiceTest extends AndroidTestCase {
  private static final long TIMEOUT_MILLISECONDS = 30_000;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    // Setup Android SDK path so that ddmlib can find adb.exe
    //noinspection CodeBlock2Expr
    ApplicationManager.getApplication().runWriteAction(() -> {
      ProjectRootManager.getInstance(getProject()).setProjectSdk(createLatestAndroidSdk());
    });
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      // We need this call so that we don't leak a thread (the ADB Monitor thread)
      AdbService.getInstance().terminateDdmlib();
    }
    finally {
      super.tearDown();
    }
  }

  public void testStartService() throws InterruptedException, ExecutionException, TimeoutException {
    // Prepare
    AdbDeviceFileSystemService service = new AdbDeviceFileSystemService(aVoid -> AndroidSdkUtils.getAdb(getProject()),
                                                                        EdtExecutor.INSTANCE,
                                                                        PooledThreadExecutor.INSTANCE);
    disposeOnTearDown(service);

    // Act
    pumpEventsAndWaitForFuture(service.start());

    // Assert
    // Note: There is not much we can assert on, other than implicitly the fact we
    // reached this statement.
    assertNotNull(pumpEventsAndWaitForFuture(service.getDevices()));
  }

  public void testRestartService() throws InterruptedException, ExecutionException, TimeoutException {
    // Prepare
    AdbDeviceFileSystemService service = new AdbDeviceFileSystemService(aVoid -> AndroidSdkUtils.getAdb(getProject()),
                                                                        EdtExecutor.INSTANCE,
                                                                        PooledThreadExecutor.INSTANCE);
    disposeOnTearDown(service);
    pumpEventsAndWaitForFuture(service.start());

    // Act
    pumpEventsAndWaitForFuture(service.restart());

    // Assert
    // Note: There is not much we can assert on, other than implicitly the fact we
    // reached this statement.
    assertNotNull(pumpEventsAndWaitForFuture(service.getDevices()));
  }

  private static <V> V pumpEventsAndWaitForFuture(ListenableFuture<V> future)
    throws InterruptedException, ExecutionException, TimeoutException {
    return FutureUtils.pumpEventsAndWaitForFuture(future, TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);
  }
}
