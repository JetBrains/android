/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.ddms.adb;

import com.android.ddmlib.AndroidDebugBridge;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Uninterruptibles;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.sdk.AndroidSdkUtils;

import java.util.concurrent.ExecutionException;

public class AdbServiceTest extends AndroidTestCase {

  // tests that basic API for getting and terminating a debug bridge works
  public void testBasics() throws ExecutionException {
    if (SystemInfo.isWindows) {
      // Do not run tests on Windows (see http://b.android.com/222904)
      return;
    }

    ListenableFuture<AndroidDebugBridge> future = AdbService.getInstance().getDebugBridge(AndroidSdkUtils.getAdb(getProject()));
    AndroidDebugBridge bridge = Uninterruptibles.getUninterruptibly(future);
    assertTrue(bridge.isConnected());
    AdbService.getInstance().terminateDdmlib();
  }
}
