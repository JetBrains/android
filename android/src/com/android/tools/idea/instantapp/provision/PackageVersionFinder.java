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
package com.android.tools.idea.instantapp.provision;

import com.android.ddmlib.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Finds the version of a package installed in a device.
 */
class PackageVersionFinder {
  static long getVersion(@NotNull IDevice device, @NotNull String pkgName) throws Exception {
    try {
      String output = executeShellCommand(device, "dumpsys package " + pkgName, 500, TimeUnit.MILLISECONDS, 1);
      return parseOutput(output);
    }
    catch (Exception e) {
      throw new Exception("Couldn't get version of installed package " + pkgName, e);
    }
  }

  private static long parseOutput(@NotNull String output) {
    int index = output.indexOf("versionCode");
    if (index == -1) {
      return 0;
    }

    int begIndex = output.indexOf('=', index) + 1;
    int endIndex = output.indexOf(' ', begIndex);
    endIndex = endIndex == -1 ? output.indexOf('\n', begIndex) : endIndex;

    if (endIndex == -1) {
      return 0;
    }

    // Returns an empty string if the package is not installed
    String versionCode = output.substring(begIndex, endIndex);
    return Long.parseLong(versionCode);
  }

  @NotNull
  private static String executeShellCommand(@NotNull IDevice device, @NotNull String cmd, long timeout, @NotNull TimeUnit timeUnit, int attempts)
    throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException, InterruptedException {
    CountDownLatch latch = new CountDownLatch(attempts);
    CollectingOutputReceiver receiver = new CollectingOutputReceiver(latch);
    device.executeShellCommand(cmd, receiver);
    latch.await(timeout, timeUnit);
    return receiver.getOutput();
  }
}
