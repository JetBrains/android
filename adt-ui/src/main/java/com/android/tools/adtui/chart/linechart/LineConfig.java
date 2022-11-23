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

import com.android.tools.adtui.LegendConfig;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.intellij.ui.JBColor;
import java.util.HashSet;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

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
                    BasicStroke.CAP_SQUARE,
                    BasicStroke.JOIN_BEVEL,
                    10.0f,  // Miter limit, Swing's default
                    new float[]{4.0f, 6.0f},  // Dash pattern in pixel
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
   * Whether data series are bucketed and shown by a bar chart. From the starting data x value,
   * each data point are drawn at previous x + interval. If this is 0, the series is not buckets.
   * The amount unit is same as x axis unit.
   */
  // TODO(b/73784793): Remove this config once we refactor a new BarChart class
  private double myDataBucketInterval = 0;

  /**
   * Whether the series should be represented by a filled chart, instead of only lines.
   */
  private boolean myIsFilled = false;

  /**
   * Whether the series should stack with other series instead of being independent.
   */
  private boolean myIsStacked = false;

  private boolean myAdjustDash = false;

  private boolean myIsDash = false;

  private float myDashLength = 0f;

  private double myAdjustedDashPhase = 0;

  /**
   * Type of the legend icon that represents the line.
   * TODO: extract it to LegendConfig
   */
  @NotNull
  private LegendConfig.IconType myLegendIconType;

  @NotNull
  private Stroke myStroke;

  @NotNull
  private Color mColor;

  public LineConfig(@NotNull Color color) {
    mColor = color;
    myStroke = DEFAULT_LINE_STROKE;
    myLegendIconType = LegendConfig.IconType.NONE;
  }

  public static LineConfig copyOf(@NotNull LineConfig otherConfig) {
    LineConfig config = new LineConfig(otherConfig.getColor());

    config.setStepped(otherConfig.isStepped());
    config.setDataBucketInterval(otherConfig.myDataBucketInterval);
    config.setFilled(otherConfig.isFilled());
    config.setStacked(otherConfig.isStacked());
    config.setAdjustDash(otherConfig.isAdjustDash());
    config.setLegendIconType(otherConfig.getLegendIconType());
    config.setStroke(otherConfig.getStroke());
    config.setAdjustedDashPhase(otherConfig.getAdjustedDashPhase());
    return config;
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
  public LineConfig setDataBucketInterval(double bucketInterval) {
    myDataBucketInterval = bucketInterval;
    return this;
  }

  public double getDataBucketInterval() {
    return myDataBucketInterval;
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

  /**
   * When a line is being drawn with dashes, the dashes can appear to shift/jump around depending on the starting point of the path being
   * drawn. When set to true, {@link LineChart} will attempt to compensate by comparing the starting point of the previous path and
   * adjusts the dash phase on the new path.
   */
  @NotNull
  public LineConfig setAdjustDash(boolean adjustDash) {
    myAdjustDash = adjustDash;
    return this;
  }

  public boolean isAdjustDash() {
    return myAdjustDash;
  }

  public boolean isDash() {
    return myIsDash;
  }

  /**
   * @return the total length of the dash pattern defined in the stroke. zero if the stroke does not contain dashes.
   */
  public float getDashLength() {
    return myDashLength;
  }

  /**
   * Sets the dash phase which should be used for the stroke. The adjusted stroke can be then retrieved via {@link #getAdjustedStroke()}
   */
  public void setAdjustedDashPhase(double dashPhase) {
    myAdjustedDashPhase = dashPhase;
  }

  public double getAdjustedDashPhase() {
    return myAdjustedDashPhase;
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

    if (myStroke instanceof BasicStroke) {
      BasicStroke basicStroke = (BasicStroke)myStroke;
      float[] dashArray = basicStroke.getDashArray();
      myIsDash = dashArray != null;
      myAdjustDash = myIsDash;  // fixed jumping dashes by default.
      myDashLength = 0f;
      if (myIsDash) {
        for (float value : dashArray) {
          myDashLength += value;
        }
      }
    }
    else {
      myIsDash = false;
      myDashLength = 0f;
    }

    return this;
  }

  @NotNull
  public Stroke getStroke() {
    return myStroke;
  }

  @NotNull
  public Stroke getAdjustedStroke() {
    if (!(myStroke instanceof BasicStroke) || !myAdjustDash) {
      return myStroke;
    }

    BasicStroke stroke = (BasicStroke)myStroke;
    return new BasicStroke(stroke.getLineWidth(), stroke.getEndCap(), stroke.getLineJoin(), stroke.getMiterLimit(), stroke.getDashArray(),
                           (float)myAdjustedDashPhase);
  }

  public LineConfig setLegendIconType(@NotNull LegendConfig.IconType legendIconType) {
    myLegendIconType = legendIconType;
    return this;
  }

  @NotNull
  public LegendConfig.IconType getLegendIconType() {
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
