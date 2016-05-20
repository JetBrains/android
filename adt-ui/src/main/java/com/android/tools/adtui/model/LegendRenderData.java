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
package com.android.tools.adtui.model;

import org.jetbrains.annotations.NotNull;

import java.awt.Color;

/**
 * Class to store all the render data needed to render a legend.
 */
public class LegendRenderData {
  public enum IconType {
    NONE,
    LINE,
    BOX
  }

  @NotNull
  private final Color mColor;

  @NotNull
  private final IconType mIcon;
  private final ReportingSeries mSeries;

  public Color getColor() {
    return mColor;
  }

  public IconType getIcon() {
    return mIcon;
  }

  public ReportingSeries getSeries() {
    return mSeries;
  }

  /**
   * Render data to be used when rendering the legend. The only optional parameter is the series. If the series is null the legend renders
   * the icon, and the label without any additional processing. If the series is populated it is used to as a postfix to the label passed
   * in.
   * @param label The prefix, or label to be drawn.
   * @param icon The icon type to be displayed
   * @param color The color of the icon to be associated with the elements in the chart.
   * @param series Series data to be used for gathering the latest value.
   */
  public LegendRenderData(@NotNull IconType icon, @NotNull Color color, ReportingSeries series) {
    mColor = color;
    mIcon = icon;
    mSeries = series;
  }
}
