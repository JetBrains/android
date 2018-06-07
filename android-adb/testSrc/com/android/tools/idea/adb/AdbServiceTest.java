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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestCase;

import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

public class AdbServiceTest extends PlatformTestCase {
  // tests that basic API for getting and terminating a debug bridge works
  public void testBasics() throws ExecutionException {
    if (SystemInfo.isWindows) {
      // Do not run tests on Windows (see http://b.android.com/222904)
      return;
    }

    Path adb = TestUtils.getSdk().toPath().resolve("platform-tools").resolve(SdkConstants.FN_ADB);
    ListenableFuture<AndroidDebugBridge> future = AdbService.getInstance().getDebugBridge(adb.toFile());
    AndroidDebugBridge bridge = Uninterruptibles.getUninterruptibly(future);
    Truth.assertThat(bridge.isConnected()).isTrue();
    AdbService.getInstance().terminateDdmlib();
  }
}
