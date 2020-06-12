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
package com.android.tools.idea.adb;

import com.android.SdkConstants;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.testutils.TestUtils;
import com.google.common.truth.Truth;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Uninterruptibles;
import com.intellij.testFramework.LightPlatformTestCase;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

public class AdbServiceTest extends LightPlatformTestCase {
  @Override
  protected void tearDown() throws Exception {
    try {
      AdbService.getInstance().terminateDdmlib();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  /**
   * Tests that basic API for getting and terminating a debug bridge works
   */
  public void testBasics() throws ExecutionException {
    // Prepare
    Path adb = TestUtils.getSdk().toPath().resolve("platform-tools").resolve(SdkConstants.FN_ADB);

    // Act
    ListenableFuture<AndroidDebugBridge> future = AdbService.getInstance().getDebugBridge(adb.toFile());
    AndroidDebugBridge bridge = Uninterruptibles.getUninterruptibly(future);

    // Assert
    Truth.assertThat(bridge.isConnected()).isTrue();
  }

  public void testInvalidAdbFile() {
    // Prepare
    Path invalidAdbPath = TestUtils.getSdk().toPath().resolve("platform-tools").resolve(SdkConstants.FN_ADB).resolve("not-a-file");

    // Act
    ListenableFuture<AndroidDebugBridge> future = AdbService.getInstance().getDebugBridge(invalidAdbPath.toFile());
    AndroidDebugBridge bridge = null;
    try {
      bridge = Uninterruptibles.getUninterruptibly(future);
    }
    catch (ExecutionException expected) {
    }

    // Assert
    Truth.assertThat(bridge).isNull();
  }

  /**
   * Tests that if the connection to the bridge is broken, re-initing works.
   */
  public void testReinit() throws ExecutionException {
    // Prepare
    Path adb = TestUtils.getSdk().toPath().resolve("platform-tools").resolve(SdkConstants.FN_ADB);

    // Act
    ListenableFuture<AndroidDebugBridge> future = AdbService.getInstance().getDebugBridge(adb.toFile());
    AndroidDebugBridge bridge = Uninterruptibles.getUninterruptibly(future);

    // Assert
    Truth.assertThat(bridge.isConnected()).isTrue();

    // Simulate disconnect
    AdbService.getInstance().cancelFutureForTesting();

    // Reinit
    Uninterruptibles.getUninterruptibly(AdbService.getInstance().getDebugBridge(adb.toFile()));

    // Get again
    Uninterruptibles.getUninterruptibly(AdbService.getInstance().getDebugBridge(adb.toFile()));
  }
}
