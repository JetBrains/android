/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.asdriver.tests;

import com.android.test.testutils.TestUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

/**
 * An X server potentially backed by Xvfb.
 */
public class XvfbServer implements Display {
  private static final String DEFAULT_RESOLUTION = "1280x1024x24";
  private static final int MAX_RETRIES_TO_FIND_DISPLAY = 20;
  private static final String XVFB_LAUNCHER = "tools/vendor/google/testing/display/launch_xvfb.sh";
  private static final String FFMPEG = "tools/vendor/google/testing/display/ffmpeg";

  private Process process;

  /**
   * The display we're using on Linux. This will start with a colon, e.g. ":40981".
   */
  private String display;

  private Process recorder;

  private final @NotNull String resolution;

  public XvfbServer() throws IOException {
    this(DEFAULT_RESOLUTION);
  }

  public XvfbServer(@NotNull String resolution) throws IOException {
    String display = System.getenv("DISPLAY");
    this.resolution = resolution;
    if (display == null || display.isEmpty()) {
      // If a display is provided use that, otherwise create one.
      this.display = launchUnusedDisplay();
      this.recorder = launchRecorder(this.display);
      System.out.println("Display: " + this.display);
    } else {
      this.display = display;
      System.out.println("Display inherited from parent: " + display);
    }
  }

  @Override
  public String getDisplay() {
    return display;
  }

  private Process launchRecorder(String display) throws IOException {
    Path dir = TestUtils.getTestOutputDir();
    Path mp4 = dir.resolve("recording.mp4");
    Path ffmpeg = TestUtils.resolveWorkspacePathUnchecked(FFMPEG);

    // Note that -pix_fmt is required by some players:
    // https://trac.ffmpeg.org/wiki/Encode/H.264#Encodingfordumbplayers
    ProcessBuilder pb = new ProcessBuilder(
      ffmpeg.toString(),
      "-framerate", "25",
      "-f", "x11grab",
      "-i", display,
      "-pix_fmt", "yuv420p",
      "-movflags", "faststart",
      mp4.toString());
    pb.redirectOutput(dir.resolve("ffmpeg_stdout.txt").toFile());
    pb.redirectError(dir.resolve("ffmpeg_stderr.txt").toFile());
    return pb.start();
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
      }
      catch (InterruptedException e) {
        throw new RuntimeException("Xvfb was interrupted", e);
      }
    }
    throw new RuntimeException("Cannot find an unused display");
  }

  private Process launchDisplay(String display) {
    this.display = display;
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
        TestUtils.getWorkspaceRoot().toString(),
        resolution
      ).start();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void close() {
    if (process != null) {
      process.destroyForcibly();
      process = null;
    }
    if (recorder != null) {
      recorder.destroy();
      recorder = null;
    }
  }
}
