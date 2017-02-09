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
import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.LegendConfig;
import com.android.tools.adtui.model.LineChartModel;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.SeriesData;
import gnu.trove.TDoubleArrayList;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.awt.BasicStroke.CAP_SQUARE;
import static java.awt.BasicStroke.JOIN_MITER;

public class LineChart extends AnimatedComponent {

  @NotNull final LineChartModel myModel;

  /**
   * Maps the series to their correspondent visual line configuration.
   * The keys insertion order is preserved.
   */
  @NotNull
  private final Map<RangedContinuousSeries, LineConfig> myLinesConfig = new LinkedHashMap<>();

  @NotNull
  private final ArrayList<Path2D> myLinePaths;

  @NotNull
  private final ArrayList<LineConfig> myLinePathConfigs;

  @NotNull
  private final List<LineChartCustomRenderer> myCustomRenderers = new ArrayList<>();

  @NotNull
  private Color myMaxLineColor = Color.BLACK;

  private int myMaxLineMargin;

  private boolean myShowMaxLine;

  /**
   * The color of the next line to be inserted, if not specified, is picked from {@code COLORS}
   * array of {@link LineConfig}. This field holds the color index.
   */
  private int mNextLineColorIndex;

  private boolean myRedraw;

  @NotNull
  private final LineChartReducer myReducer;

  // Debug draw counters. TODO: Move to a framework object
  private long myRedraws;
  private long myDraws;
  private long myLastCount;
  private long myLastDraws;
  private long myLastRedraws;

  @VisibleForTesting
  public LineChart(@NotNull LineChartModel model, @NotNull LineChartReducer reducer) {
    myLinePaths = new ArrayList<>();
    myLinePathConfigs = new ArrayList<>();
    myReducer = reducer;
    myModel = model;
    myRedraw = true;
    myModel.addDependency(myAspectObserver)
      .onChange(LineChartModel.Aspect.LINE_CHART, this::modelChanged);
  }

  public LineChart(@NotNull LineChartModel model) {
    this(model, new DefaultLineChartReducer());
  }

  /**
   * Initialize a {@code LineChart} with a list of lines.
   */
  public LineChart(@NotNull List<RangedContinuousSeries> data) {
    this(new LineChartModel());
    myModel.addAll(data);
  }

  /**
   * Configures a line in the chart.
   *
   * @param series the ranged series to configure.
   * @param config configuration of the line to be inserted.
   */
  public void configure(@NotNull RangedContinuousSeries series, @NotNull LineConfig config) {
    myLinesConfig.put(series, config);
  }

  public void addCustomRenderer(@NotNull LineChartCustomRenderer renderer) {
    myCustomRenderers.add(renderer);
  }

  @NotNull
  public LineConfig getLineConfig(RangedContinuousSeries rangedContinuousSeries) {
    LineConfig config = myLinesConfig.get(rangedContinuousSeries);
    if (config == null) {
      config = new LineConfig(LineConfig.getColor(mNextLineColorIndex++));
      configure(rangedContinuousSeries, config);
    }
    return config;
  }

  /**
   * Removes all existing lines in the line chart.
   */
  public void clearConfigs() {
    myLinesConfig.clear();
    myCustomRenderers.clear();
  }

  /**
   * Creates a {@link LegendConfig} instance. The configuration will be derived based on the {@link LineConfig} associated
   * with the input series used in this {@link LineChart} instance. If the series is not part of the LineChart, defaults will be chosen.
   *
   * @param series    the RangedContinuousSeries which the legend will query data from.
   * @param formatter the BaseAxisFormatter which will be used to format the data coming from the series.
   *                  TODO revisit - this can be potentially moved inside RangedContinuousSeries.
   * @range range     the range object which the legend will use to gather data. Note that this does not have to be the same as the
   * the range inside RangedContinuousSeries (e.g. if the legend needs to show the most recent data, or some data at
   * a particular point in time)
   */

  private void modelChanged() {
    myRedraw = true;
    opaqueRepaint();
  }

  private void redraw() {
    long duration = System.nanoTime();
    int p = 0;

    // Store the Y coordinates of the last stacked series to use them to increment the Y values
    // of the current stacked series.
    TDoubleArrayList lastStackedSeriesY = null;

    Deque<Path2D> orderedPaths = new ArrayDeque<>(myLinesConfig.size());
    Deque<LineConfig> orderedConfigs = new ArrayDeque<>(myLinesConfig.size());

    for (RangedContinuousSeries ranged : myModel.getSeries()) {
      if (ranged.getXRange().isEmpty() || ranged.getXRange().isPoint()
          || ranged.getYRange().isEmpty() || ranged.getYRange().isPoint()) {
        continue;
      }

      final LineConfig config = getLineConfig(ranged);
      // Stores the y coordinates of the current series in case it's used as a stacked series
      final TDoubleArrayList currentSeriesY = new TDoubleArrayList();

      Path2D path = new Path2D.Float();
      double xMin = ranged.getXRange().getMin();
      double xLength = ranged.getXRange().getLength();
      double yMin = ranged.getYRange().getMin();
      double yLength = ranged.getYRange().getLength();

      // X coordinate of the first point
      double firstXd = 0f;

      List<SeriesData<Long>> seriesList = ranged.getSeries();
      for (int i = 0; i < seriesList.size(); i++) {
        // TODO: refactor to allow different types (e.g. double)
        SeriesData<Long> seriesData = seriesList.get(i);
        long currX = seriesData.x;
        long currY = seriesData.value;
        double xd = (currX - xMin) / xLength;
        double yd = (currY - yMin) / yLength;

        // If the current series is stacked, increment its yd by the yd of the last stacked
        // series if it's not null.
        // As the series are constantly populated, the current series might have one more
        // point than the last stacked series (meaning that the last one was populated in a
        // prior iteration). In this case, yd of the current series shouldn't change.
        if (config.isStacked() && lastStackedSeriesY != null &&
            i < lastStackedSeriesY.size()) {
          yd += lastStackedSeriesY.get(i);
        }
        currentSeriesY.add(yd);
        // Swing's (0, 0) coordinate is in top-left. As we use bottom-left (0, 0), we need to adjust the y coordinate.
        float adjustedYd = 1 - (float)yd;

        if (i == 0) {
          path.moveTo(xd, adjustedYd);
          firstXd = xd;
        }
        else {
          // If the chart is stepped, a horizontal line should be drawn from the current
          // point (e.g. (x0, y0)) to the destination's X value (e.g. (x1, y0)) before
          // drawing a line to the destination point itself (e.g. (x1, y1)).
          if (config.isStepped()) {
            float y = (float)path.getCurrentPoint().getY();
            path.lineTo(xd, y);
          }
          path.lineTo(xd, adjustedYd);
        }
      }

      if (config.isFilled() && path.getCurrentPoint() != null) {
        // If the chart is filled, but not stacked, draw a line from the last point to X
        // axis and another one from this new point to the first destination point.
        path.lineTo(path.getCurrentPoint().getX(), 1f);
        path.lineTo(firstXd, 1f);
      }

      if (config.isStacked()) {
        lastStackedSeriesY = currentSeriesY;
      }

      if (config.isFilled()) {
        // Draw the filled lines first, otherwise other lines won't be visible.
        // Also, to draw stacked and filled lines correctly, they need to be drawn in reverse order to their adding order.
        orderedPaths.addFirst(path);
        orderedConfigs.addFirst(config);
      }
      else {
        orderedPaths.addLast(path);
        orderedConfigs.addLast(config);
      }

      p++;
    }

    myLinePaths.clear();
    myLinePaths.addAll(orderedPaths);

    myLinePathConfigs.clear();
    myLinePathConfigs.addAll(orderedConfigs);

    addDebugInfo("postAnimate time: %d ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - duration));
  }

  @Override
  protected void draw(Graphics2D g2d, Dimension dim) {
    long now = System.nanoTime();
    long drawStartTime = now;

    if (now - myLastCount > 1e9) {
      myLastDraws = myDraws;
      myLastRedraws = myRedraws;
      myDraws = 0;
      myRedraws = 0;
      myLastCount = now;
    }
    myDraws++;
    if (myRedraw) {
      myRedraw = false;
      redraw();
      myRedraws++;
    }
    else {
      addDebugInfo("postAnimate time: 0 ms");
    }
    addDebugInfo("Draws in the last second %d", myLastDraws);
    addDebugInfo("Redraws in the last second %d", myLastRedraws);

    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    AffineTransform scale = AffineTransform.getScaleInstance(dim.getWidth(), dim.getHeight());

    if (myShowMaxLine) {
      g2d.setColor(myMaxLineColor);
      g2d.setStroke(new BasicStroke(1, CAP_SQUARE, JOIN_MITER, 10, new float[]{3.0f, 3.0f}, 0.0f));
      g2d.drawLine(myMaxLineMargin, 0, dim.width, 0);
    }

    // Cache the transformed line paths for reuse below.
    List<Path2D> transformedPaths = new ArrayList<>(myLinePaths.size());
    for (int i = 0; i < myLinePaths.size(); ++i) {
      Path2D scaledPath = new Path2D.Float(myLinePaths.get(i), scale);
      scaledPath = myReducer.reduce(scaledPath, myLinePathConfigs.get(i));
      transformedPaths.add(scaledPath);

      if (isDrawDebugInfo()) {
        int count = 0;
        PathIterator it = scaledPath.getPathIterator(null);
        while (!it.isDone()) {
          ++count;
          it.next();
        }
        addDebugInfo("# of points drawn: %d", count);
      }
    }

    // 1st pass - draw all the lines in the background.
    drawLines(g2d, transformedPaths, myLinePathConfigs, false);

    // 2nd pass - call each custom renderer instances to redraw any regions/lines as needed.
    myCustomRenderers.forEach(renderer -> renderer.renderLines(this, g2d, transformedPaths, myLinePathConfigs));

    addDebugInfo("Draw time: %.2fms", (System.nanoTime() - drawStartTime) / 1e6);
  }

  public static void drawLines(Graphics2D g2d, List<Path2D> transformedPaths, List<LineConfig> configs, boolean grayScale) {
    assert transformedPaths.size() == configs.size();

    for (int i = 0; i < transformedPaths.size(); ++i) {
      Path2D path = transformedPaths.get(i);
      LineConfig config = configs.get(i);
      Color lineColor = config.getColor();

      if (grayScale) {
        int gray = (lineColor.getBlue() + lineColor.getRed() + lineColor.getGreen()) / 3;
        g2d.setColor(new Color(gray, gray, gray));
      }
      else {
        g2d.setColor(lineColor);
      }
      g2d.setStroke(config.getStroke());

      if (config.isFilled()) {
        g2d.fill(path);
      }
      else {
        g2d.draw(path);
      }
    }
  }

  public void setShowMaxLine(boolean showMaxLine) {
    myShowMaxLine = showMaxLine;
  }

  public void setMaxLineColor(@NotNull Color maxLineColor) {
    myMaxLineColor = maxLineColor;
  }

  public void setMaxLineMargin(int maxLineMargin) {
    myMaxLineMargin = maxLineMargin;
  }
}

