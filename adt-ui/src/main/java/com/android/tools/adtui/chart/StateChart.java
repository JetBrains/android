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

package com.android.tools.adtui.chart;

import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.DefaultDataSeries;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.SeriesData;
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
 * A chart component that renders series of state change events as rectangles.
 */
public class StateChart<E extends Enum<E>> extends AnimatedComponent {

  public enum RenderMode {
    BAR,  // Each state is rendered as a filled rectangle until the next state changed.
    TEXT  // Each state is marked with a vertical line and and corresponding state text/label at the beginning.
  }

  private static final int TEXT_PADDING = 3;

  @NotNull
  private final List<RangedSeries<E>> mSeriesList;

  @NotNull
  private final EnumMap<E, Color> mColors;

  private float mArcWidth;

  private float mArcHeight;

  private float mHeightGap;

  @NotNull
  private final ArrayList<RoundRectangle2D.Float> mRectangles;

  @NotNull
  private final List<E> mValues;

  @NotNull
  private RenderMode mRenderMode;

  /**
   * @param colors map of a state to corresponding color
   */
  public StateChart(@NotNull EnumMap<E, Color> colors) {
    mColors = colors;
    mRectangles = new ArrayList<>();
    mValues = new ArrayList<>();
    mSeriesList = new ArrayList<>();
    mRenderMode = RenderMode.BAR;
  }

  public void setRenderMode(RenderMode mode) {
    mRenderMode = mode;
  }

  public void addSeries(@NotNull RangedSeries<E> series) {
    mSeriesList.add(series);
  }

  /**
   * Sets the arc width parameter for the rectangles.
   */
  public void setArcWidth(float arcWidth) {
    mArcWidth = arcWidth;
  }

  /**
   * Sets the arc height parameter for the rectangles.
   */
  public void setArcHeight(float arcHeight) {
    mArcHeight = arcHeight;
  }

  /**
   * Sets the gap between multiple data series.
   *
   * @param gap The gap value as a percentage {0...1} of the height given to each data series
   */
  public void setHeightGap(float gap) {
    mHeightGap = gap;
  }

  @Override
  protected void updateData() {
    int seriesSize = mSeriesList.size();

    if (seriesSize == 0) {
      return;
    }

    // TODO support adding series on the fly and interpolation.
    float height = 1f / seriesSize;
    float gap = height * mHeightGap;
    mValues.clear();

    int seriesIndex = 0, rectCount = 0;
    for (RangedSeries<E> data : mSeriesList) {
      double min = data.getXRange().getMin();
      double max = data.getXRange().getMax();
      ImmutableList<SeriesData<E>> seriesDataList = data.getSeries();
      int size = seriesDataList.size();

      // Construct rectangles.
      long previousX = -1;
      E previousValue = null;

      for (int i = 0; i < size; i++) {
        SeriesData<E> seriesData = seriesDataList.get(i);
        long x = seriesData.x;
        E value = seriesData.value;

        float startHeight = 1 - (height * (seriesIndex + 1));

        if (i > 0) {
          // Draw the previous block.
          setRectangleAndValueData(rectCount,
                                   previousX,
                                   x,
                                   min,
                                   max,
                                   previousValue,
                                   startHeight + gap * 0.5f,
                                   height - gap);
          rectCount++;
        }

        // Start a new block.
        previousValue = value;
        previousX = x;

        if (x >= max) {
          // Drawn past max range, stop.
          break;
        }
        else if (i == size - 1) {
          // Reached the end, assumes the last data point continues till max.
          setRectangleAndValueData(rectCount,
                                   previousX,
                                   max,
                                   min,
                                   max,
                                   previousValue,
                                   startHeight + gap * 0.5f,
                                   height - gap);
          rectCount++;
        }
      }
      seriesIndex++;
    }

    if (rectCount < mRectangles.size()) {
      mRectangles.subList(rectCount, mRectangles.size()).clear();
    }
  }

  @Override
  protected void draw(Graphics2D g2d) {
    Dimension dim = getSize();
    g2d.setFont(AdtUiUtils.DEFAULT_FONT);
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    assert mRectangles.size() == mValues.size();
    AffineTransform scale = AffineTransform.getScaleInstance(dim.getWidth(), dim.getHeight());
    for (int i = 0; i < mRectangles.size(); i++) {
      g2d.setColor(mColors.get(mValues.get(i)));
      Shape shape = scale.createTransformedShape(mRectangles.get(i));

      switch (mRenderMode) {
        case BAR:
          g2d.fill(shape);
          break;
        case TEXT:
          Rectangle2D rect = shape.getBounds2D();
          g2d.draw(new Line2D.Double(rect.getX(), rect.getY(), rect.getX(), rect.getY() + rect.getHeight()));
          String text = AdtUiUtils.getFittedString(mDefaultFontMetrics,
                                                   mValues.get(i).toString(),
                                                   (float)rect.getWidth() - TEXT_PADDING * 2,
                                                   1);
          if (!text.isEmpty()) {
            g2d.setColor(AdtUiUtils.DEFAULT_FONT_COLOR);
            g2d.drawString(text, (float)(rect.getX() + TEXT_PADDING), (float)(rect.getY() + rect.getHeight() - TEXT_PADDING));
          }
          break;
      }
    }
  }

  private void setRectangleAndValueData(int rectCount,
                                        double previousX,
                                        double currentX,
                                        double minX,
                                        double maxX,
                                        E previousValue,
                                        float rectY,
                                        float rectHeight) {
    RoundRectangle2D.Float rect;

    //Reuse existing Rectangle objects when possible to avoid unnecessary allocations.
    if (rectCount == mRectangles.size()) {
      rect = new RoundRectangle2D.Float();
      mRectangles.add(rect);
    } else {
      rect = mRectangles.get(rectCount);
    }

    mValues.add(previousValue);

    rect.setRoundRect((previousX - minX) / (maxX - minX),
                      rectY,
                      (currentX - previousX) / (maxX - minX),
                      rectHeight,
                      mArcWidth,
                      mArcHeight);
  }
}

