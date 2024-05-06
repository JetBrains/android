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
package com.android.tools.asdriver.inject;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.ui.UIUtil;
import java.awt.Component;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.swing.JFrame;

/**
 * Captures screenshots for platforms that are unable to capture the entire display.
 * <p>
 * This is done via Swing renderings, meaning the screenshots themselves will only capture windows
 * from Android Studio, not OS windows like notifications.
 * <p>
 * The reason this exists has to do with how we run our tests on Windows: we use Windows Server
 * images which do not have a GUI, so screen-recording programs won't work. The lack of such
 * recordings makes diagnosing test failures incredibly challenging, so this class serves as a way
 * to get some insight into what's happening.
 */
public class ScreenshotCapturer {

  /**
   * Keeps track of which number to use when creating a screenshot. The keys in this map are
   * hash codes of the windows that we're capturing, that way all screenshots for a particular
   * window go into the same folder.
   */
  private final Map<Integer, Integer> nextScreenshotNumbers = new HashMap<>();
  private final Path outputFolder;

  /**
   * A format string (like "screenshot_%04d.png") that we use when creating the screenshots. It
   * must meet two requirements:
   * <p>
   * 1. It has to contain "%d" (although the padding amount is up to the caller).
   * 2. It has to match the format that we use when creating videos out of the screenshots.
   */
  private final String screenshotNameFormat;

  public ScreenshotCapturer(Path outputFolder, String screenshotNameFormat) throws IOException {
    this.outputFolder = outputFolder;
    this.screenshotNameFormat = screenshotNameFormat;

    System.out.println("Creating a ScreenshotCapturer that saves to " + outputFolder);

    Files.createDirectories(outputFolder);
  }

  public void start() {
    Runnable screenshotRunnable = this::takeScreenshots;

    // Don't keep a pool of threads running for good, that way we don't hold up the JVM
    // when the test is naturally finished.
    ScheduledExecutorService executor = Executors.newScheduledThreadPool(0);
    executor.scheduleAtFixedRate(screenshotRunnable, 0, 1, TimeUnit.SECONDS);
  }

  private void takeScreenshots() {
    try {
      Instant startTime = Instant.now();
      ApplicationManager.getApplication().invokeAndWait(() -> {
        Duration timeSinceRequest = Duration.between(startTime, Instant.now());

        // If we were stuck waiting for too long for the EDT, don't take a screenshot since it'll
        // only be confusing to the person looking at it later if several got queued to happen at
        // once.
        if (timeSinceRequest.getSeconds() > 1) {
          System.out.printf("Not taking screenshot since the elapsed time from the request is %s%n", timeSinceRequest);
          return;
        }
        for (Frame frame : JFrame.getFrames()) {
          takeScreenshot(frame);
        }
      });
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  private Path getNextScreenshotOutputLocation(Component c) throws IOException {
    int hashCode = c.hashCode();
    Path dir = getScreenshotFolder(hashCode);
    Files.createDirectories(dir);
    Integer screenshotNumber = nextScreenshotNumbers.merge(hashCode, 1, Integer::sum);
    String screenshotName = String.format(screenshotNameFormat, screenshotNumber);

    return dir.resolve(screenshotName);
  }

  private Path getScreenshotFolder(int hashCode) {
    return outputFolder.resolve(String.valueOf(hashCode));
  }

  private void takeScreenshot(Component c) {
    Rectangle rec = c.getBounds();
    BufferedImage bufferedImage = UIUtil.createImage(c, rec.width, rec.height, BufferedImage.TYPE_INT_ARGB);
    c.paint(bufferedImage.getGraphics());

    try {
      Path destination = getNextScreenshotOutputLocation(c);
      ImageIO.write(bufferedImage, "png", destination.toFile());
    }
    catch (IOException ioe) {
      ioe.printStackTrace();
    }
  }
}
