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

import com.android.SdkConstants;
import com.android.resources.ScreenOrientation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;

/**
 * Specifications for device art.
 *
 * The specifications themselves are stored in a json file. The specifications were originally
 * obtained from <a href="http://developer.android.com/distribute/promote/device-art.html">Device Art Generator</a>.
 */
public class DeviceArtSpec {
  private String id;
  private String title;
  private String url;
  private double physicalSize;
  private double physicalHeight;
  private String density;
  private String[] landRes;
  private int[] landOffset;
  private String[] portRes;
  private int[] portOffset;
  private int[] portSize;

  File rootFolder;

  @NotNull
  public String getId() {
    return id;
  }

  @NotNull
  public String getName() {
    return title;
  }

  public double getAspectRatio() {
    return (double) portSize[1] / portSize[0];
  }

  @Nullable
  public File getDropShadow(ScreenOrientation orientation) {
    return getImageRes(orientation, 0);
  }

  @Nullable
  public File getFrame(ScreenOrientation orientation) {
    return getImageRes(orientation, 1);
  }

  @Nullable
  public File getReflectionOverlay(ScreenOrientation orientation) {
    return getImageRes(orientation, 2);
  }

  @Nullable
  private File getImageRes(ScreenOrientation orientation, int index) {
    String path;
    switch (orientation) {
      case PORTRAIT:
        path = "port_" + portRes[index] + SdkConstants.DOT_PNG;
        break;
      case LANDSCAPE:
        path = "land_" + landRes[index] + SdkConstants.DOT_PNG;
        break;
      default:
        return null;
    }

    return new File(rootFolder, path);
  }

  public Point getScreenOffset(ScreenOrientation orientation) {
    int[] offsets = orientation == ScreenOrientation.PORTRAIT ? portOffset : landOffset;
    return new Point(offsets[0], offsets[1]);
  }
}
