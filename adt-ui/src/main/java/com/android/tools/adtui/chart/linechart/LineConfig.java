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

import com.android.tools.adtui.LegendRenderData;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * This class handles the configuration of lines that compose a line chart.
 */
public class LineConfig {

  /**
   * Stroke which can be used with {@link #setStroke(Stroke)} which results in a solid line with
   * default thickness.
   */
  public static final BasicStroke DEFAULT_LINE_STROKE = new BasicStroke(2);

  /**
   * Stroke which can be used with {@link #setStroke(Stroke)} which results in a dashed line with
   * default thickness.
   */
  public static final BasicStroke DEFAULT_DASH_STROKE =
    new BasicStroke(2.0f,
                    BasicStroke.CAP_BUTT,
                    BasicStroke.JOIN_BEVEL,
                    10.0f,  // Miter limit, Swing's default
                    new float[]{8.0f, 5.0f},  // Dash pattern in pixel
                    0.0f);  // Dash phase - just starts at zero.

  //TODO Move colors out of LineConfig
  private static final Color[] COLORS = {
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
   * Whether the series should be represented by a stepped chart.
   * In case it is not, a straight line is drawn between points (e.g. (x0, y0) and (x1, y1)).
   * Otherwise, a line is drawn from (x0, y0) to (x1, y0) and another one is drawn from (x1, y0)
   * to (x1, y1).
   */
  private boolean myIsStepped = false;

  /**
   * Whether the series should be represented by a filled chart, instead of only lines.
   */
  private boolean myIsFilled = false;

  /**
   * Whether the series should stack with other series instead of being independent.
   */
  private boolean myIsStacked = false;

  /**
   * Type of the legend icon that represents the line.
   */
  @Nullable
  private LegendRenderData.IconType myLegendIconType;

  @NotNull
  private Stroke myStroke;

  @NotNull
  private Color mColor;

  public LineConfig(@NotNull Color color) {
    mColor = color;
    myStroke = DEFAULT_LINE_STROKE;
  }

  @NotNull
  public LineConfig setStepped(boolean isStepped) {
    myIsStepped = isStepped;
    return this;
  }

  public boolean isStepped() {
    return myIsStepped;
  }

  @NotNull
  public LineConfig setFilled(boolean isFilled) {
    myIsFilled = isFilled;
    return this;
  }

  public boolean isFilled() {
    return myIsFilled;
  }

  @NotNull
  public LineConfig setStacked(boolean isStacked) {
    myIsStacked = isStacked;
    return this;
  }

  public boolean isStacked() {
    return myIsStacked;
  }

  @NotNull
  public Color getColor() {
    return mColor;
  }

  @NotNull
  public LineConfig setColor(@NotNull Color color) {
    mColor = color;
    return this;
  }

  @NotNull
  public LineConfig setStroke(@NotNull Stroke stroke) {
    myStroke = stroke;
    return this;
  }

  @NotNull
  public Stroke getStroke() {
    return myStroke;
  }

  public LineConfig setLegendIconType(@Nullable LegendRenderData.IconType legendIconType) {
    myLegendIconType = legendIconType;
    return this;
  }

  @Nullable
  public LegendRenderData.IconType getLegendIconType() {
    return myLegendIconType;
  }

  /**
   * @deprecated This is mostly used by visual tests and should really be removed. Color resources can live elsewhere.
   */
  @NotNull
  public static Color getColor(int index) {
    return COLORS[index % COLORS.length];
  }
}
