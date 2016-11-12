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

import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.common.datareducer.DataReducer;
import com.android.tools.adtui.common.datareducer.LineChartReducer;
import com.android.tools.adtui.model.*;
import com.intellij.ui.ColorUtil;
import com.intellij.util.containers.ImmutableList;
import gnu.trove.TDoubleArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.android.tools.adtui.model.DurationData.UNSPECIFIED_DURATION;

public class LineChart extends AnimatedComponent {

  /**
   * Transparency value to be applied to overlaying events.
   * TODO investigate whether events should be rendered as a separate component.
   */
  private static final float ALPHA_VALUE = 0.5f;

  private static final int EVENT_LABEL_PADDING_PX = 5;

  /**
   * Maps the series to their correspondent visual line configuration.
   * The keys insertion order is preserved.
   */
  @NotNull
  private final Map<RangedContinuousSeries, LineConfig> myLinesConfig = new LinkedHashMap<>();

  @NotNull
  private final Map<RangedSeries<DurationData>, EventConfig> myEventsConfig = new HashMap<>();

  @NotNull
  private final ArrayList<Path2D> myLinePaths;

  @NotNull
  private final ArrayList<LineConfig> myLinePathConfigs;

  /**
   * Stores the regions that each event series need to render.
   */
  @NotNull
  private final Map<RangedSeries<DurationData>, List<Rectangle2D.Float>> myEventsPathCache = new HashMap<>();

  /**
   * The color of the next line to be inserted, if not specified, is picked from {@code COLORS}
   * array of {@link LineConfig}. This field holds the color index.
   */
  private int mNextLineColorIndex;

  @NotNull
  private DataReducer myReducer;

  public LineChart() {
    myLinePaths = new ArrayList<>();
    myLinePathConfigs = new ArrayList<>();
    myReducer = new LineChartReducer();
  }

  @TestOnly
  public LineChart(@NotNull DataReducer reducer) {
    this();
    myReducer = reducer;
  }

  /**
   * Initialize a {@code LineChart} with a list of lines.
   */
  public LineChart(@NotNull List<RangedContinuousSeries> data) {
    this();
    addLines(data);
  }

  /**
   * Add a line to the line chart.
   * Note: The order of adding lines is important for stacked lines,
   *       i.e a stacked line is stacked on top of previous added stacked line.
   * @param series data of the line to be inserted
   * @param config configuration of the line to be inserted
   */
  public void addLine(@NotNull RangedContinuousSeries series, @NotNull LineConfig config) {
    myLinesConfig.put(series, config);
  }

  /**
   * Add a line to the line chart with default configuration.
   *
   * @param series series data of the line to be inserted
   */
  public void addLine(@NotNull RangedContinuousSeries series) {
    addLine(series, new LineConfig(LineConfig.COLORS[mNextLineColorIndex++]));
    mNextLineColorIndex %= LineConfig.COLORS.length;
  }

  /**
   * Add multiple lines with default configuration.
   */
  public void addLines(@NotNull List<RangedContinuousSeries> data) {
    data.forEach(this::addLine);
  }

  public void addEvent(@NotNull RangedSeries<DurationData> series, @NotNull EventConfig config) {
    myEventsConfig.put(series, config);
  }

  @NotNull
  public LineConfig getLineConfig(RangedContinuousSeries rangedContinuousSeries) {
    return myLinesConfig.get(rangedContinuousSeries);
  }

  /**
   * Removes all existing lines in the line chart.
   */
  public void clearConfigs() {
    myLinesConfig.clear();
    myEventsConfig.clear();
  }

  @NotNull
  public List<RangedContinuousSeries> getRangedContinuousSeries() {
    return new ArrayList<>(myLinesConfig.keySet());
  }

  @Override
  protected void updateData() {
    Map<Range, Double> max = new HashMap<>();
    // TODO Handle stacked configs
    for (RangedContinuousSeries ranged : myLinesConfig.keySet()) {
      Range range = ranged.getYRange();
      double yMax = Double.MIN_VALUE;

      ImmutableList<SeriesData<Long>> seriesList = ranged.getSeries();
      // TODO Think about if SeriesDataStore should store a Max/min value for each datatype.
      // Leaving this here for now to keep the DataStore API set to a minimum.
      for(int i = 0; i < seriesList.size(); i++) {
        double value = seriesList.get(i).value;
        if (yMax < value) {
          yMax = value;
        }
      }

      Double m = max.get(range);
      max.put(range, m == null ? yMax: Math.max(yMax, m));
    }

    for (Map.Entry<Range, Double> entry : max.entrySet()) {
      Range range = entry.getKey();
      // Prevent the LineChart to update the range below its initial max. We are only check against the initial max here as the
      // AxisComponent can interact with the range and clamp to a higher max value (See AxisComponent.setClampToMajorTicks(boolean)).
      // In each pass, the LineChart needs to reset the max target according to the data, so the AxisComponent can apply the clamping logic
      // using the current data max instead of the clamped max from the previous pass.
      if (range.getInitialMax() < entry.getValue()) {
        // TODO revisit how to animate.
        range.setMax(entry.getValue());
      }
    }
  }

  @Override
  public void postAnimate() {
    long duration = System.nanoTime();
    int p = 0;

    // Store the Y coordinates of the last stacked series to use them to increment the Y values
    // of the current stacked series.
    TDoubleArrayList lastStackedSeriesY = null;

    Deque<Path2D> orderedPaths = new ArrayDeque<>(myLinesConfig.size());
    Deque<LineConfig> orderedConfigs = new ArrayDeque<>(myLinesConfig.size());

    for (Map.Entry<RangedContinuousSeries, LineConfig> lineConfig : myLinesConfig.entrySet()) {
      final RangedContinuousSeries ranged = lineConfig.getKey();
      final LineConfig config = lineConfig.getValue();
      // Stores the y coordinates of the current series in case it's used as a stacked series
      final TDoubleArrayList currentSeriesY = new TDoubleArrayList();

      Path2D path = new Path2D.Float();

      double xMin = ranged.getXRange().getMin();
      double xMax = ranged.getXRange().getMax();
      double yMin = ranged.getYRange().getMin();
      double yMax = ranged.getYRange().getMax();

      // X coordinate of the first point
      double firstXd = 0f;

      List<SeriesData<Long>> seriesList = ranged.getSeries();
      for (int i = 0; i < seriesList.size(); i++) {
        // TODO: refactor to allow different types (e.g. double)
        SeriesData<Long> seriesData = seriesList.get(i);
        long currX = seriesData.x;
        long currY = seriesData.value;
        double xd = (currX - xMin) / (xMax - xMin);
        double yd = (currY - yMin) / (yMax - yMin);

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
        float adjustedYd = 1 - (float) yd;

        if (i == 0) {
          path.moveTo(xd, adjustedYd);
          firstXd = xd;
        } else {
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
      } else {
        orderedPaths.addLast(path);
        orderedConfigs.addLast(config);
      }

      addDebugInfo("Range[%d] Max: %.2f", p, xMax);
      p++;
    }

    myLinePaths.clear();
    myLinePaths.addAll(orderedPaths);

    myLinePathConfigs.clear();
    myLinePathConfigs.addAll(orderedConfigs);

    // First remove any path caches that do not have the corresponding RangedSeries anymore.
    myEventsPathCache.keySet().removeIf(e -> !myEventsConfig.containsKey(e));

    // Generate the rectangle regions for the events.
    // Note that we try to reuse any rectangles already created inside the myEventsPathCache as much as possible
    // So we only create new one when we run out and remove any unused ones at the end.
    for (RangedSeries<DurationData> ranged : myEventsConfig.keySet()) {
      List<Rectangle2D.Float> rectangleCache = myEventsPathCache.get(ranged);
      if (rectangleCache == null) {
        // Generate a new cache for the RangedSeries if one does not exist already. (e.g. newly added series)
        rectangleCache = new ArrayList<>();
        myEventsPathCache.put(ranged, rectangleCache);
      }

      double xMin = ranged.getXRange().getMin();
      double xMax = ranged.getXRange().getMax();
      ImmutableList<SeriesData<DurationData>> seriesList = ranged.getSeries();
      int i = 0;
      Rectangle2D.Float rect;
      for (; i < seriesList.size(); i++) {
        if (i == rectangleCache.size()) {
          rect = new Rectangle2D.Float();
          rectangleCache.add(rect);
        }
        else {
          rect = rectangleCache.get(i);
        }

        SeriesData<DurationData> seriesData = seriesList.get(i);
        double xStart = (seriesData.x - xMin) / (xMax - xMin);
        double xDuration = seriesData.value.getDuration() == UNSPECIFIED_DURATION ?
                           (xMax - seriesData.x) / (xMax - xMin) : seriesData.value.getDuration() / (xMax - xMin);

        rect.setRect(xStart, 0, xDuration, 1);
      }
      // Clear any outstanding rectangles in the list.
      // At the end, size of the rectangleCache should equal the number of samples from the seriesList above.
      rectangleCache.subList(i, rectangleCache.size()).clear();
    }

    addDebugInfo("postAnimate time: %d ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - duration));
  }

  @Override
  protected void draw(Graphics2D g2d) {
    if (myLinePaths.size() != myLinesConfig.size() || myEventsConfig.size() != myEventsPathCache.size()) {
      // Early return if the cached paths have not been sync'd with the configs.
      // e.g. updateData/postAnimate has not been invoked before this draw call.
      return;
    }

    Dimension dim = getSize();
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    AffineTransform scale = AffineTransform.getScaleInstance(dim.getWidth(), dim.getHeight());

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

    // 2nd pass - if there are any blocking events, clear the regions where those events take place
    //            and render the lines in those regions in grayscale.
    //            TODO support multiple blocking events by intersection their regions.
    Rectangle2D clipRect = new Rectangle2D.Float();
    for (RangedSeries<DurationData> eventSeries : myEventsPathCache.keySet()) {
      EventConfig config = myEventsConfig.get(eventSeries);
      if (config.isBlocking()) {
        for (Rectangle2D.Float rect : myEventsPathCache.get(eventSeries)) {
          double scaledXStart = rect.x * dim.width;
          double scaledXDuration = rect.width * dim.width;
          clipRect.setRect(scaledXStart, 0, scaledXDuration, dim.height);

          // Clear the region by repainting the background
          g2d.setColor(getBackground());
          g2d.fill(clipRect);

          // Set clip region and redraw the lines in grayscale.
          g2d.setClip(clipRect);
          drawLines(g2d, transformedPaths, myLinePathConfigs, true);
          g2d.setClip(null);
        }
      }
    }

    // 3rd pass - draw the event start/end lines and their labels
    //            TODO handle overlapping events
    Line2D eventLine = new Line2D.Float();
    for (Map.Entry<RangedSeries<DurationData>, List<Rectangle2D.Float>> entry : myEventsPathCache.entrySet()) {
      RangedSeries<DurationData> eventSeries = entry.getKey();
      List<Rectangle2D.Float> rectList = entry.getValue();

      EventConfig config = myEventsConfig.get(eventSeries);

      for (Rectangle2D.Float rect : rectList) {
        double scaledXStart = rect.x * dim.width;
        double scaledXDuration = rect.width * dim.width;

        g2d.setStroke(config.getStroke());
        // Draw the start/end lines, represented by the rectangles created during postAnimate.
        Shape scaledRect = scale.createTransformedShape(rect);
        if (config.isFilled()) {
          g2d.setColor(ColorUtil.withAlpha(config.getColor(), ALPHA_VALUE));
          g2d.fill(scaledRect);
        }
        else {
          g2d.setColor(config.getColor());
          eventLine.setLine(scaledXStart, 0, scaledXStart, dim.height);
          g2d.draw(eventLine);
          eventLine.setLine(scaledXStart + scaledXDuration, 0, scaledXStart + scaledXDuration, dim.height);
          g2d.draw(eventLine);
        }

        // Draw the label.
        g2d.translate(scaledXStart + EVENT_LABEL_PADDING_PX, EVENT_LABEL_PADDING_PX);
        g2d.clipRect(0, 0, (int)scaledXDuration - EVENT_LABEL_PADDING_PX, dim.height);
        config.getLabel().paint(g2d);
        g2d.setClip(null);
        g2d.translate(-(scaledXStart + EVENT_LABEL_PADDING_PX), -EVENT_LABEL_PADDING_PX);
      }
    }
  }

  private void drawLines(Graphics2D g2d, List<Path2D> transformedPaths, List<LineConfig> configs, boolean grayScale) {
    assert transformedPaths.size() == configs.size();

    for (int i = 0; i < transformedPaths.size(); ++i) {
      Path2D path = transformedPaths.get(i);
      LineConfig config = configs.get(i);
      Color lineColor = config.getColor();

      if (grayScale) {
        int gray = (lineColor.getBlue() + lineColor.getRed() + lineColor.getGreen()) / 3;
        g2d.setColor(new Color(gray, gray, gray));
      } else {
        g2d.setColor(lineColor);
      }
      g2d.setStroke(config.getStroke());

      if (config.isFilled()) {
        g2d.fill(path);
      } else {
        g2d.draw(path);
      }
    }
  }
}

