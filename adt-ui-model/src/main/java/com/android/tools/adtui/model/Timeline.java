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
 * A timeline that exposes range data.
 */
public interface Timeline {
  /**
   * @return entire range of the underlying data.
   */
  @NotNull
  Range getDataRange();

  /**
   * @return range of the currently visible section.
   */
  @NotNull
  Range getViewRange();

  /**
   * @return range of the data shown by tooltip.
   */
  @NotNull
  Range getTooltipRange();

  /**
   * @return range of currently selected data.
   */
  @NotNull
  Range getSelectionRange();

  /**
   * Decrease view range.
   */
  void zoomIn();

  /**
   * Increase view range.
   */
  void zoomOut();

  /**
   * Set view range to the entire data range.
   */
  void resetZoom();

  /**
   * Calculates a zoom within the current data bounds. If a zoom extends beyond data max the left over is applied to the view minimum.
   *
   * @param deltaUs the amount of time request to change the view by.
   * @param ratio a ratio between 0 and 1 that determines the focal point of the zoom. 1 applies the full delta to the min while 0 applies
   *                the full delta to the max.
   */
  void zoom(double deltaUs, double ratio);

  /**
   * Set view range to the given range.
   */
  void frameViewToRange(Range targetRange);

  /**
   * Pan view range based on the given value.
   *
   * @param delta negative to pan left, positive to pan right.
   */
  default void panView(double delta) {
    if (getViewRange().getMin() + delta < getDataRange().getMin()) {
      delta = getDataRange().getMin() - getViewRange().getMin();
    }
    else if (getViewRange().getMax() + delta > getDataRange().getMax()) {
      delta = getDataRange().getMax() - getViewRange().getMax();
    }
    getViewRange().shift(delta);
  }
}
