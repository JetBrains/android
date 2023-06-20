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

import com.android.testutils.TestUtils;
import com.intellij.util.system.CpuArch;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MacDisplay implements Display {
  private static final String FFMPEG = "tools/vendor/google/testing/display/ffmpeg_mac64";

  private Process recorder;

  /**
   * When true, the recording process will be forcibly destroyed. This is needed for scenarios
   * where ffmpeg doesn't have Screen Recording permissions on macOS versions ≥10.15; the process
   * won't exit even when the regular (i.e. non-forcible) destroy is called.
   */
  private boolean forciblyDestroy = true;

  public MacDisplay() throws IOException {
    // When running through IDEA, it typically means one of two things:
    // 1. You can watch the test execution yourself, so the video would be redundant
    // 2. You want to still use your computer, in which case you don't want ffmpeg using resources
    if (!TestUtils.runningFromBazel()) {
      System.out.println("MacDisplay created, but there won't be a screen recording since the test was invoked from outside of Bazel");
      return;
    }

    // We don't have an ffmpeg binary that works on ARM for macOS.
    if (CpuArch.isArm64()) {
      System.out.println("MacDisplay created, but there won't be a screen recording due to not having ffmpeg on ARM.");
      return;
    }
    String videoDeviceIndex = determineVideoDeviceIndex();
    if (videoDeviceIndex == null) {
      System.err.println("Could not find a video-device index from ffmpeg's output. Proceeding without a screen recording.");
      return;
    }

    launchRecorder(videoDeviceIndex);
  }

  @Override
  public void debugTakeScreenshot(String fileName) throws IOException {
    System.out.println("MacDisplay cannot take screenshots yet");
  }

  @Override
  public String getDisplay() {
    return null;
  }

  /**
   * Runs ffmpeg to determine the video-device index to record from.
   *
   * This process does not require any specific OS-level permissions.
   */
  private String determineVideoDeviceIndex() throws IOException {
    Path tempDir = Files.createTempDirectory("ffmpeg_temp");
    Path ffmpeg = TestUtils.resolveWorkspacePathUnchecked(FFMPEG);
    ProcessBuilder pb = new ProcessBuilder(ffmpeg.toString(), "-f", "avfoundation", "-list_devices", "true", "-i", "\"\"");
    File stderr = tempDir.resolve("ffmpeg_stderr.txt").toFile();
    pb.redirectError(stderr);
    Process process = pb.start();
    try {
      process.waitFor(20, TimeUnit.SECONDS);
    }
    catch (InterruptedException e) {
      throw new IOException("Could not determine video-device index");
    }

    // The output will end up looking something like this:
    // [...preamble omitted for brevity...]
    // [AVFoundation indev @ 0x7f9b49005280] AVFoundation video devices:
    // [AVFoundation indev @ 0x7f9b49005280] [0] C922 Pro Stream Webcam
    // [AVFoundation indev @ 0x7f9b49005280] [1] FaceTime HD Camera (Built-in)
    // [AVFoundation indev @ 0x7f9b49005280] [2] Capture screen 0
    // [AVFoundation indev @ 0x7f9b49005280] [3] Capture screen 1
    // [AVFoundation indev @ 0x7f9b49005280] AVFoundation audio devices:
    // [...audio device information...]
    String output = Files.readString(stderr.toPath());
    String regex = ".*\\[(\\d+)\\].*screen.*";
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(output);
    return matcher.find() ? matcher.group(1) : null;
  }

  private void launchRecorder(String videoDeviceIndex) throws IOException {
    Path dir = TestUtils.getTestOutputDir();
    Path mkv = dir.resolve("recording.mkv");
    Path ffmpeg = TestUtils.resolveWorkspacePathUnchecked(FFMPEG);

    // Note that -pix_fmt is required by some players:
    // https://trac.ffmpeg.org/wiki/Encode/H.264#Encodingfordumbplayers
    //
    // It's possible (and even likely) that the format below won't be supported; ffmpeg will output
    // "Selected pixel format (yuv420p) is not supported by the input device." in that case.
    ProcessBuilder pb =
      new ProcessBuilder(ffmpeg.toString(), "-framerate", "25", "-f", "avfoundation", "-video_device_index", videoDeviceIndex, "-i",
                         "default:none", "-pix_fmt", "yuv420p", mkv.toString());
    pb.redirectOutput(dir.resolve("ffmpeg_stdout.txt").toFile());
    pb.redirectError(dir.resolve("ffmpeg_stderr.txt").toFile());

    recorder = pb.start();

    // There's no reliable way to test for the Screen Recording permission ahead of time, so we
    // wait a small amount of time before checking to see if our video file exists. If it does,
    // then we can conclude that the video is working.
    new Thread(() -> {
      try {
        Thread.sleep(2000);
        if (Files.exists(mkv)) {
          System.out.println("Screen-recording via ffmpeg appears to be working!");
          forciblyDestroy = false;
        } else {
          System.err.println("Screen-recording via ffmpeg does not appear to be working. The most likely cause is a missing Screen " +
                             "Recording permission. Enable this by adding an entry in Preferences → Security & Privacy → Privacy → Screen" +
                             " Recording. The entry to add depends on the test runner (e.g. IntelliJ IDEA, iTerm 2, Terminal, etc.).");
        }
      }
      catch (InterruptedException e) {
        e.printStackTrace();
      }
    }).start();
  }

  @Override
  public void close() {
    if (recorder != null) {
      if (forciblyDestroy) {
        recorder.destroyForcibly();
      } else {
        recorder.destroy();
      }
      recorder = null;
    }
  }
}
