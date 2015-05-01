/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.api;

import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

import static com.android.SdkConstants.VALUE_N_DP;
import static com.android.resources.Density.DEFAULT_DENSITY;

/**
 * The UI builder / layout editor as exposed to {@link ViewHandler} instances.
 * This allows the view handlers to query the surrounding editor for more information.
 */
public abstract class ViewEditor {
  public abstract int getDpi();

  /**
   * Converts a device independent pixel to a screen pixel for the current screen density
   *
   * @param dp the device independent pixel dimension
   * @return the corresponding pixel dimension
   */
  public int dpToPx(int dp) {
    int dpi = getDpi();
    return dp * dpi / DEFAULT_DENSITY;
  }

  /**
   * Converts a pixel to a dp (device independent pixel) for the current screen density
   *
   * @param px the pixel dimension (in Android screen pixels)
   * @return the corresponding dp dimension
   */
  public int pxToDp(@AndroidCoordinate int px) {
    int dpi = getDpi();
    return px * DEFAULT_DENSITY / dpi;
  }

  /**
   * Converts a pixel to a dp (device independent pixel) for the current screen density,
   * and returns it as a dimension string.
   *
   * @param px the pixel dimension
   * @return the corresponding dp dimension string
   */
  @NotNull
  public String pxToDpWithUnits(int px) {
    return String.format(Locale.US, VALUE_N_DP, pxToDp(px));
  }
}
