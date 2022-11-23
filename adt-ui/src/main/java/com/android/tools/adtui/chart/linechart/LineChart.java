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

import static java.awt.BasicStroke.CAP_SQUARE;
import static java.awt.BasicStroke.JOIN_MITER;

import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.model.LineChartModel;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.SeriesData;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.util.containers.ContainerUtil;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleSupplier;
import org.jetbrains.annotations.NotNull;

public class LineChart extends AnimatedComponent {

  static final float EPSILON = 1e-4f;

  // Helper structure to cache dash-related info used in a previous frame, so we can compensate for where the dash starts in the next frame.
  private static class DashInfo {
    double myPreviousFirstX;
    double myPreviousXMin;
    double myPreviousXLength;
    double myPreviousYLength;
    Path2D myPreviousDashPath;
  }

  /**
   * If data series are shown as bucket bar and given LineConfig#myDataBucketInterval,
   * how much percent the bar width and how much percent the gap, i.e. (bar + gap) = interval.
   */
  private static final double BUCKET_BAR_PERCENTAGE = 0.7;

  public static final DoubleSupplier ALWAYS_0 = () -> 0;
  public static final DoubleSupplier ALWAYS_1 = () -> 1;

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
  private final ArrayList<RangedContinuousSeries> myLinePathSeries;

  @NotNull
  private final List<LineChartCustomRenderer> myCustomRenderers = new ArrayList<>();

  @NotNull
  private Color myMaxLineColor = Color.BLACK;

  private int myMaxLineMargin;

  private boolean myShowMaxLine;

  /**
   * The X coordinate translation offset for line path transformation.
   */
  private int myXOffset = 0;

  /**
   * The Y coordinate translation offset for line path transformation.
   */
  private int myYOffset = 0;

  private int myTopPadding = 0;

  /**
   * The color of the next line to be inserted, if not specified, is picked from {@code COLORS}
   * array of {@link LineConfig}. This field holds the color index.
   */
  private int mNextLineColorIndex;

  private boolean myRedraw;

  @NotNull private DoubleSupplier myFillEndSupplier = ALWAYS_0;

  @NotNull
  private final LineChartReducer myReducer;

  // Debug draw counters. TODO: Move to a framework object
  private long myRedraws;
  private long myDraws;
  private long myLastCount;
  private long myLastDraws;
  private long myLastRedraws;

  private Map<LineConfig, DashInfo> myDashInfoCache = new HashMap<>();

  @VisibleForTesting
  public LineChart(@NotNull LineChartModel model, @NotNull LineChartReducer reducer) {
    myLinePaths = new ArrayList<>();
    myLinePathSeries = new ArrayList<>();
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

  private void modelChanged() {
    myRedraw = true;
    opaqueRepaint();
  }

  private void redraw(@NotNull Dimension dim) {
    long duration = System.nanoTime();

    // Store the last stacked series to use them to increment the Y values
    // of the current stacked series.
    List<SeriesData<Long>> lastStackedSeries = null;

    Deque<Path2D> orderedPaths = new ArrayDeque<>(myLinesConfig.size());
    Deque<RangedContinuousSeries> orderedSeries = new ArrayDeque<>(myLinesConfig.size());

    for (RangedContinuousSeries ranged : myModel.getSeries()) {
      if (ranged.getXRange().isEmpty() || ranged.getXRange().isPoint()
          || ranged.getYRange().isEmpty() || ranged.getYRange().isPoint()) {
        continue;
      }
      final LineConfig config = getLineConfig(ranged);

      List<SeriesData<Long>> seriesList = ranged.getSeries();
      if (config.isStacked()) {
        if (lastStackedSeries == null) {
          // Create a new list of SeriesData to prevent modifying the backing data series, which could be cached.
          lastStackedSeries = ContainerUtil.map(seriesList, data -> new SeriesData<>(data.x, data.value));
        }
        else {
          // If the current series is stacked, increment its value by the value of the last stacked
          // series. As the series are constantly populated, the current series might have more
          // points than the last stacked series (meaning that the last one was populated in a
          // prior iteration). In this case, ignore the new points (i.e. we take only the intersection
          // across all series).
          for (int i = 0; i < seriesList.size() && i < lastStackedSeries.size(); ++i) {
            // An assumption is made here that the x values across series are aligned.
            lastStackedSeries.get(i).value += seriesList.get(i).value;
          }
          seriesList = lastStackedSeries;
        }
      }

      Path2D path = new Path2D.Float();
      double xMin = ranged.getXRange().getMin();
      double xLength = ranged.getXRange().getLength();
      double yMin = ranged.getYRange().getMin();
      double yLength = ranged.getYRange().getLength();

      // X coordinate of the first point
      double firstXd = 0f;
      // Actual value of first point
      double firstX = 0;
      seriesList = myReducer.reduceData(seriesList, config);
      double xBucketInterval = config.getDataBucketInterval() / xLength;
      double xBucketBarWidth = xBucketInterval * BUCKET_BAR_PERCENTAGE;
      // If we are a stepped chart or bar chart, we don't need to worry about start and end points' Y value.
      boolean optimizeYZooming = !config.isStepped() && xBucketInterval == 0;
      for (int i = 0; i < seriesList.size(); i++) {
        SeriesData<Long> data = seriesList.get(i);
        SeriesData<Long> dataNext = seriesList.get(i + 1 == seriesList.size() ? i : i + 1);
        SeriesData<Long> dataPrev = seriesList.get(i - 1 < 0 ? i : i - 1);
        // TODO: refactor to allow different types (e.g. double)
        double xd = (data.x - xMin) / xLength;
        // Swing's (0, 0) coordinate is in top-left. As we use bottom-left (0, 0), we need to adjust the y coordinate.
        double yd = 1 - (data.value - yMin) / yLength;

        // This change significantly speeds up drawing when zoomed into the chart. Without this change a line could extend
        // a few thousand pixels off the screen in both directions. The fill/draw function would then spend a lot of time
        // computing a line fill for pixels never to be rendered.
        // Truncate points that are off screen. Ones that cross the border get pushed to the border, and the
        // height gets scaled accordingly.
        // For example, two out of bounds points would get snapped into place.
        //                |                    |
        //                |                    * <-- *
        //          * --> *                    |
        //                |                    |
        // X Axis: -------|--------------------|------
        //                0                    1

        double originalXd = xd;
        if (xd < 0) {
          double xdNext = (dataNext.x - xMin) / xLength;
          // If our next point is also offscreen then ignore this point and continue.
          if (xdNext < 0) {
            if (data == dataNext) {
              // The last point is still off screen, we should add a point at (0, y) to avoid drawing nothing.
              //     |   |
              // *-->*----
              //     |   |
              //     0   1
              path.moveTo(0f, yd);
            }
            continue;
          }

          //Get the Y offset of our next point.
          double ydNext = 1 - (dataNext.value - yMin) / yLength;

          // If we are a dash line we get the closest normalized point to are graph otherwise we just set our point to 0.
          double newPosition = 0;
          if (config.isDash()) {
            newPosition = xd % 1.0f;
          }
          if (optimizeYZooming) {
            // If we are not stepped we need to adjust the starting Y position to be a linear interpolation of our new X point.
            double ratio = (newPosition - xd) / (xdNext - xd);
            yd = (1 - ratio) * yd + (ratio * ydNext);
          }
          // Set our new X position and carry on.
          xd = newPosition;
        }
        else if (xd > 1) {
          double xdPrev = (dataPrev.x - xMin) / xLength;
          if (xdPrev > 1) {
            break;
          }
          if (optimizeYZooming) {
            double ratio = (1 - xdPrev) / (xd - xdPrev);
            double ydPrev = 1 - (dataPrev.value - yMin) / yLength;
            yd = (1 - ratio) * ydPrev + (ratio * yd);
          }
          xd = 1;
        }

        if (path.getCurrentPoint() == null) {
          firstXd = xd;
          firstX = data.x;
          // If for bucket data, because the previous ending x value is next data point's starting
          // x value, i.e. (xd + interval, 1), move the path start point to (xd, 1).
          // Otherwise, move the path start point to (xd, yd).
          path.moveTo(xd, xBucketInterval != 0 ? 1 : yd);
        }
        else if (xBucketInterval == 0) {
          // If the chart is stepped, a horizontal line should be drawn from the current
          // point (e.g. (x0, y0)) to the destination's X value (e.g. (x1, y0)) before
          // drawing a line to the destination point itself (e.g. (x1, y1)).
          if (config.isStepped()) {
            float y = (float)path.getCurrentPoint().getY();
            path.lineTo(xd, y);
          }
          path.lineTo(xd, yd);
        }

        if (xBucketInterval != 0) {
          // Move line to (xd, 1) first because data points may not be equal time buckets, for example, data points are (1000, 1),
          // (1200, 2), (2000, 3).
          double barX = Math.min(1, originalXd + xBucketBarWidth);
          if (barX - xd > EPSILON) {
            path.lineTo(xd, 1);
            path.lineTo(xd, yd);
            path.lineTo(barX, yd);
            path.lineTo(barX, 1);
          }
        }
      }

      if (path.getCurrentPoint() != null) {
        // Extends the last point on the path to the end
        path.lineTo(Math.max(path.getCurrentPoint().getX(), myFillEndSupplier.getAsDouble()), path.getCurrentPoint().getY());
      }

      if (config.isFilled() && path.getCurrentPoint() != null) {
        // If the chart is filled, draw a line from the last point to X
        // axis and another one from this new point to the first destination point.
        path.lineTo(path.getCurrentPoint().getX(), 1f);
        path.lineTo(firstXd, 1f);
      }

      if (config.isFilled()) {
        // Draw the filled lines first, otherwise other lines won't be visible.
        // Also, to draw stacked and filled lines correctly, they need to be drawn in reverse order to their adding order.
        orderedPaths.addFirst(path);
        orderedSeries.addFirst(ranged);
      }
      else {
        orderedPaths.addLast(path);
        orderedSeries.addLast(ranged);
      }

      if (config.isDash() && config.isAdjustDash()) {
        DashInfo dashInfo;
        if (!myDashInfoCache.containsKey(config)) {
          dashInfo = new DashInfo();
          myDashInfoCache.put(config, dashInfo);
          // No previous dataInfo so don't bother trying to adjust dash phase.
        }
        else {
          dashInfo = myDashInfoCache.get(config);
          computeAdjustedDashPhase(dashInfo, config, path, dim, firstX, xMin, xLength, yLength);
        }
        dashInfo.myPreviousFirstX = firstX;
        dashInfo.myPreviousXMin = xMin;
        dashInfo.myPreviousXLength = xLength;
        dashInfo.myPreviousYLength = yLength;
        dashInfo.myPreviousDashPath = path;
      }
      else {
        myDashInfoCache.remove(config);
      }
    }

    myLinePaths.clear();
    myLinePaths.addAll(orderedPaths);

    myLinePathSeries.clear();
    myLinePathSeries.addAll(orderedSeries);

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
      redraw(dim);
      myRedraws++;
    }
    else {
      addDebugInfo("postAnimate time: 0 ms");
    }
    addDebugInfo("Draws in the last second %d", myLastDraws);
    addDebugInfo("Redraws in the last second %d", myLastRedraws);

    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    AffineTransform scale = new AffineTransform(dim.getWidth(), 0, 0, dim.getHeight() - myTopPadding, myXOffset, myYOffset + myTopPadding);

    if (myShowMaxLine) {
      g2d.setColor(myMaxLineColor);
      g2d.setStroke(new BasicStroke(1, CAP_SQUARE, JOIN_MITER, 10, new float[]{3.0f, 3.0f}, 0.0f));
      g2d.drawLine(myMaxLineMargin, 0, dim.width, 0);
    }

    // Cache the transformed line paths for reuse below.
    List<Path2D> transformedPaths = new ArrayList<>(myLinePaths.size());
    List<LineConfig> configs = new ArrayList<>(myLinePaths.size());

    for (int i = 0; i < myLinePaths.size(); ++i) {
      Path2D scaledPath = new Path2D.Float(myLinePaths.get(i), scale);
      LineConfig config = getLineConfig(myLinePathSeries.get(i));
      configs.add(config);
      scaledPath = myReducer.reducePath(scaledPath, config);
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
    for (int i = 0; i < transformedPaths.size(); ++i) {
      drawLine(g2d, transformedPaths.get(i), configs.get(i));
    }

    // 2nd pass - call each custom renderer instances to redraw any regions/lines as needed.
    myCustomRenderers.forEach(renderer -> renderer.renderLines(this, g2d, transformedPaths, myLinePathSeries));

    addDebugInfo("Draw time: %.2fms", (System.nanoTime() - drawStartTime) / 1e6);
  }

  public static void drawLine(@NotNull Graphics2D g2d,
                              @NotNull Path2D path,
                              @NotNull LineConfig config) {
    g2d.setColor(config.getColor());
    g2d.setStroke(config.isDash() && config.isAdjustDash() ? config.getAdjustedStroke() : config.getStroke());
    if (config.isStepped()) {
      // In stepped mode, everything is at right angles, and turning off anti-aliasing
      // in this case makes everything look sharper in a good way.
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
    }
    else {
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    if (config.isFilled()) {
      g2d.fill(path);
    }
    else {
      g2d.draw(path);
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

  /**
   * Computes the dash phase that a {@link LineConfig} should use to start its next path by comparing it to the last path that was drawn.
   */
  private void computeAdjustedDashPhase(@NotNull DashInfo dashInfo,
                                        @NotNull LineConfig config,
                                        @NotNull Path2D path,
                                        @NotNull Dimension dim,
                                        double firstX,
                                        double xMin,
                                        double xLength,
                                        double yLength) {
    // Only tries to adjust the dash phase iff:
    // 1. The lengths in both x and y directions have not changed. Otherwise the path would have been scaled differently anyway and there
    //    isn't a point to try to compensate
    // 2. The x min values between the two frames have not changed more than half the length. The constraint is somewhat arbitrary but
    //    it gives us enough room to locate the new starting X value on the old path, and vice versa.
    if (Math.abs(dashInfo.myPreviousXLength - xLength) > EPSILON ||
        Math.abs(dashInfo.myPreviousYLength - yLength) > EPSILON ||
        Math.abs(dashInfo.myPreviousXMin - xMin) > (xLength / 2f)) {
      return;
    }

    Path2D pathToUse = null;
    double firstXd = 0;
    boolean newPathIsAhead = false;
    if (xMin - dashInfo.myPreviousXMin > EPSILON) {
      // If the new xMin is ahead, then calculate the pixel length between myPreviousX and xMin on the OLD path
      pathToUse = dashInfo.myPreviousDashPath;
      firstXd = (firstX - dashInfo.myPreviousXMin) / xLength;
      newPathIsAhead = true;
    }
    else if (dashInfo.myPreviousXMin - xMin > EPSILON) {
      // If the new xMin is trailing, then calculate the pixel length between xMin and myPreviousX on the NEW path
      pathToUse = path;
      firstXd = (dashInfo.myPreviousFirstX - xMin) / xLength;
    }

    if (pathToUse == null || pathToUse.getCurrentPoint() == null) {
      return;
    }

    // Dash pattern length in pixel
    float dashPatternLength = config.getDashLength();
    // Length of path between myPreviousXMin and xMin
    double deltaPathLength = 0;

    // Starting from the beginning of the path. Accumulate the path length until we've reached firstXd - the path length tells us
    // how much we need to adjust the dash phase by.
    PathIterator iterator = pathToUse.getPathIterator(null);
    float[] coords = new float[6];
    int segType = iterator.currentSegment(coords);
    assert segType == PathIterator.SEG_MOVETO;
    // Special case first point - if the x coordinate for the first point has not changed, use the same dash phase.
    if (Math.abs(coords[0] - firstXd) < EPSILON) {
      return;
    }

    double prevX = coords[0];
    double prevY = coords[1];
    iterator.next();
    while (!iterator.isDone()) {
      segType = iterator.currentSegment(coords);
      assert segType == PathIterator.SEG_LINETO;
      if (coords[0] - firstXd >= EPSILON) {
        // Special case: firstXd could have been reduced away from the series data list if it holds the same y value.
        // Here we make sure the length from firstXd - prevX is accounted for.
        if (firstXd - prevX >= EPSILON) {
          deltaPathLength += (firstXd - prevX) * dim.width;
        }
        break;
      }

      if (config.isStepped()) {
        deltaPathLength += Math.abs(coords[0] - prevX) * dim.width + Math.abs(coords[1] - prevY) * dim.height;
      }
      else {
        deltaPathLength += Math.hypot((coords[0] - prevX) * dim.width, (coords[1] - prevY) * dim.height);
      }
      prevX = coords[0];
      prevY = coords[1];

      iterator.next();
    }

    // Update dash phase.
    double dashPhase = config.getAdjustedDashPhase();
    if (newPathIsAhead) {
      dashPhase = (dashPhase + deltaPathLength) % dashPatternLength;
    }
    else {
      dashPhase = (dashPhase - deltaPathLength) % dashPatternLength;
      if (dashPhase < 0) {
        dashPhase += dashPatternLength;
      }
    }
    config.setAdjustedDashPhase(dashPhase);
  }

  /**
   * Sets the translation offsets of lines coordinates transformation.
   */
  public void setRenderOffset(int xOffset, int yOffset) {
    myXOffset = xOffset;
    myYOffset = yOffset;
  }

  public void setTopPadding(int padding) {
    myTopPadding = padding;
  }

  public void setFillEndGap(boolean fillEndGap) {
    setFillEndSupplier(fillEndGap ? ALWAYS_1 : ALWAYS_0);
  }

  /**
   * @param fillEndSupplier Produces the right edge, as a percentage between 0 and 1 to extend to
   *                        (if the edge passes the last data-point).
   *                        This means that ALWAYS_0 never fills, and ALWAYS_1 always fills.
   */
  public void setFillEndSupplier(@NotNull DoubleSupplier fillEndSupplier) {
    myFillEndSupplier = fillEndSupplier;
  }
}
