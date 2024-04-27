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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Uninterruptibles.getUninterruptibly;

import com.android.SdkConstants;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.DdmPreferences;
import com.android.test.testutils.TestUtils;
import com.android.tools.idea.flags.StudioFlags;
import com.google.common.util.concurrent.ListenableFuture;
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
    Path adb = TestUtils.getSdk().resolve("platform-tools").resolve(SdkConstants.FN_ADB);

    // Act
    ListenableFuture<AndroidDebugBridge> future = AdbService.getInstance().getDebugBridge(adb.toFile());
    AndroidDebugBridge bridge = getUninterruptibly(future);

    // Assert
    assertThat(bridge.isConnected()).isTrue();
  }

  public void testInvalidAdbFile() {
    // Prepare
    Path invalidAdbPath = TestUtils.getSdk().resolve("platform-tools").resolve(SdkConstants.FN_ADB).resolve("not-a-file");

    // Act
    ListenableFuture<AndroidDebugBridge> future = AdbService.getInstance().getDebugBridge(invalidAdbPath.toFile());
    AndroidDebugBridge bridge = null;
    try {
      bridge = getUninterruptibly(future);
    }
    catch (ExecutionException expected) {
    }

    // Assert
    assertThat(bridge).isNull();
  }

  /**
   * Tests that if the connection to the bridge is broken, re-initing works.
   */
  public void testReinit() throws Exception {
    // Prepare
    Path adb = TestUtils.getSdk().resolve("platform-tools").resolve(SdkConstants.FN_ADB);

    // Act
    ListenableFuture<AndroidDebugBridge> future = AdbService.getInstance().getDebugBridge(adb.toFile());
    AndroidDebugBridge bridge = getUninterruptibly(future);
    assertThat(bridge.isConnected()).isTrue();

    // Terminate
    AdbService.getInstance().terminateDdmlib();

    // Reinit
    AndroidDebugBridge newBridge = getUninterruptibly(AdbService.getInstance().getDebugBridge(adb.toFile()));
    assertThat(newBridge.isConnected()).isTrue();
    assertThat(newBridge).isNotSameAs(bridge);

    // Get again
    assertThat(getUninterruptibly(AdbService.getInstance().getDebugBridge(adb.toFile()))).isSameAs(newBridge);
    assertThat(newBridge.isConnected()).isTrue();
  }

  public void testOptionsChanged() throws Exception {
    Path adb = TestUtils.getSdk().resolve("platform-tools").resolve(SdkConstants.FN_ADB);
    boolean testOptionSetting = StudioFlags.ENABLE_JDWP_PROXY_SERVICE.get();

    ListenableFuture<AndroidDebugBridge> future = AdbService.getInstance().getDebugBridge(adb.toFile());
    AndroidDebugBridge bridge0 = getUninterruptibly(future);
    assertThat(bridge0.isConnected()).isTrue();
    assertThat(DdmPreferences.isJdwpProxyEnabled()).isEqualTo(testOptionSetting);

    // Change options and notify AdbService.
    testOptionSetting = !testOptionSetting;
    StudioFlags.ENABLE_JDWP_PROXY_SERVICE.override(testOptionSetting);
    AdbService.getInstance().optionsChanged();

    // Ensure new bridge is recreated with new settings.
    future = AdbService.getInstance().getDebugBridge(adb.toFile());
    AndroidDebugBridge bridge1 = getUninterruptibly(future);
    assertThat(bridge1.isConnected()).isTrue();
    assertThat(DdmPreferences.isJdwpProxyEnabled()).isEqualTo(testOptionSetting);
    assertThat(bridge1).isNotSameAs(bridge0);
  }
}
