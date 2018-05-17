/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.adtui.actions;

import com.intellij.icons.AllIcons;
import java.util.Arrays;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Describes different types of zoom actions */
public enum ZoomType {
  /**
   * Zoom to fit (the screen view port)
   */
  FIT("Zoom to Fit Screen", AllIcons.General.FitContent),

  /**
   * Zoom to actual size (100%)
   */
  ACTUAL("100%", "Zoom to Actual Size (100%)", AllIcons.General.ActualZoom),

  /**
   * Zoom in
   */
  IN("Zoom In",  AllIcons.General.Add),

  /**
   * Zoom out
   */
  OUT("Zoom Out",  AllIcons.General.Remove);

  ZoomType(@NotNull String label, @Nullable String description, @Nullable Icon icon) {
    myLabel = label;
    myDescription = description;
    myIcon = icon;
  }

  ZoomType(@NotNull String label, @Nullable Icon icon) {
    this(label, null, icon);
  }

  /** Returns the icon for this zoom type when used on the DesignSurface. Null for actions not used on the DesignSurface. */
  @Nullable
  public Icon getIcon() {
    return myIcon;
  }

  /** Returns the label for this zoom type when used on the DesignSurface. */
  public String getLabel() {
    return myLabel;
  }

  /** Returns the label for this zoom type when used on the DesignSurface. */
  @Nullable
  public String getDescription() {
    return myDescription;
  }

  @Override
  public String toString() {
    return getLabel();
  }

  private final String myLabel;
  private final String myDescription;
  private final Icon myIcon;

  // Zoom percentages
  // 25%, 33%, 50%, 67%, 75%, 90%, 100%, 110%, 125%, 150%, 200%, 300%, 400%, .... +100%
  private static final int[] ZOOM_POINTS = new int[] {
    25, 33, 50, 67, 75, 90, 100, 110, 125, 150, 200
  };

  public static int zoomIn(int percentage) {
    return zoomIn(percentage, ZOOM_POINTS);
  }

  public static int zoomIn(int percentage, int[] zoomPoints) {
    int i = Arrays.binarySearch(zoomPoints, percentage);
    if (i < 0) {
      // inexact match: jump to nearest
      i = -i - 1;
      if (i == 0) {
        // If we're far down (like 0.1) don't just jump up to 25%, jump 20% on the current zoom point
        if (percentage < zoomPoints[0] * 0.75) {
          return Math.min((int)(Math.ceil(percentage * 1.2)), zoomPoints[0]);
        }
      }
      if (i < zoomPoints.length) {
        return zoomPoints[i];
      }
      else {
        // Round up to next nearest hundred
        return ((percentage / 100) + 1) * 100;
      }
    }
    else {
      // exact match
      if (i < zoomPoints.length - 1) {
        return zoomPoints[i + 1];
      }
      // Just increase by 100 after this: 200, 300, 400, ...
      return percentage + 100;
    }
  }

  public static int zoomOut(int percentage) {
    return zoomOut(percentage, ZOOM_POINTS);
  }

  public static int zoomOut(int percentage, int[] zoomPoints) {
    int i = Arrays.binarySearch(zoomPoints, percentage);
    if (i < 0) {
      // inexact match: jump to nearest
      i = -i - 1;
      if (i == 0) {
        // If we're far down (like 0.1) don't just jump up to 25%, jump 10%
        return (int)Math.floor(percentage / 1.1);
      }
      if (i < zoomPoints.length) {
        return zoomPoints[i - 1];
      }
      else {
        // Round down to next nearest hundred
        return ((percentage / 100) - 1) * 100;
      }
    }
    else {
      // exact match
      if (i > 0) {
        return zoomPoints[i - 1];
      }
      return (int)Math.floor(percentage / 1.1);
    }
  }
}