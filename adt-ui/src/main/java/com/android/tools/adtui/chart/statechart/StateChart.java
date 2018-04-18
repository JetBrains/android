/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.adtui.chart.statechart;

import com.android.annotations.VisibleForTesting;
import com.android.tools.adtui.MouseAdapterComponent;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.StateChartModel;
import com.android.tools.adtui.model.Stopwatch;
import com.intellij.ui.ColorUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A chart component that renders series of state change events as rectangles.
 */
public final class StateChart<T> extends MouseAdapterComponent<T> {

  public enum RenderMode {
    BAR,  // Each state is rendered as a filled rectangle until the next state changed.
    TEXT  // Each state is marked with a vertical line and and corresponding state text/label at the beginning.
  }

  private static final int TEXT_PADDING = 3;

  private StateChartModel<T> myModel;

  /**
   * An object that maps between a type T, and a color to be used in the StateChart, all values of T should return a valid color.
   */
  @NotNull
  private final StateChartColorProvider<T> myColorProvider;

  private float myHeightGap;

  @NotNull
  private RenderMode myRenderMode;

  @NotNull
  private final StateChartConfig<T> myConfig;

  private boolean myNeedsTransform;

  @NotNull
  private final StateChartTextConverter<T> myTextConverter;

  private final List<Rectangle2D.Float> myRectangles = new ArrayList<>();
  private final List<T> myRectangleValues = new ArrayList<>();

  private float myRectHeight = 1.0f;

  /**
   * @param colors map of a state to corresponding color
   */
  @VisibleForTesting
  public StateChart(@NotNull StateChartModel<T> model, @NotNull Map<T, Color> colors) {
    this(model, new StateChartColorProvider<T>() {
      @Override
      @NotNull
      public Color getColor(boolean isMouseOver, @NotNull T value) {
        Color color = colors.get(value);
        return isMouseOver ? ColorUtil.brighter(color, 2) : color;
      }
    });
  }

  public StateChart(@NotNull StateChartModel<T> model, @NotNull StateChartColorProvider<T> colorMapping) {
    this(model, new StateChartConfig<>(new DefaultStateChartReducer<>()), colorMapping, (val) -> val.toString());
  }

  public StateChart(@NotNull StateChartModel<T> model,
                    @NotNull StateChartColorProvider<T> colorMapping,
                    StateChartTextConverter<T> textConverter) {
    this(model, new StateChartConfig<>(new DefaultStateChartReducer<>()), colorMapping, textConverter);
  }

  public StateChart(@NotNull StateChartModel<T> model,
                    @NotNull StateChartConfig<T> config,
                    @NotNull StateChartColorProvider<T> colorMapping) {
    this(model, config, colorMapping, (val) -> val.toString());
  }

  @VisibleForTesting
  public StateChart(@NotNull StateChartModel<T> model,
                    @NotNull StateChartConfig<T> config,
                    @NotNull StateChartColorProvider<T> colorMapping,
                    @NotNull StateChartTextConverter<T> textConverter) {
    myColorProvider = colorMapping;
    myRenderMode = RenderMode.BAR;
    myConfig = config;
    myNeedsTransform = true;
    myTextConverter = textConverter;
    setFont(AdtUiUtils.DEFAULT_FONT);
    setModel(model);
    setHeightGap(myConfig.getHeightGap());
  }

  public void setModel(@NotNull StateChartModel<T> model) {
    if (myModel != null) {
      myModel.removeDependencies(myAspectObserver);
    }
    myModel = model;
    myModel.addDependency(myAspectObserver).onChange(StateChartModel.Aspect.MODEL_CHANGED, this::modelChanged);
    modelChanged();
  }

  private void modelChanged() {
    myNeedsTransform = true;
    opaqueRepaint();
  }

  public void setRenderMode(@NotNull RenderMode mode) {
    myRenderMode = mode;
  }

  /**
   * Sets the gap between multiple data series.
   *
   * @param gap The gap value as a percentage {0...1} of the height given to each data series
   */
  public void setHeightGap(float gap) {
    myHeightGap = gap;
  }

  private void clearRectangles() {
    myRectangles.clear();
    myRectangleValues.clear();
  }

  /**
   * Creates a rectangle with the supplied dimensions. This function will normalize the x, and width values, then store
   * the rectangle off using its key value as a lookup.
   *
   * @param value     value used to associate with the created rectangle..
   * @param previousX value used to determine the x position and width of the rectangle. This value should be relative to the currentX param.
   * @param currentX  value used to determine the width of the rectangle. This value should be relative to the previousX param.
   * @param minX      minimum value of the range total range used to normalize the x position and width of the rectangle.
   * @param maxX      maximum value of the range total range used to normalize the x position and width of the rectangle.
   * @param rectY     rectangle height offset from max growth of rectangle. This value is expressed as a percentage from 0-1
   * @param vGap      height offset from bottom of rectangle. This value is expressed as percentage from 0-1
   */
  private void addRectangleDelta(@NotNull T value, double previousX, double currentX, double minX, double maxX, float rectY, double vGap) {
    // Because we start our activity line from the bottom and grow up we offset the height from the bottom of the component
    // instead of the top by subtracting our height from 1.
    Rectangle2D.Float rect = new Rectangle2D.Float(
      (float)((previousX - minX) / (maxX - minX)),
      rectY,
      (float)((currentX - previousX) / (maxX - minX)),
      (float)((1.0 - vGap) * myRectHeight));
    myRectangles.add(rect);
    myRectangleValues.add(value);
  }

  private void transform() {
    if (!myNeedsTransform) {
      return;
    }

    myNeedsTransform = false;

    List<RangedSeries<T>> series = myModel.getSeries();
    int seriesSize = series.size();
    if (seriesSize == 0) {
      return;
    }

    // TODO support adding series on the fly and interpolation.
    myRectHeight = 1.0f / seriesSize;
    float gap = myRectHeight * myHeightGap;

    clearRectangles();

    for (int seriesIndex = 0; seriesIndex < series.size(); seriesIndex++) {
      RangedSeries<T> data = series.get(seriesIndex);

      double min = data.getXRange().getMin();
      double max = data.getXRange().getMax();
      float startHeight = 1.0f - (myRectHeight * (seriesIndex + 1));

      List<SeriesData<T>> seriesDataList = data.getSeries();
      if (seriesDataList.isEmpty()) {
        continue;
      }

      // Construct rectangles.
      long previousX = seriesDataList.get(0).x;
      T previousValue = seriesDataList.get(0).value;
      for (int i = 1; i < seriesDataList.size(); i++) {
        SeriesData<T> seriesData = seriesDataList.get(i);
        long x = seriesData.x;
        T value = seriesData.value;

        if (value.equals(previousValue)) {
          // Ignore repeated values.
          continue;
        }

        assert previousValue != null;

        // Don't draw if this block doesn't intersect with [min..max]
        if (x >= min) {
          // Draw the previous block.
          addRectangleDelta(previousValue, Math.max(min, previousX), Math.min(max, x), min, max, startHeight + gap * 0.5f, gap);
        }

        // Start a new block.
        previousValue = value;
        previousX = x;

        if (previousX >= max) {
          // Drawn past max range, stop.
          break;
        }
      }
      // The last data point continues till max
      if (previousX < max && previousValue != null) {
        addRectangleDelta(previousValue, Math.max(min, previousX), max, min, max, startHeight + gap * 0.5f, gap);
      }
    }
  }

  @Override
  protected void draw(Graphics2D g2d, Dimension dim) {
    Stopwatch stopwatch = new Stopwatch().start();

    transform();

    long transformTime = stopwatch.getElapsedSinceLastDeltaNs();

    g2d.setFont(getFont());
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    assert myRectangles.size() == myRectangleValues.size();
    List<Rectangle2D.Float> transformedShapes = new ArrayList<>(myRectangles.size());
    List<T> transformedValues = new ArrayList<>(myRectangleValues.size());

    AffineTransform scale = AffineTransform.getScaleInstance(dim.getWidth(), dim.getHeight());
    for (int i = 0; i < myRectangles.size(); i++) {
      Rectangle2D.Float rectangle = myRectangles.get(i);
      // Manually scaling the rectangle results in ~6x performance improvement over
      // calling AffineTransform::createTransformedShape. The reason for this is the shape created is a Point2D.Double
      // this shape has to support all types of points as such cannot be rendered as efficiently as a
      // rectangle.
      transformedShapes.add(new Rectangle2D.Float((float)(rectangle.getX() * scale.getScaleX()),
                                                  (float)(rectangle.getY() * scale.getScaleY()),
                                                  (float)(rectangle.getWidth() * scale.getScaleX()),
                                                  (float)(rectangle.getHeight() * scale.getScaleY())));
      transformedValues.add(myRectangleValues.get(i));
    }

    long scalingTime = stopwatch.getElapsedSinceLastDeltaNs();

    myConfig.getReducer().reduce(transformedShapes, transformedValues);
    assert transformedShapes.size() == transformedValues.size();

    long reducerTime = stopwatch.getElapsedSinceLastDeltaNs();

    for (int i = 0; i < transformedShapes.size(); i++) {
      T value = transformedValues.get(i);
      Rectangle2D rect = transformedShapes.get(i);
      boolean isMouseOver = isMouseOverRectangle(rect);
      Color color = myColorProvider.getColor(isMouseOver, value);
      g2d.setColor(color);
      g2d.fill(rect);
      if (myRenderMode == RenderMode.TEXT) {
        String valueText = myTextConverter.convertToString(value);
        String text = AdtUiUtils.shrinkToFit(valueText, mDefaultFontMetrics, (float)rect.getWidth() - TEXT_PADDING * 2);
        if (!text.isEmpty()) {
          g2d.setColor(myColorProvider.getFontColor(isMouseOver, value));
          float textOffset = (float)(rect.getY() + (rect.getHeight() - mDefaultFontMetrics.getHeight()) / 2.0);
          textOffset += mDefaultFontMetrics.getAscent();
          g2d.drawString(text, (float)(rect.getX() + TEXT_PADDING), textOffset);
        }
      }
    }

    long drawTime = stopwatch.getElapsedSinceLastDeltaNs();

    addDebugInfo("XS ms: %.2fms, %.2fms", transformTime / 1000000.f, scalingTime / 1000000.f);
    addDebugInfo("RDT ms: %.2f, %.2f, %.2f", reducerTime / 1000000.f, drawTime / 1000000.f,
                 (scalingTime + reducerTime + drawTime) / 1000000.f);
    addDebugInfo("# of drawn rects: %d", transformedShapes.size());
  }
}

