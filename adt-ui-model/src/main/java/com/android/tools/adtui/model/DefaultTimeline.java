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
package com.android.tools.adtui.model;

import org.jetbrains.annotations.NotNull;

/**
 * A default implementation of {@link Timeline}.
 */
public class DefaultTimeline implements Timeline {
  protected static final double DEFAULT_ZOOM_RATIO = 0.75;
  public static final double PADDING_RATIO = 0.1;
  public static final double PAN_RATIO_PER_WHEEL_CLICK = 0.1;

  private final Range myDataRange = new Range();
  private final Range myViewRange = new Range();
  private final Range myTooltipRange = new Range();
  private final Range mySelectionRange = new Range();

  /**
   * Ratio to multiply when zooming in and to divide by when zooming out.
   */
  private double myZoomRatio = DEFAULT_ZOOM_RATIO;

  @NotNull
  @Override
  public Range getDataRange() {
    return myDataRange;
  }

  @NotNull
  @Override
  public Range getViewRange() {
    return myViewRange;
  }

  @NotNull
  @Override
  public Range getTooltipRange() {
    return myTooltipRange;
  }

  @NotNull
  @Override
  public Range getSelectionRange() {
    return mySelectionRange;
  }

  public void setZoomRatio(double zoomRatio) {
    myZoomRatio = zoomRatio;
  }

  @Override
  public void zoomIn() {
    zoomIn(0.5);
  }

  @Override
  public void zoomOut() {
    zoomOut(0.5);
  }

  @Override
  public void resetZoom() {
    myViewRange.set(myDataRange);
  }

  @Override
  public void frameViewToRange(@NotNull Range targetRange) {
    myViewRange.set(targetRange.getIntersection(myDataRange));
  }

  @Override
  public void handleMouseWheelZoom(double count, double anchor) {
    if (count < 0) {
      while (count < 0) {
        zoomIn(anchor);
        count++;
      }
    }
    else if (count > 0) {
      while (count > 0) {
        zoomOut(anchor);
        count--;
      }
    }
  }

  @Override
  public void handleMouseWheelPan(double count) {
    panView(count * myDataRange.getLength() * PAN_RATIO_PER_WHEEL_CLICK);
  }

  private void zoomIn(double ratio) {
    double min = myViewRange.getMin();
    double max = myViewRange.getMax();
    double mid = min + (max - min) * ratio;
    double newMin = mid - (mid - min) * myZoomRatio;
    double newMax = mid + (max - mid) * myZoomRatio;
    if (newMin < newMax) {
      // Only Zoom in when it doesn't collapse to a point or an empty range.
      myViewRange.set(newMin, newMax);
    }
  }

  private void zoomOut(double ratio) {
    double min = myViewRange.getMin();
    double max = myViewRange.getMax();
    double mid = min + (max - min) * ratio;
    double newMin = mid - (mid - min) / myZoomRatio;
    double newMax = mid + (max - mid) / myZoomRatio;
    myViewRange.set(new Range(newMin, newMax).getIntersection(myDataRange));
  }
}
