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
package com.android.tools.tests;

import com.android.testutils.OsType;
import com.android.testutils.TestUtils;
import com.sun.jna.Library;
import com.sun.jna.Native;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * An X server backed by Xvfb. Constructor searches for an available X display ID in the system
 * and starts Xvfb to use that. It will also set the native environment variable DISPLAY to the
 * newly found value.
 */
class XvfbServer {
  private static final String DEFAULT_RESOLUTION = "1280x1024x24";
  private static final int MAX_RETRIES_TO_FIND_DISPLAY = 20;
  private static final String XVFB_LAUNCHER = "tools/vendor/google/testing/display/launch_xvfb.sh";

  @Nullable
  private Process process;

  XvfbServer() {
    if (OsType.getHostOs().equals(OsType.LINUX) && System.getenv("DISPLAY") == null) {
      String display = launchUnusedDisplay();
      setEnv("DISPLAY", display);
    }
  }

  public String launchUnusedDisplay() {
    int retry = MAX_RETRIES_TO_FIND_DISPLAY;
    Random random = new Random();
    while (retry-- > 0) {
      String display = String.format(":%d", random.nextInt(65535));
      Process process = launchDisplay(display);
      try {
        boolean exited = process.waitFor(1, TimeUnit.SECONDS);
        if (!exited) {
          this.process = process;
          System.out.println("Launched xvfb on \"" + display + "\"");
          return display;
        }
      } catch (InterruptedException e) {
        throw new RuntimeException("Xvfb was interrupted", e);
      }
    }
    throw new RuntimeException("Cannot find an unused display");
  }

  private Process launchDisplay(String display) {
    File workspace = TestUtils.getWorkspaceRoot();
    File launcher = new File(workspace, XVFB_LAUNCHER);
    if (!launcher.exists()) {
      throw new IllegalStateException("Xvfb runfiles did not exist; "
                                      + "add a data dependency on the runfiles for Xvfb. "
                                      + "It will look something like "
                                      + "//tools/vendor/google/testing/display:xvfb");
    }
    try {
      return new ProcessBuilder(
        launcher.getAbsolutePath(),
        display,
        workspace.getAbsolutePath(),
        DEFAULT_RESOLUTION
      ).start();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void kill() {
    if (process != null) {
      process.destroyForcibly();
    }
  }

  // Helper JNA class to set an env var.
  private static class Environment {
    private interface LibC extends Library {
      int setenv(String name, String value, int overwrite);
      int unsetenv(String name);
    }
    static LibC libc = (LibC) Native.loadLibrary("c", LibC.class);
  }

  private static void setEnv(String name, String value) {
    Environment.libc.setenv(name, value, 1);
  }
}
