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

package com.android.tools.adtui.chart.linechart;

import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * This class handles the configuration of lines that compose a line chart.
 */
public class LineConfig {

  //TODO Move colors out of LineConfig
  public static final Color[] COLORS = {
    new JBColor(0x6baed6, 0x6baed6),
    new JBColor(0xff0000, 0xff0000),
    new JBColor(0xfd8d3c, 0xfd8d3c),
    new JBColor(0x00ffa2, 0x00ffa2),
    new JBColor(0x000ff0, 0x000ff0),
    new JBColor(0xc7e9c0, 0xc7e9c0),
    new JBColor(0x9e9ac8, 0x9e9ac8),
    new JBColor(0xdadaeb, 0xdadaeb),
    new JBColor(0x969696, 0x969696),
    new JBColor(0xd9d9d9, 0xd9d9d9),
  };

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

  @NotNull
  private Color mColor;

  public LineConfig(@NotNull Color color) {
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

  @NotNull
  public Color getColor() {
    return mColor;
  }

  public void setColor(@NotNull Color color) {
    mColor = color;
  }
}
