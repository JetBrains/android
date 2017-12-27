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

import com.android.ddmlib.IDevice;
import com.android.ddmlib.RawImage;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.*;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ScreenshotTask extends Task.Modal {
  private final IDevice myDevice;

  private String myError;
  private BufferedImage myImage;

  public ScreenshotTask(@NotNull Project project, @NotNull IDevice device) {
    super(project, AndroidBundle.message("android.ddms.actions.screenshot"), true);
    myDevice = device;
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    indicator.setIndeterminate(true);

    indicator.setText(AndroidBundle.message("android.ddms.screenshot.task.step.obtain"));
    RawImage rawImage;

    ScreenshotRetrieverTask retrieverTask = new ScreenshotRetrieverTask(myDevice);
    ApplicationManager.getApplication().executeOnPooledThread(retrieverTask);
    Future<RawImage> image = retrieverTask.getRawImage();

    while (true) {
      try {
        rawImage = image.get(100, TimeUnit.MILLISECONDS);
        break;
      }
      catch (InterruptedException e) {
        myError = AndroidBundle.message("android.ddms.screenshot.task.error1", ExceptionUtil.getMessage(e));
        return;
      }
      catch (ExecutionException e) {
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

    myImage = createImage(rawImage);
    for (int y = 0; y < rawImage.height; y++) {
      for (int x = 0; x < rawImage.width; x++) {
        int argb = rawImage.getARGB((x + y * rawImage.width) * (rawImage.bpp / 8));
        myImage.setRGB(x, y, argb);
      }
    }
  }

  @NotNull
  private BufferedImage createImage(RawImage rawImage) {
    String profileName = getProfileName(rawImage);
    if (profileName == null) {
      //noinspection UndesirableClassUsage
      return new BufferedImage(rawImage.width, rawImage.height, BufferedImage.TYPE_INT_ARGB);
    }

    ICC_Profile profile = ICC_Profile.getInstance(ColorSpace.CS_sRGB);
    try {
      profile = ICC_Profile.getInstance(getClass().getClassLoader().getResourceAsStream("colorProfiles/" + profileName));
    }
    catch (IOException e) {
      // Ignore
    }
    ICC_ColorSpace colorSpace = new ICC_ColorSpace(profile);

    ColorModel colorModel = new DirectColorModel(colorSpace, 32, 0x00ff0000, 0x0000ff00, 0x000000ff, 0xff000000, false, DataBuffer.TYPE_INT);
    WritableRaster raster = colorModel.createCompatibleWritableRaster(rawImage.width, rawImage.height);

    //noinspection UndesirableClassUsage
    return new BufferedImage(colorModel, raster, colorModel.isAlphaPremultiplied(), null);
  }

  @Nullable
  private static String getProfileName(RawImage image) {
    switch (image.colorSpace) {
      case RawImage.COLOR_SPACE_UNKNOWN:
        return null;
      case RawImage.COLOR_SPACE_SRGB:
        return "sRGB.icc";
      case RawImage.COLOR_SPACE_DISPLAY_P3:
        return "DisplayP3.icc";
    }
    return null;
  }

  public BufferedImage getScreenshot() {
    return myImage;
  }

  public String getError() {
    return myError;
  }

  private static class ScreenshotRetrieverTask implements Runnable {
    private final IDevice myDevice;
    private final SettableFuture<RawImage> myFuture;

    public ScreenshotRetrieverTask(@NotNull IDevice device) {
      myDevice = device;
      myFuture = SettableFuture.create();
    }

    @Override
    public void run() {
      try {
        RawImage image = myDevice.getScreenshot(10, TimeUnit.SECONDS);
        myFuture.set(image);
      }
      catch (Throwable t) {
        myFuture.setException(t);
      }
    }

    public Future<RawImage> getRawImage() {
      return myFuture;
    }
  }
}
