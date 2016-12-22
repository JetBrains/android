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

  public enum IconType {
    NONE,
    LINE,
    DOTTED_LINE,
    BOX
  }

  @NotNull
  private final Color mColor;

  @NotNull
  private final IconType mIcon;

  /**
   * Render data to be used when rendering the legend.
   * @param icon   The icon type to be displayed
   * @param color  The color of the icon to be associated with the elements in the chart.
   */
  public LegendConfig(@NotNull IconType icon, @NotNull Color color) {
    mColor = color;
    mIcon = icon;
  }

  public LegendConfig(@NotNull LineConfig config) {
    mColor = config.getColor();
    IconType icon = config.getLegendIconType();
    // Use a default icon type for the line in case there is no icon set in line config.
    // TODO: use LegendRenderData.IconType.DOTTED_LINE for dashed lines
    if (icon == null) {
      mIcon = config.isFilled() ? LegendConfig.IconType.BOX : LegendConfig.IconType.LINE;
    } else {
      mIcon = icon;
    }
  }

  public Color getColor() {
    return mColor;
  }

  public IconType getIcon() {
    return mIcon;
  }
}
