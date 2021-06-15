/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.ddms.screenshot;

import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.RawImage;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.ExceptionUtil;
import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

public class ScreenshotTask extends Task.Modal {
  private final IDevice myDevice;

  private String myError;
  private BufferedImage myImage;
  private boolean myIsRound;

  public ScreenshotTask(@NotNull Project project, @NotNull IDevice device) {
    super(project, AndroidBundle.message("android.ddms.actions.screenshot.title"), true);
    myDevice = device;
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    indicator.setIndeterminate(true);

    indicator.setText(AndroidBundle.message("android.ddms.screenshot.task.step.obtain"));
    RawImage rawImage;
    boolean isRound;

    ScreenshotRetrieverTask retrieverTask = new ScreenshotRetrieverTask(myDevice);
    ApplicationManager.getApplication().executeOnPooledThread(retrieverTask);
    Future<ScreenshotRetrieverTask.ScreenshotResult> image = retrieverTask.getScreenshotResult();

    while (true) {
      try {
        ScreenshotRetrieverTask.ScreenshotResult result = image.get(100, TimeUnit.MILLISECONDS);
        rawImage = result.getRawImage();
        isRound = result.isRound();
        break;
      }
      catch (InterruptedException | ExecutionException e) {
        myError = AndroidBundle.message("android.ddms.screenshot.task.error1", ExceptionUtil.getMessage(e));
        return;
      }
      catch (TimeoutException e) {
        if (indicator.isCanceled()) {
          return;
        }
      }
    }

    if (rawImage.bpp != 16 && rawImage.bpp != 32) {
      myError = AndroidBundle.message("android.ddms.screenshot.task.error.invalid.bpp", rawImage.bpp);
      return;
    }

    indicator.setText(AndroidBundle.message("android.ddms.screenshot.task.step.load"));

    myImage = rawImage.asBufferedImage();
    myIsRound = isRound;
  }

  public BufferedImage getScreenshot() {
    return myImage;
  }

  public String getError() {
    return myError;
  }

  public boolean isRound() {
    return myIsRound;
  }

  private static class ScreenshotRetrieverTask implements Runnable {
    private final IDevice myDevice;
    private final SettableFuture<ScreenshotResult> myFuture;

    public ScreenshotRetrieverTask(@NotNull IDevice device) {
      myDevice = device;
      myFuture = SettableFuture.create();
    }

    @Override
    public void run() {
      try {
        RawImage image = myDevice.getScreenshot(10, TimeUnit.SECONDS);
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        myDevice.executeShellCommand("dumpsys display", receiver);
        myFuture.set(new ScreenshotResult(image, receiver.getOutput().contains("FLAG_ROUND")));
      }
      catch (Throwable t) {
        myFuture.setException(t);
      }
    }

    public Future<ScreenshotResult> getScreenshotResult() {
      return myFuture;
    }

    private static class ScreenshotResult{
      private final RawImage myRawImage;
      private final boolean myIsRound;

      private ScreenshotResult(RawImage image, boolean isRound) {
        myRawImage = image;
        myIsRound = isRound;
      }

      private boolean isRound() {
        return myIsRound;
      }

      private RawImage getRawImage() {
        return myRawImage;
      }
    }
  }
}
