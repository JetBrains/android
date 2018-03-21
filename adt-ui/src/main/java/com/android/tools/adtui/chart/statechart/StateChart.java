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
import com.android.tools.adtui.common.EnumColors;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.StateChartModel;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.List;

/**
 * A chart component that renders series of state change events as rectangles.
 */
public class StateChart<E extends Enum<E>> extends MouseAdapterComponent<Long> {

  public enum RenderMode {
    BAR,  // Each state is rendered as a filled rectangle until the next state changed.
    TEXT  // Each state is marked with a vertical line and and corresponding state text/label at the beginning.
  }

  private static final int TEXT_PADDING = 3;

  private StateChartModel<E> myModel;

  @NotNull
  private EnumColors<E> mColors;

  private float mHeightGap;

  @NotNull
  private final HashMap<Rectangle2D.Float, E> mValues;

  @NotNull
  private RenderMode mRenderMode;

  @NotNull
  private StateChartConfig<E> myConfig;

  private boolean myRender;

  /**
   * @param colors map of a state to corresponding color
   */
  public StateChart(@NotNull StateChartModel<E> model, @NotNull Map<E, Color> colors) {
    this(model, new EnumColors<>(colors));
  }

  public StateChart(@NotNull StateChartModel<E> model, @NotNull EnumColors<E> enumColors) {
    this(model, enumColors, new StateChartConfig(new DefaultStateChartReducer<>()));
  }

  @VisibleForTesting
  public StateChart(@NotNull StateChartModel<E> model, @NotNull Map<E, Color> colors, @NotNull StateChartConfig<E> config) {
    this(model, new EnumColors<>(colors), config);
  }

  @VisibleForTesting
  public StateChart(@NotNull StateChartModel<E> model, @NotNull EnumColors<E> enumColors, @NotNull StateChartConfig<E> config) {
    super(config.getRectangleHeightRatio(), config.getRectangleMouseOverHeightRatio());
    mColors = enumColors;
    mValues = new HashMap<>();
    mRenderMode = RenderMode.BAR;
    myConfig = config;
    myRender = true;
    setFont(AdtUiUtils.DEFAULT_FONT);
    setModel(model);
    setHeightGap(myConfig.getHeightGap());
  }

  public void setModel(@NotNull StateChartModel<E> model) {
    if (myModel != null) {
      myModel.removeDependencies(myAspectObserver);
    }
    myModel = model;
    myModel.addDependency(myAspectObserver).onChange(StateChartModel.Aspect.STATE_CHART, this::modelChanged);
    modelChanged();
  }

  private void modelChanged() {
    myRender = true;
    opaqueRepaint();
  }

  public void setRenderMode(RenderMode mode) {
    mRenderMode = mode;
  }

  /**
   * Sets the gap between multiple data series.
   *
   * @param gap The gap value as a percentage {0...1} of the height given to each data series
   */
  public void setHeightGap(float gap) {
    mHeightGap = gap;
  }

  @NotNull
  public EnumColors<E> getColors() {
    return mColors;
  }

  protected void render() {
    long renderTime = System.nanoTime();

    List<RangedSeries<E>> series = myModel.getSeries();
    int seriesSize = series.size();
    if (seriesSize == 0) {
      return;
    }

    // TODO support adding series on the fly and interpolation.
    float height = 1f / seriesSize;
    float gap = height * mHeightGap;
    setHeightFactor(height);
    mValues.clear();

    int seriesIndex = 0;
    long rectCount = 0;
    Set<Long> pastRectangleKeys = getRectangleKeys();
    for (RangedSeries<E> data : series) {
      double min = data.getXRange().getMin();
      double max = data.getXRange().getMax();
      float startHeight = 1 - (height * (seriesIndex + 1));

      // Construct rectangles.
      long previousX = -1;
      E previousValue = null;
      for (SeriesData<E> seriesData : data.getSeries()) {
        long x = seriesData.x;
        E value = seriesData.value;

        if (value.equals(previousValue)) {
          // Ignore repeated values
          continue;
        }

        // Don't draw if this block doesn't intersect with [min..max]
        if (previousValue != null && x >= min) {
          // Draw the previous block.
          Rectangle2D.Float rect = setRectangleData(rectCount,
                                                    Math.max(min, previousX),
                                                    Math.min(max, x),
                                                    min,
                                                    max,
                                                    startHeight + gap * 0.5f,
                                                    gap);
          pastRectangleKeys.remove(rectCount);
          mValues.put(rect, previousValue);
          rectCount++;
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
        Rectangle2D.Float rect = setRectangleData(rectCount,
                                                  Math.max(min, previousX),
                                                  max,
                                                  min,
                                                  max,
                                                  startHeight + gap * 0.5f,
                                                  gap);
        pastRectangleKeys.remove(rectCount);
        mValues.put(rect, previousValue);
        rectCount++;
      }
      seriesIndex++;
    }

    for (Long key : pastRectangleKeys) {
      mValues.remove(getRectangle(key));
      removeRectangle(key);
    }

    addDebugInfo("Render time: %.2fms", (System.nanoTime() - renderTime) / 1000000.f);
  }

  @Override
  protected void draw(Graphics2D g2d, Dimension dim) {
    if (myRender) {
      render();
      myRender = false;
    }
    long drawTime = System.nanoTime();

    g2d.setFont(getFont());
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    assert getRectangleCount() == mValues.size();

    List<Shape> transformedShapes = new ArrayList<>(getRectangleCount());
    List<E> transformedValues = new ArrayList<>(getRectangleCount());
    AffineTransform scale = AffineTransform.getScaleInstance(dim.getWidth(), dim.getHeight());
    for (Long key : getRectangleKeys()) {
      transformedShapes.add(scale.createTransformedShape(getRectangle(key)));
      transformedValues.add(mValues.get(getRectangle(key)));
    }
    myConfig.getReducer().reduce(transformedShapes, transformedValues);
    assert transformedShapes.size() == transformedValues.size();

    for (int i = 0; i < transformedShapes.size(); i++) {
      Shape shape = transformedShapes.get(i);
      E value = transformedValues.get(i);
      g2d.setColor(mColors.getColor(value));

      switch (mRenderMode) {
        case BAR:
          g2d.fill(shape);
          break;
        case TEXT:
          Rectangle2D rect = shape.getBounds2D();
          g2d.draw(new Line2D.Double(rect.getX(), rect.getY(), rect.getX(), rect.getY() + rect.getHeight()));
          String text = AdtUiUtils.shrinkToFit(value.toString(), mDefaultFontMetrics, (float)rect.getWidth() - TEXT_PADDING * 2);
          if (!text.isEmpty()) {
            g2d.setColor(AdtUiUtils.DEFAULT_FONT_COLOR);
            g2d.drawString(text, (float)(rect.getX() + TEXT_PADDING), (float)(rect.getY() + rect.getHeight() - TEXT_PADDING));
          }
          break;
      }
    }

    addDebugInfo("Draw time: %.2fms", (System.nanoTime() - drawTime) / 1000000.f);
    addDebugInfo("# of drawn rects: %d", transformedShapes.size());
  }
}

