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
package com.android.tools.idea.tests.util.ddmlib;

import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.NullOutputReceiver;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * <p>Common queries used by tests that are best handled using ddmlib</p>
 */
public class DeviceQueries {
  @NotNull private final IDevice device;

  public DeviceQueries(@NotNull IDevice device) {
    this.device = device;
  }

  /**
   * <p>Polls the device using uiautomator until a view from the application
   * {@code applicationId} is visible on the screen.</p>
   */
  public void waitUntilAppViewsAreVisible(@NotNull String applicationId, long timeoutInSeconds) {
    Wait.seconds(timeoutInSeconds)
      .expecting("application views to be visible")
      .until(() -> {
        try {
          return isAppViewOnScreen(device, applicationId);
        } catch(IOException ignored) {
          return false;
        }
      });
  }

  private static boolean isAppViewOnScreen(@NotNull IDevice device, @NotNull String applicationId) throws IOException {
    CollectingOutputReceiver receiver = new CollectingOutputReceiver();
    executeShellCommand(device, "uiautomator dump /sdcard/window_dump.xml", new NullOutputReceiver());
    executeShellCommand(device, "cat /sdcard/window_dump.xml", receiver);
    return receiver.getOutput().contains(applicationId);
  }

  private static void executeShellCommand(@NotNull IDevice device, @NotNull String shellCmd, @NotNull IShellOutputReceiver receiver) throws IOException {
    try {
      device.executeShellCommand(shellCmd, receiver, 10, TimeUnit.SECONDS);
    } catch(Exception cmdFailed) {
      throw new IOException("Could not run " + shellCmd, cmdFailed);
    }
  }
}
