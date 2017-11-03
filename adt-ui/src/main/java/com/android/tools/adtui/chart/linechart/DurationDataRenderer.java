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

import com.android.annotations.VisibleForTesting;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A custom renderer to support drawing {@link DurationData} over line charts
 */
public final class DurationDataRenderer<E extends DurationData> extends AspectObserver implements LineChartCustomRenderer {

  static final float EPSILON = 1e-6f;

  /**
   * Percentage of screen dimension the icon+label for the DurationData will be offset.
   */
  private static final int CLICK_REGION_PADDING_Y = 2;
  private static final int CLICK_REGION_PADDING_X = 4;

  @NotNull private DurationDataModel<E> myModel;

  @NotNull private final Color myColor;
  @Nullable private final Color myDurationBgColor;

  @Nullable private Icon myIcon = null;
  @Nullable private Stroke myStroke = null;
  @Nullable private Function<E, String> myLabelProvider = null;
  @Nullable private Consumer<E> myClickHandler = null;
  @Nullable private Color myLabelBgColor = null;
  @Nullable private Color myLabelHoveredBgColor = null;
  @Nullable private Color myLabelClickedBgColor = null;
  @Nullable private Color myLabelTextColor = null;
  private float myLineStrokeOffset;
  private float myLabelXOffset;
  private float myLabelYOffset;

  @NotNull private final List<Rectangle2D.Float> myPathCache = new ArrayList<>();
  @NotNull private final List<E> myDataCache = new ArrayList<>();
  @NotNull private final List<JLabel> myLabelCache = new ArrayList<>();

  @NotNull private final Map<RangedContinuousSeries, LineConfig> myCustomLineConfigs = new HashMap<>();

  /**
   * Cached rectangles calculated during updataData used for detecting if a DurationData label has been clicked on.
   * Note that the x+y values (unknown actual component dimension at updateData time) stored are normalized,
   * but the width+height values (predetermined by the icon+label dimensions) are not.
   */
  @NotNull private final List<Rectangle2D.Float> myClickRegionCache = new ArrayList<>();

  private Point myMousePosition;
  private boolean myClick;

  public DurationDataRenderer(@NotNull DurationDataModel<E> model, @NotNull Builder builder) {
    myModel = model;
    myColor = builder.myColor;
    myDurationBgColor = builder.myDurationBgColor;
    myIcon = builder.myIcon;
    myStroke = builder.myStroke;
    myLabelProvider = builder.myLabelProvider;
    myClickHandler = builder.myClickHandler;
    myLabelBgColor = builder.myLabelBgColor;
    myLabelHoveredBgColor = builder.myLabelHoveredBgColor;
    myLabelClickedBgColor = builder.myLabelClickedBgColor;
    myLabelTextColor = builder.myLabelTextColor;
    myLabelXOffset = builder.myLabelXOffset;
    myLabelYOffset = builder.myLabelYOffset;
    if (myStroke instanceof BasicStroke) {
      BasicStroke stroke = (BasicStroke)myStroke;
      myLineStrokeOffset = stroke.getLineWidth() / 2f;
    }
    else {
      myLineStrokeOffset = 0;
    }

    myModel.addDependency(this).onChange(DurationDataModel.Aspect.DURATION_DATA, this::modelChanged);
  }

  public void addCustomLineConfig(@NotNull RangedContinuousSeries series, @NotNull LineConfig config) {
    myCustomLineConfigs.put(series, config);
  }

  @VisibleForTesting
  LineConfig getCustomLineConfig(@NotNull RangedContinuousSeries series) {
    return myCustomLineConfigs.get(series);
  }

  private void modelChanged() {
    // Generate the rectangle regions for the duration data series
    myDataCache.clear();
    myClickRegionCache.clear();
    myPathCache.clear();
    myLabelCache.clear();

    RangedSeries<E> series = myModel.getSeries();
    RangedContinuousSeries attached = myModel.getAttachedSeries();
    double xMin = series.getXRange().getMin();
    double xLength = series.getXRange().getLength();
    List<SeriesData<E>> seriesList = series.getSeries();
    List<SeriesData<Long>> attachedSeriesList = attached != null ? attached.getSeries() : null;
    int j = 0;
    SeriesData<Long> lastFoundData = null;
    for (int i = 0; i < seriesList.size(); i++) {
      Rectangle2D.Float rect = new Rectangle2D.Float();
      SeriesData<E> data = seriesList.get(i);
      double yStart = 1;
      double xStart = (data.x - xMin) / xLength;
      double xDuration = data.value.getDuration() / xLength;
      rect.setRect(xStart, 0, xDuration, 1);
      myPathCache.add(rect);
      myDataCache.add(data.value);

      Rectangle2D.Float clickRegion = new Rectangle2D.Float();
      myClickRegionCache.add(clickRegion);
      // If the DurationData series is attached to a line series, finds the Y value on the line series matching the current DurationData.
      // This will be used as the y position to draw the icon +/ label.
      if (attachedSeriesList != null) {
        double yMin = attached.getYRange().getMin();
        double yMax = attached.getYRange().getMax();
        for (; j < attachedSeriesList.size(); j++) {
          SeriesData<Long> seriesData = attachedSeriesList.get(j);
          if (seriesData.x - data.x > EPSILON) {
            // Stop as soon as we found a point on the attached series greater than the duration data's start point.
            if (lastFoundData == null) {
              // If the duraiton data is before the first data point on the attached series, simply places the DurationData
              // at the bottom (yStart == 1), as we have nothing to attach to.
              break;
            }
            // Interpolate the y value in case the attached series and the duration data series do not match.
            assert myModel.getInterpolatable() != null;
            double adjustedY = myModel.getInterpolatable().interpolate(lastFoundData, seriesData, data.x);
            yStart = 1 - (adjustedY - yMin) / (yMax - yMin);
            break;
          }
          lastFoundData = seriesData;
        }
      }

      double regionWidth = 0;
      double regionHeight = 0;
      if (myIcon != null) {
        regionWidth += myIcon.getIconWidth();
        regionHeight += myIcon.getIconHeight();
      }

      if (myLabelProvider != null) {
        JLabel label = new JLabel(myLabelProvider.apply(data.value));
        label.setFont(AdtUiUtils.DEFAULT_FONT.deriveFont(9f));
        label.setForeground(myLabelTextColor);
        Dimension size = label.getPreferredSize();
        label.setBounds(0, 0, size.width, size.height);
        myLabelCache.add(label);

        regionWidth += size.getWidth(); // TODO padding between label + icon?
        regionHeight = Math.max(regionHeight, size.getHeight());
      }

      if (regionWidth > 0) {
        // x and y values are normalized here and not accounting for extra paddings. These will be added back in place where the values are
        // scaled back to the host's size.
        clickRegion.setRect(xStart, yStart, regionWidth, regionHeight);
      }
    }
  }

  @Override
  public void renderLines(@NotNull LineChart lineChart,
                          @NotNull Graphics2D g2d,
                          @NotNull List<Path2D> transformedPaths,
                          @NotNull List<RangedContinuousSeries> series) {
    if (myDurationBgColor != null || !myCustomLineConfigs.isEmpty()) {
      Shape originalClip = g2d.getClip();
      Dimension dim = lineChart.getSize();

      Rectangle2D clipRect = new Rectangle2D.Float();
      // Build the list of configs for the corresponding series. Use a custom config if it has been specified, otherwise grab the default
      // config from the LineChart.
      List<LineConfig> configs = new ArrayList<>(series.size());
      for (RangedContinuousSeries rangedSeries : series) {
        LineConfig config = lineChart.getLineConfig(rangedSeries);
        if (myCustomLineConfigs.containsKey(rangedSeries)) {
          LineConfig customConfig = myCustomLineConfigs.get(rangedSeries);
          // Dash phases can be modified during the LineChart update loop, so we have to copy any changes over to the custom config.
          if (config.isAdjustDash() && customConfig.isAdjustDash()) {
            customConfig.setAdjustedDashPhase(config.getAdjustedDashPhase());
          }
          config = customConfig;
        }
        configs.add(config);
      }

      for (Rectangle2D.Float rect : myPathCache) {
        double scaledXStart = rect.x * dim.getWidth();
        double scaledXDuration = rect.width * dim.getWidth();
        double newX = Math.max(scaledXStart, originalClip.getBounds().getX());
        clipRect.setRect(newX,
                         0,
                         Math.min(scaledXDuration + scaledXStart - newX,
                                  originalClip.getBounds().getX() + originalClip.getBounds().getWidth() - newX),
                         dim.getHeight());

        // Paint the background
        g2d.setColor(myDurationBgColor == null ? lineChart.getBackground() : myDurationBgColor);
        g2d.fill(clipRect);
        g2d.setClip(clipRect);
        // Redraw lines in clipRect.
        LineChart.drawLines(g2d, transformedPaths, configs);
        g2d.setClip(originalClip);
      }
    }

    // Draw the start/end lines if stroke has been set.
    if (myStroke != null) {
      g2d.setColor(myColor);
      g2d.setStroke(myStroke);
      Line2D eventLine = new Line2D.Float();
      for (int i = 0; i < myPathCache.size(); i++) {
        Rectangle2D.Float rect = myPathCache.get(i);
        double scaledXStart = rect.x * lineChart.getWidth();
        double scaledXDuration = rect.width * lineChart.getWidth();
        g2d.translate(scaledXStart, 0);
        eventLine.setLine(0, 0, 0, lineChart.getHeight());
        g2d.draw(eventLine);
        eventLine.setLine(scaledXDuration, 0, scaledXDuration, lineChart.getHeight());
        g2d.draw(eventLine);
        g2d.translate(-scaledXStart, 0);
      }
    }
  }

  public void renderOverlay(@NotNull Component host, @NotNull Graphics2D g2d) {
    assert myClickRegionCache.size() == myDataCache.size();

    for (int i = 0; i < myClickRegionCache.size(); i++) {
      Rectangle2D.Float rect = myClickRegionCache.get(i);
      float paddedHeight = rect.height + CLICK_REGION_PADDING_Y * 2;
      float paddedWidth = rect.width + CLICK_REGION_PADDING_X * 2;
      float scaledStartX = rect.x * host.getWidth() + myLabelXOffset + myLineStrokeOffset;
      float scaledStartY = getClampedLabelY(rect.y, paddedHeight, host.getHeight());
      if (myLabelBgColor != null) {
        g2d.setColor(myLabelBgColor);
        Rectangle2D scaledRect = new Rectangle2D.Float(scaledStartX,
                                                       scaledStartY,
                                                       paddedWidth,
                                                       paddedHeight);
        if (myMousePosition != null && scaledRect.contains(myMousePosition)) {
          g2d.setColor(myClick && myClickHandler != null ? myLabelClickedBgColor : myLabelHoveredBgColor);
        }
        g2d.fill(scaledRect);
      }

      scaledStartX += CLICK_REGION_PADDING_X;
      scaledStartY += CLICK_REGION_PADDING_Y;
      g2d.translate(scaledStartX, scaledStartY);
      if (myIcon != null) {
        myIcon.paintIcon(host, g2d, 0, 0);
        float shift = myIcon.getIconWidth();
        g2d.translate(shift, 0);
        scaledStartX += shift;  // keep track of the amount of shift to revert the translate at the end.
      }

      if (myLabelProvider != null) {
        myLabelCache.get(i).paint(g2d);
      }

      g2d.translate(-scaledStartX, -scaledStartY);
    }

    myClick = false;
  }

  public boolean handleMouseEvent(@NotNull MouseEvent event) {
    myMousePosition = event.getPoint();
    myClick = event.getClickCount() > 0;
    if (myClickHandler == null || !myClick) {
      return false;
    }

    assert myDataCache.size() == myClickRegionCache.size();
    E hitData = null;
    Dimension dim = event.getComponent().getSize();
    for (int i = 0; i < myClickRegionCache.size(); i++) {
      Rectangle2D.Float rect = myClickRegionCache.get(i);
      float paddedHeight = rect.height + CLICK_REGION_PADDING_Y * 2;
      float paddedWidth = rect.width + CLICK_REGION_PADDING_X * 2;
      float scaledY = getClampedLabelY(rect.y, paddedHeight, dim.height);
      Rectangle2D.Float scaledRect = new Rectangle2D.Float(rect.x * dim.width + myLabelXOffset + myLineStrokeOffset,
                                                           scaledY,
                                                           paddedWidth,
                                                           paddedWidth);
      if (scaledRect.contains(myMousePosition)) {
        // Return the first hit region.
        hitData = myDataCache.get(i);
        break;
      }
    }

    if (hitData != null) {
      myClickHandler.accept(hitData);
      return true;
    }

    return false;
  }

  /**
   * Clamp and return the y position (accounting for height + custom offsets) of the label so that it is always within bounds of the host.
   */
  private float getClampedLabelY(float normalizedY, float height, int hostHeight) {
    float maxScaledY = hostHeight - height;
    float scaledY = normalizedY * hostHeight - height + myLabelYOffset;
    return Math.max(0, Math.min(scaledY, maxScaledY));
  }

  @VisibleForTesting
  List<Rectangle2D.Float> GetDurationDataRegions() {
    return myClickRegionCache;
  }

  public static class Builder<E extends DurationData> {
    // Required
    @NotNull private final DurationDataModel<E> myModel;
    @NotNull private final Color myColor;
    @Nullable private Color myDurationBgColor;
    @Nullable private Icon myIcon = null;
    @Nullable private Stroke myStroke = null;
    @Nullable private Function<E, String> myLabelProvider = null;
    @Nullable private Consumer<E> myClickHandler = null;
    @Nullable private Color myLabelBgColor = null;
    @Nullable private Color myLabelHoveredBgColor = null;
    @Nullable private Color myLabelClickedBgColor = null;
    @Nullable private Color myLabelTextColor = null;
    private float myLabelXOffset;
    private float myLabelYOffset;

    public Builder(@NotNull DurationDataModel<E> model, @NotNull Color color) {
      myModel = model;
      myColor = color;
    }

    public Builder<E> setDurationBg(@NotNull Color durationBgColor) {
      myDurationBgColor = durationBgColor;
      return this;
    }

    /**
     * Sets the icon which will be drawn at the start point of each DurationData.
     */
    public Builder<E> setIcon(@NotNull Icon icon) {
      myIcon = icon;
      return this;
    }

    /**
     * Sets the stroke of the lines that mark the start and end of the DurationData.
     */
    public Builder<E> setStroke(@NotNull Stroke stroke) {
      myStroke = stroke;
      return this;
    }

    /**
     * Sets the provider of the string which will be drawn at the start point of each DurationData.
     */
    public Builder<E> setLabelProvider(@NotNull Function<E, String> provider) {
      myLabelProvider = provider;
      return this;
    }

    /**
     * If set, the handler will get triggered when the user clicked on the icon+label region of the DurationData.
     */
    public Builder<E> setClickHander(@NotNull Consumer<E> handler) {
      myClickHandler = handler;
      return this;
    }

    /**
     * Sets the colors to use for the label.
     */
    public Builder<E> setLabelColors(@NotNull Color bgColor,
                                     @NotNull Color hoveredColor,
                                     @NotNull Color clickedColor,
                                     @NotNull Color color) {
      myLabelBgColor = bgColor;
      myLabelHoveredBgColor = hoveredColor;
      myLabelClickedBgColor = clickedColor;
      myLabelTextColor = color;
      return this;
    }

    public Builder<E> setLabelOffsets(float xOffset, float yOffset) {
      myLabelXOffset = xOffset;
      myLabelYOffset = yOffset;
      return this;
    }

    @NotNull
    public DurationDataRenderer<E> build() {
      return new DurationDataRenderer<>(myModel, this);
    }
  }
}
