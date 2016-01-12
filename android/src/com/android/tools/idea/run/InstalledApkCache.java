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
package com.android.tools.idea.run;

import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.*;
import com.google.common.base.Splitter;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class InstalledApkCache implements Disposable {
  private final DeviceStateCache<InstallState> myCache;

  /** Diagnostic output set by {@link #getLastUpdateTime(com.android.ddmlib.IDevice, String)} */
  private String myDiagnosticOutput;

  public InstalledApkCache() {
    myCache = new DeviceStateCache<InstallState>(this);
  }

  @Override
  public void dispose() {
  }

  public boolean isInstalled(@NotNull IDevice device, @NotNull File apk, @NotNull String pkgName) throws IOException {
    InstallState state = myCache.get(device, pkgName);
    if (state == null) {
      return false;
    }

    String lastUpdateTime = getLastUpdateTime(device, pkgName);
    return lastUpdateTime != null && lastUpdateTime.equals(state.lastUpdateTime) && state.hash.equals(hash(apk));
  }

  public void setInstalled(@NotNull IDevice device, @NotNull File apk, @NotNull String pkgName) throws IOException {
    String lastUpdateTime = getLastUpdateTime(device, pkgName);
    if (lastUpdateTime == null) {
      // set installed should be called only after the package has been installed
      // If this error happens, look at the output of "dumpsys package <name>", and see why the parser did not identify the install state.
      String msg = String.format("Unexpected error: package manager reports that package %1$s has not been installed: %2$s", pkgName,
                                 StringUtil.notNullize(myDiagnosticOutput));

      // We used to log an error, but see https://code.google.com/p/android/issues/detail?id=79778 for a case where this doesn't work
      // on custom Android systems. So we just log a warning: the impact is that these users won't have any benefits of caching - the apk
      // will always be uploaded
      Logger.getInstance(InstalledApkCache.class).warn(msg);
      return;
    }

    myCache.put(device, pkgName, new InstallState(hash(apk), lastUpdateTime));
  }

  @NotNull
  private static HashCode hash(@NotNull File apk) throws IOException {
    return Files.hash(apk, Hashing.goodFastHash(32));
  }

  @VisibleForTesting
  void deviceDisconnected(IDevice device) {
    myCache.deviceDisconnected(device);
  }

  /**
   * Returns the lastUpdateTime field from dumpsys package's output from the given device for the given package.
   * A null return value indicates that the package was not found, and an empty string indicates that the package was installed, but the
   * last update time could not be determined.
   */
  @Nullable
  public String getLastUpdateTime(@NotNull IDevice device, @NotNull String pkgName) {
    boolean deviceHasPackage = false;
    myDiagnosticOutput = null;

    String output;
    try {
      output = executeShellCommand(device, "dumpsys package " + pkgName, 500, TimeUnit.MILLISECONDS);
    }
    catch (Exception e) {
      myDiagnosticOutput = String.format("Error executing 'dumpsys package %1$s:\n%2$s'", pkgName, e.getMessage());
      return null;
    }

    // The follow code assumes that the output of "dumpsys package <pkgname>" has at least the following line:
    //       Package [pkgName]
    // Optionally, if it also has a line of form:
    //        lastUpdateTime=2014-09-29 11:58:19
    // then that line is saved as is as the last updated time
    Iterable<String> lines = Splitter.on("\n").split(output);
    for (String line : lines) {
      line = line.trim();
      if (line.startsWith("Package [")) {
        int startIndex = line.indexOf('[');
        int endIndex = line.indexOf(']');
        if (startIndex > 0 && endIndex > startIndex) {
          deviceHasPackage = pkgName.equals(line.substring(startIndex + 1, endIndex));
        }
        break;
      }
    }

    if (!deviceHasPackage) {
      myDiagnosticOutput = String.format("Expected string 'Package [%1$s]' not found in output: %2$s", pkgName, output);
      return null;
    }

    for (String line : lines) {
      line = line.trim();
      if (line.startsWith("lastUpdateTime")) {
        return line;
      }
    }

    return "";
  }

  protected String executeShellCommand(@NotNull IDevice device, @NotNull String cmd, long timeout, @NotNull TimeUnit timeUnit)
    throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException, InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    CollectingOutputReceiver receiver = new CollectingOutputReceiver(latch);
    device.executeShellCommand(cmd, receiver);
    latch.await(timeout, timeUnit);
    return receiver.getOutput();
  }

  private static class InstallState {
    @NotNull public final HashCode hash;
    @Nullable public final String lastUpdateTime;

    public InstallState(@NotNull HashCode hash, @Nullable String lastUpdateTime) {
      this.hash = hash;
      this.lastUpdateTime = lastUpdateTime;
    }
  }
}
