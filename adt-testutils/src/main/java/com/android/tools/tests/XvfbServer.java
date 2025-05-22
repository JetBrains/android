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
import com.android.test.testutils.TestUtils;
import com.sun.jna.platform.linux.LibC;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.nio.file.Paths;
import org.jetbrains.annotations.Nullable;

/**
 * An X server backed by Xvfb. Constructor searches for an available X display ID in the system
 * and starts Xvfb to use that. It will also set the native environment variable DISPLAY to the
 * newly found value.
 */
// LINT.IfChange
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
      int candidate = random.nextInt(65535);
      // The only mechanism with our version of Xvfb to know when it's ready
      // to accept connections is to check for the following file. Additionally,
      // this serves as a check to know if another server is using the same
      // display.
      Path socket = Paths.get("/tmp/.X11-unix", "X" + candidate);
      if (Files.exists(socket)) {
        continue;
      }
      String display = String.format(":%d", candidate);
      Process process = launchDisplay(display);
      try {
        boolean exited = false;
        while (!exited && !Files.exists(socket)) {
          exited = process.waitFor(1, TimeUnit.SECONDS);
        }
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
    Path launcher = TestUtils.resolveWorkspacePathUnchecked(XVFB_LAUNCHER);
    if (Files.notExists(launcher)) {
      throw new IllegalStateException("Xvfb runfiles does not exist. "
                                      + "Add a data dependency on the runfiles for Xvfb. "
                                      + "It will look something like "
                                      + "//tools/vendor/google/testing/display:xvfb");
    }
    try {
      return new ProcessBuilder(
        launcher.toString(),
        display,
        com.android.test.testutils.TestUtils.getWorkspaceRoot().toString(),
        DEFAULT_RESOLUTION
      )
        .redirectErrorStream(true)
        .redirectOutput(TestUtils.getTestOutputDir().resolve("xvfb.log").toFile())
        .start();
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

  private static void setEnv(String name, String value) {
    LibC.INSTANCE.setenv(name, value, 1);
  }
}
// LINT.ThenChange(/as-driver/utils/testSrc/com/android/tools/asdriver/tests/XvfbServer.java)