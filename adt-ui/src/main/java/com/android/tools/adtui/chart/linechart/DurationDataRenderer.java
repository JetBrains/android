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

import com.android.tools.adtui.Animatable;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.DurationData;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.SeriesData;
import com.intellij.util.containers.ImmutableList;
import gnu.trove.TFloatArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.android.tools.adtui.model.DurationData.UNSPECIFIED_DURATION;

/**
 * A custom renderer to support drawing {@link DurationData} over line charts
 */
public final class DurationDataRenderer<E extends DurationData> implements Animatable, LineChartCustomRenderer {

  /**
   * Percentage of screen dimension the icon+label for the DurationData will be offset.
   */
  private static final float DISPLAY_OFFSET_PERCENTAGE = 0.005f;

  @NotNull private final RangedSeries<E> mySeries;
  @NotNull private final Color myColor;
  private boolean myIsBlocking = false;
  @Nullable private Icon myIcon = null;
  @Nullable private Stroke myStroke = null;
  @Nullable private RangedContinuousSeries myAttachedLineSeries = null;
  @Nullable private Function<E, String> myTooltipProvider = null;
  @Nullable private Function<E, String> myLabelProvider = null;
  @Nullable private Consumer<E> myClickHandler = null;

  @NotNull private final List<Rectangle2D.Float> myPathCache = new ArrayList<>();
  @NotNull private final List<E> myDataCache = new ArrayList<>();
  @NotNull private final List<JLabel> myLabelCache = new ArrayList<>();
  @NotNull private final TFloatArrayList myCachedYPositions = new TFloatArrayList();

  public DurationDataRenderer(@NotNull Builder builder) {
    mySeries = builder.mySeries;
    myColor = builder.myColor;
    myIsBlocking = builder.myIsBlocking;
    myIcon = builder.myIcon;
    myStroke = builder.myStroke;
    myAttachedLineSeries = builder.myAttachedLineSeries;
    myTooltipProvider = builder.myTooltipProvider;
    myLabelProvider = builder.myLabelProvider;
    myClickHandler = builder.myClickHandler;
  }

  @Override
  public void animate(float frameLength) {
    // No-op - do everything in postAnimate in case we are attaching to a line series which needs to perform logic in its animate first.
  }

  @Override
  public void postAnimate() {
    // Generate the rectangle regions for the duration data series
    myDataCache.clear();
    myPathCache.clear();
    myLabelCache.clear();
    myCachedYPositions.clear();

    double xMin = mySeries.getXRange().getMin();
    double xMax = mySeries.getXRange().getMax();
    double xLength = mySeries.getXRange().getLength();
    ImmutableList<SeriesData<E>> seriesList = mySeries.getSeries();
    ImmutableList<SeriesData<Long>> attachedSeriesList = myAttachedLineSeries != null ? myAttachedLineSeries.getSeries() : null;
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

      // If the DurationData series is attached to a line series, finds the Y value on the line series closest to the current DurationData.
      // This will be used as the y position to draw the icon +/ label.
      if (attachedSeriesList != null) {
        double yMin = myAttachedLineSeries.getYRange().getMin();
        double yMax = myAttachedLineSeries.getYRange().getMax();
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
      myCachedYPositions.add(attachY);

      // Cache the labels that will be rendered.
      if (myLabelProvider != null) {
        JLabel label = new JLabel(myLabelProvider.apply(data.value));
        label.setFont(AdtUiUtils.DEFAULT_FONT);
        label.setForeground(myColor);
        Dimension size = label.getPreferredSize();
        label.setBounds(0, 0, size.width, size.height);
        myLabelCache.add(label);
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
        clipRect.setRect(scaledXStart, 0, scaledXDuration, dim.getHeight());

        // Clear the region by repainting the background
        g2d.setColor(host.getBackground());
        g2d.fill(clipRect);

        // Set clip region and redraw the lines in grayscale.
        g2d.setClip(clipRect);
        LineChart.drawLines(g2d, transformedPaths, configs, true);
        g2d.setClip(originalClip);
      }
    }

    assert myPathCache.size() == myCachedYPositions.size();
    int xOffset = (int)(DISPLAY_OFFSET_PERCENTAGE * dim.getWidth());
    int yOffset = (int)(DISPLAY_OFFSET_PERCENTAGE * dim.getHeight());
    Line2D eventLine = new Line2D.Float();
    for (int i = 0; i < myPathCache.size(); i++) {
      Rectangle2D.Float rect = myPathCache.get(i);
      double scaledXStart = rect.x * dim.getWidth();
      double scaledXDuration = rect.width * dim.getWidth();
      g2d.translate(scaledXStart, 0);

      // Draw the start/end lines if stroke has been set.
      if (myStroke != null) {
        g2d.setColor(myColor);
        g2d.setStroke(myStroke);
        eventLine.setLine(0, 0, 0, dim.getHeight());
        g2d.draw(eventLine);
        eventLine.setLine(scaledXDuration, 0, scaledXDuration, dim.getHeight());
        g2d.draw(eventLine);
      }

      // TODO if we want these to show above components that are layered over the LineChart (e.g. SelectionCompoent),
      // these should be extracted into a separate draw call and be handled separately.
      double scaledYStart = myCachedYPositions.get(i) * dim.getHeight() - yOffset;
      g2d.translate(0, scaledYStart);
      if (myIcon != null) {
        myIcon.paintIcon(host, g2d, xOffset, -myIcon.getIconHeight());
        double xShift = myIcon.getIconWidth() + xOffset;
        g2d.translate(xShift, 0);
        scaledXStart += xShift;  // keep track of the amount of shift to revert the translate at the end.
      }

      if (myLabelProvider != null) {
        JLabel label = myLabelCache.get(i);
        double yShift = label.getPreferredSize().getHeight();
        g2d.translate(xOffset, -yShift);
        label.paint(g2d);
        scaledYStart -= yShift;
        scaledXStart += xOffset;
      }

      g2d.translate(-scaledXStart, -scaledYStart);
    }
  }

  public static class Builder<E extends DurationData> {
    // Required
    @NotNull private final RangedSeries<E> mySeries;
    @NotNull private final Color myColor;

    // Optional
    // TODO add config to allow changing the colors of line series underneath the DurationData.
    private boolean myIsBlocking = false;
    @Nullable private Icon myIcon = null;
    @Nullable private Stroke myStroke = null;
    @Nullable private RangedContinuousSeries myAttachedLineSeries = null;
    @Nullable private Function<E, String> myTooltipProvider = null;
    @Nullable private Function<E, String> myLabelProvider = null;
    @Nullable private Consumer<E> myClickHandler = null;

    public Builder(@NotNull RangedSeries<E> series, @NotNull Color color) {
      mySeries = series;
      myColor = color;
    }

    /**
     * If true, the renderer will gray out the line series underneath the DurationData.
     */
    public Builder setIsBlocking(boolean value) {
      myIsBlocking = value;
      return this;
    }

    /**
     * Sets the icon which will be drawn at the start point of each DurationData.
     */
    public Builder setIcon(@NotNull Icon icon) {
      myIcon = icon;
      return this;
    }

    /**
     * Sets the stroke of the lines that mark the start and end of the DurationData.
     */
    public Builder setStroke(@NotNull Stroke stroke) {
      myStroke = stroke;
      return this;
    }

    /**
     * If set the renderer will attach the content (e.g. icon/text) of the DurationData on top of the closest point of the corresponding
     * line series.
     */
    public Builder setAttachLineSeries(@NotNull RangedContinuousSeries series) {
      myAttachedLineSeries = series;
      return this;
    }

    /**
     * Sets the tooltip provider.
     */
    public Builder setTooltipProvider(@NotNull Function<E, String> provider) {
      myTooltipProvider = provider;
      return this;
    }

    /**
     * Sets the provider of the string which will be drawn at the start point of each DurationData.
     */
    public Builder setlabelProvider(@NotNull Function<E, String> provider) {
      myLabelProvider = provider;
      return this;
    }

    /**
     * If set, the handler will get triggered when the user clicked on the icon+label region of the DurationData.
     */
    public Builder setClickHander(@NotNull Consumer<E> handler) {
      myClickHandler = handler;
      return this;
    }

    @NotNull
    public DurationDataRenderer<E> build() {
      return new DurationDataRenderer(this);
    }
  }
}
