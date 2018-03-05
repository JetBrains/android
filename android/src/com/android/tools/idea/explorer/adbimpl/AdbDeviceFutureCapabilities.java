/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

final class AdbDeviceFutureCapabilities {
  private final IDevice myDevice;
  private final ExecutorService myExecutorService;

  private Future<Boolean> myLogcatThatSupportsEpochFormatModifier;

  AdbDeviceFutureCapabilities(@NotNull IDevice device) {
    this(device, Executors.newCachedThreadPool());
  }

  @VisibleForTesting
  AdbDeviceFutureCapabilities(@NotNull IDevice device, @NotNull ExecutorService executorService) {
    myDevice = device;
    myExecutorService = executorService;
  }

  @NotNull
  Future<Boolean> hasLogcatThatSupportsEpochFormatModifier() {
    if (myLogcatThatSupportsEpochFormatModifier == null) {
      myLogcatThatSupportsEpochFormatModifier = myExecutorService.submit(this::hasLogcatThatSupportsEpochFormatModifierImpl);
    }

    return myLogcatThatSupportsEpochFormatModifier;
  }

  private boolean hasLogcatThatSupportsEpochFormatModifierImpl()
    throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {

    String command = new AdbShellCommandBuilder()
      .withText("logcat --help")
      .build();

    AdbShellCommandResult result = AdbShellCommandsUtil.executeCommand(myDevice, command);

    if (result.isError()) {
      Logger.getInstance(AdbDeviceFutureCapabilities.class).warn("Device \"" + DeviceUtil.toDebugString(myDevice) + "\" does not seem to"
                                                                 + " support the logcat --help command");

      return false;
    }

    return result.getOutput().stream().anyMatch(line -> line.contains("epoch"));
  }
}
