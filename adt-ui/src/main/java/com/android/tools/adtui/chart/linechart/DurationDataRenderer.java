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

import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.event.MouseEvent;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.android.tools.adtui.model.DurationData.UNSPECIFIED_DURATION;

/**
 * A custom renderer to support drawing {@link DurationData} over line charts
 */
public final class DurationDataRenderer<E extends DurationData> extends AspectObserver implements LineChartCustomRenderer {
  /**
   * Percentage of screen dimension the icon+label for the DurationData will be offset.
   */
  private static final float DISPLAY_OFFSET_PERCENTAGE = 0.005f;
  private static final int CLICK_REGION_DRAW_PADDING_Y = 2;
  private static final int CLICK_REGION_DRAW_PADDING_X = 4;
  public static final int LABEL_ARCH_LENGTH = 7;

  @NotNull private DurationDataModel<E> myModel;

  @NotNull private final Color myColor;
  private boolean myIsBlocking = false;
  @Nullable private Icon myIcon = null;
  @Nullable private Stroke myStroke = null;
  @Nullable private Function<E, String> myTooltipProvider = null;
  @Nullable private Function<E, String> myLabelProvider = null;
  @Nullable private Consumer<E> myClickHandler = null;
  @Nullable private Color myLabelBgColor = null;
  @Nullable private Color myLabelHoveredBgColor = null;
  @Nullable private Color myLabelClickedBgColor = null;
  @Nullable private Color myLabelTextColor = null;

  @NotNull private final List<Rectangle2D.Float> myPathCache = new ArrayList<>();
  @NotNull private final List<E> myDataCache = new ArrayList<>();
  @NotNull private final List<JLabel> myLabelCache = new ArrayList<>();

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
    myIsBlocking = builder.myIsBlocking;
    myIcon = builder.myIcon;
    myStroke = builder.myStroke;
    myTooltipProvider = builder.myTooltipProvider;
    myLabelProvider = builder.myLabelProvider;
    myClickHandler = builder.myClickHandler;
    myLabelBgColor = builder.myLabelBgColor;
    myLabelHoveredBgColor = builder.myLabelHoveredBgColor;
    myLabelClickedBgColor = builder.myLabelClickedBgColor;
    myLabelTextColor = builder.myLabelTextColor;

    myModel.addDependency(this).onChange(DurationDataModel.Aspect.DURATION_DATA, this::modelChanged);
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
    double xMax = series.getXRange().getMax();
    double xLength = series.getXRange().getLength();
    List<SeriesData<E>> seriesList = series.getSeries();
    List<SeriesData<Long>> attachedSeriesList = attached != null ? attached.getSeries() : null;
    int j = 0;
    float attachY = 1;
    for (int i = 0; i < seriesList.size(); i++) {
      Rectangle2D.Float rect = new Rectangle2D.Float();
      SeriesData<E> data = seriesList.get(i);
      double xStart = (data.x - xMin) / (xMax - xMin);
      double xDuration = data.value.getDuration() == UNSPECIFIED_DURATION ?
                         (xMax - data.x) / xLength : data.value.getDuration() / xLength;
      rect.setRect(xStart, 0, xDuration, 1);
      myPathCache.add(rect);
      myDataCache.add(data.value);

      Rectangle2D.Float clickRegion = new Rectangle2D.Float();
      myClickRegionCache.add(clickRegion);
      // If the DurationData series is attached to a line series, finds the Y value on the line series closest to the current DurationData.
      // This will be used as the y position to draw the icon +/ label.
      if (attachedSeriesList != null) {
        double yMin = attached.getYRange().getMin();
        double yMax = attached.getYRange().getMax();
        for (; j < attachedSeriesList.size(); j++) {
          SeriesData<Long> seriesData = attachedSeriesList.get(j);
          double attachXStart = (seriesData.x - xMin) / (xMax - xMin);
          if (attachXStart > xStart) {
            // find the data point on the attach series greater than the duration data's start point
            // use the last found y value.
            break;
          }
          attachY = (float)(1 - (seriesData.value - yMin) / (yMax - yMin));
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
        label.setFont(AdtUiUtils.DEFAULT_FONT);
        label.setForeground(myLabelTextColor);
        Dimension size = label.getPreferredSize();
        label.setBounds(0, 0, size.width, size.height);
        myLabelCache.add(label);

        regionWidth += size.getWidth(); // TODO padding between label + icon?
        regionHeight = Math.max(regionHeight, size.getHeight());
      }

      if (regionWidth > 0) {
        clickRegion.setRect(xStart + DISPLAY_OFFSET_PERCENTAGE, attachY - DISPLAY_OFFSET_PERCENTAGE, regionWidth, regionHeight);
      }
    }
  }

  @Override
  public void renderLines(@NotNull Component host,
                          @NotNull Graphics2D g2d,
                          @NotNull List<Path2D> transformedPaths,
                          @NotNull List<LineConfig> configs) {
    Shape originalClip = g2d.getClip();
    Dimension dim = host.getSize();
    if (myIsBlocking) {
      // Grey out
      Rectangle2D clipRect = new Rectangle2D.Float();
      for (Rectangle2D.Float rect : myPathCache) {
        double scaledXStart = rect.x * dim.getWidth();
        double scaledXDuration = rect.width * dim.getWidth();
        double newX = Math.max(scaledXStart, originalClip.getBounds().getX());
        clipRect.setRect(newX,
                         0,
                         Math.min(scaledXDuration + scaledXStart - newX,
                                  originalClip.getBounds().getX() + originalClip.getBounds().getWidth() - newX),
                         dim.getHeight());

        // Clear the region by repainting the background
        g2d.setColor(host.getBackground());
        g2d.fill(clipRect);

        // Set clip region and redraw the lines in grayscale.
        g2d.setClip(clipRect);
        LineChart.drawLines(g2d, transformedPaths, configs, true);
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
        double scaledXStart = rect.x * host.getWidth();
        double scaledXDuration = rect.width * host.getWidth();
        g2d.translate(scaledXStart, 0);
        eventLine.setLine(0, 0, 0, host.getHeight());
        g2d.draw(eventLine);
        eventLine.setLine(scaledXDuration, 0, scaledXDuration, host.getHeight());
        g2d.draw(eventLine);
        g2d.translate(-scaledXStart, 0);
      }
    }
  }

  public void renderOverlay(@NotNull Component host, @NotNull Graphics2D g2d) {
    assert myClickRegionCache.size() == myDataCache.size();
    for (int i = 0; i < myClickRegionCache.size(); i++) {
      Rectangle2D.Float rect = myClickRegionCache.get(i);
      float scaledStartX = rect.x * host.getWidth();
      float scaledStartY = rect.y * host.getHeight() - rect.height;
      if (myLabelBgColor != null) {
        g2d.setColor(myLabelBgColor);
        RoundRectangle2D scaledRect = new RoundRectangle2D.Float(scaledStartX - CLICK_REGION_DRAW_PADDING_X,
                                                                 scaledStartY - CLICK_REGION_DRAW_PADDING_Y,
                                                                 rect.width + CLICK_REGION_DRAW_PADDING_X * 2,
                                                                 rect.height + CLICK_REGION_DRAW_PADDING_Y * 2,
                                                                 LABEL_ARCH_LENGTH,
                                                                 LABEL_ARCH_LENGTH);
        if (myMousePosition != null && scaledRect.contains(myMousePosition)) {
          g2d.setColor(myClick && myClickHandler != null ? myLabelClickedBgColor : myLabelHoveredBgColor);
        }
        g2d.fill(scaledRect);
      }

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
      Rectangle2D.Float scaledRect = new Rectangle2D.Float(rect.x * dim.width,
                                                           rect.y * dim.height - rect.height,
                                                           rect.width,
                                                           rect.height);
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

  public static class Builder<E extends DurationData> {
    // Required
    @NotNull private final DurationDataModel<E> myModel;
    @NotNull private final Color myColor;

    // Optional
    // TODO add config to allow changing the colors of line series underneath the DurationData.
    private boolean myIsBlocking = false;
    @Nullable private Icon myIcon = null;
    @Nullable private Stroke myStroke = null;
    @Nullable private Function<E, String> myTooltipProvider = null;
    @Nullable private Function<E, String> myLabelProvider = null;
    @Nullable private Consumer<E> myClickHandler = null;
    @Nullable private Color myLabelBgColor = null;
    @Nullable private Color myLabelHoveredBgColor = null;
    @Nullable private Color myLabelClickedBgColor = null;
    @Nullable private Color myLabelTextColor = null;

    public Builder(@NotNull DurationDataModel<E> model, @NotNull Color color) {
      myModel = model;
      myColor = color;
    }

    /**
     * If true, the renderer will gray out the line series underneath the DurationData.
     */
    public Builder<E> setIsBlocking(boolean value) {
      myIsBlocking = value;
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
     * Sets the tooltip provider.
     */
    public Builder<E> setTooltipProvider(@NotNull Function<E, String> provider) {
      myTooltipProvider = provider;
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
    public Builder<E> setLabelColors(@NotNull Color bgColor, @NotNull Color hoveredColor, @NotNull Color clickedColor, @NotNull Color color) {
      myLabelBgColor = bgColor;
      myLabelHoveredBgColor = hoveredColor;
      myLabelClickedBgColor = clickedColor;
      myLabelTextColor = color;
      return this;
    }

    @NotNull
    public DurationDataRenderer<E> build() {
      return new DurationDataRenderer<>(myModel, this);
    }
  }
}
