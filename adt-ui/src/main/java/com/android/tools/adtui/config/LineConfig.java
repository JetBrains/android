/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.adtui.config;

import com.android.annotations.NonNull;

import java.awt.*;

/**
 * This class handles the configuration of lines that compose a line chart.
 */
public class LineConfig {

  public static final Color[] COLORS = {
    new Color(0x6baed6),
    new Color(0xff0000),
    new Color(0xfd8d3c),
    new Color(0x00ffa2),
    new Color(0x000ff0),
    new Color(0xc7e9c0),
    new Color(0x9e9ac8),
    new Color(0xdadaeb),
    new Color(0x969696),
    new Color(0xd9d9d9),
  };

  /**
   * Stroke style to be used in dashed lines.
   */
  public static final Stroke DASHED_STROKE = new BasicStroke(1, BasicStroke.CAP_BUTT,
                                                             BasicStroke.JOIN_MITER, 10, new float[]{10}, 0);

  /**
   * Stroke style to be used in continuous lines.
   */
  public static final Stroke BASIC_STROKE = new BasicStroke();

  /**
   * Whether the series should be represented by dashed lines.
   */
  private boolean mIsDashed = false;

  /**
   * Whether the series should be represented by a stepped chart.
   * In case it is not, a straight line is drawn between points (e.g. (x0, y0) and (x1, y1)).
   * Otherwise, a line is drawn from (x0, y0) to (x1, y0) and another one is drawn from (x1, y0)
   * to (x1, y1).
   */
  private boolean mIsStepped = false;

  /**
   * Whether the series should be represented by a filled chart, instead of only lines.
   */
  private boolean mIsFilled = false;

  /**
   * Whether the series should stack with other series instead of being independent.
   */
  private boolean mIsStacked = false;

  @NonNull
  private Color mColor;

  public LineConfig(@NonNull Color color) {
    mColor = color;
  }

  public void setDashed(boolean isDashed) {
    mIsDashed = isDashed;
  }

  public boolean isDashed() {
    return mIsDashed;
  }

  public void setStepped(boolean isStepped) {
    mIsStepped = isStepped;
  }

  public boolean isStepped() {
    return mIsStepped;
  }

  public void setFilled(boolean isFilled) {
    mIsFilled = isFilled;
  }

  public boolean isFilled() {
    return mIsFilled;
  }

  public void setStacked(boolean isStacked) {
    mIsStacked = isStacked;
  }

  public boolean isStacked() {
    return mIsStacked;
  }

  @NonNull
  public Color getColor() {
    return mColor;
  }

  public void setColor(@NonNull Color color) {
    mColor = color;
  }
}
