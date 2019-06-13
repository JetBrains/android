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
import java.util.function.Function;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import org.jetbrains.annotations.Nullable;

/**
 * Class to store all the render data needed to render a legend.
 */
public class LegendConfig {

  @NotNull
  private final Color mColor;
  @NotNull
  private final IconType mIconType;
  @Nullable
  private final Function<String, Icon> mIconGetter;

  /**
   * Render data to be used when rendering the legend.
   *
   * @param iconType  The iconType type to be displayed
   * @param color     The color of the iconType to be associated with the elements in the chart
   */
  public LegendConfig(@NotNull IconType iconType, @NotNull Color color) {
    this(iconType, color, null);
  }

  /**
   * Render data to be used when rendering the legend.
   *
   * @param iconGetter A function that maps the legend string value to a custom icon
   * @param color      The background color of the icon
   */
  public LegendConfig(Function<String, Icon> iconGetter, @NotNull Color color) {
    this(IconType.CUSTOM, color, iconGetter);
  }

  public LegendConfig(@NotNull LineConfig config) {
    this(config.getLegendIconType(), config.getColor());
  }

  private LegendConfig(@NotNull IconType iconType, @NotNull Color color, @Nullable Function<String, Icon> iconGetter) {
    mColor = color;
    mIconGetter = iconGetter;
    mIconType = iconType;
  }

  @NotNull
  public Color getColor() {
    return mColor;
  }

  @NotNull
  public IconType getIconType() {
    return mIconType;
  }

  @Nullable
  public Function<String, Icon> getIconGetter() {
    return mIconGetter;
  }

  public enum IconType {
    NONE,
    LINE,
    DASHED_LINE,
    BOX,
    CUSTOM
  }
}
