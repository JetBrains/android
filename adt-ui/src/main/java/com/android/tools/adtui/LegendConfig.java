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
package com.android.tools.adtui;

import com.android.tools.adtui.chart.linechart.LineConfig;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Class to store all the render data needed to render a legend.
 */
public class LegendConfig {

  @NotNull
  private final Color mColor;
  @NotNull
  private final IconType mIconType;

  /**
   * Render data to be used when rendering the legend.
   *
   * @param icon  The icon type to be displayed
   * @param color The color of the icon to be associated with the elements in the chart
   */
  public LegendConfig(@NotNull IconType icon, @NotNull Color color) {
    mColor = color;
    mIconType = icon;
  }

  public LegendConfig(@NotNull LineConfig config) {
    this(config.getLegendIconType(), config.getColor());
  }

  @NotNull
  public Color getColor() {
    return mColor;
  }

  @NotNull
  public IconType getIconType() {
    return mIconType;
  }

  public enum IconType {
    NONE,
    LINE,
    DASHED_LINE,
    BOX
  }
}
