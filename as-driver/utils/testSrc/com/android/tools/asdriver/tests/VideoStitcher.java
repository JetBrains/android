/*
 * Copyright (C) 2023 The Android Open Source Project
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
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class VideoStitcher {
  private static final String FFMPEG = "tools/vendor/google/testing/display/ffmpeg_win64.exe";

  private static final String SCREENSHOT_FOLDER_NAME = "screenshots";

  public static final String SCREENSHOT_NAME_FORMAT = "screenshot_%04d.png";

  private boolean createdVideos = false;

  public static Path getScreenshotFolder() throws IOException {
    return TestUtils.getTestOutputDir().resolve(SCREENSHOT_FOLDER_NAME);
  }

  public VideoStitcher() {
  }

  public void createVideos() {
    if (createdVideos) {
      System.out.println("Got another call to createVideos, but this method was already invoked");
      return;
    }

    createdVideos = true;

    try {
      List<Path> foldersWithScreenshots = getFoldersWithScreenshots();
      System.out.printf("Creating videos for %d folder(s)%n", foldersWithScreenshots.size());
      for (Path dir : foldersWithScreenshots) {
        createVideoFromFolder(dir);
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void createVideoFromFolder(Path dir) throws IOException, InterruptedException {
    Instant startTime = Instant.now();
    Path ffmpeg = TestUtils.resolveWorkspacePathUnchecked(FFMPEG);
    Path videoFile = dir.resolve("video.mp4");

    ProcessBuilder pb =
      new ProcessBuilder(
        ffmpeg.toString(),
        // 1 FPS matches the capture frequency, which makes it more obvious to the viewer where the
        // video is in relation to real time.
        "-framerate", "1",

        // Use all images matching the name format to form the video.
        "-i", dir.resolve(SCREENSHOT_NAME_FORMAT).toString(),

        // The video filter
        //
        // The scale was chosen to fit most modern displays and doesn't need to relate to the size
        // of the screenshots that were captured.
        //
        // The aspect ratio should remain the same for all screenshots.
        "-vf", "scale=1920:1080:force_original_aspect_ratio=decrease:eval=frame,pad=1920:1080:-1:-1:eval=frame,format=yuv420p",

        // Puts the moov atom at the beginning of the file, which essentially means that playback
        // over the web should allow for scrubbing the video. This isn't strictly necessary since
        // people may download the video file to their machine before viewing it (Fusion may not
        // even serve the video for streaming playback).
        "-movflags", "+faststart",

        // This is the encoding rate, which may not be needed, but it doesn't hurt to add it.
        // See https://stackoverflow.com/a/51224132/18718222.
        "-r", "1",

        // See https://trac.ffmpeg.org/wiki/Encode/H.264#Encodingfordumbplayers
        "-pix_fmt", "yuv420p",
        videoFile.toString());
    pb.redirectOutput(dir.resolve("ffmpeg_stdout.txt").toFile());
    pb.redirectError(dir.resolve("ffmpeg_stderr.txt").toFile());

    System.out.println("Starting the video stitcher with " + pb.command());
    Process process = pb.start();

    process.waitFor(60, TimeUnit.SECONDS);

    Instant endTime = Instant.now();
    Duration elapsedTime = Duration.between(startTime, endTime);
    System.out.printf("Created %s in %s%n", videoFile, elapsedTime);
  }

  /**
   * Returns the set of folders that actually contain screenshots.
   * <p>
   * The agent is the one producing these, and it may have already been terminated, so that's why
   * we don't ask it for the set of folders it created and instead figure it out for ourselves.
   */
  private List<Path> getFoldersWithScreenshots() throws IOException {
    Path screenshotFolder = getScreenshotFolder();
    List<Path> foldersWithScreenshots = new ArrayList<>();

    File[] files = screenshotFolder.toFile().listFiles();
    if (files == null) {
      return foldersWithScreenshots;
    }

    for (File file : files) {
      if (file.isDirectory()) {
        File[] screenshotFiles = file.listFiles();
        if (screenshotFiles != null && screenshotFiles.length > 0) {
          foldersWithScreenshots.add(file.toPath());
        }
      }
    }

    return foldersWithScreenshots;
  }
}
