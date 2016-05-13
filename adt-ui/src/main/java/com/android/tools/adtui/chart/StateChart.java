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

import com.android.annotations.NonNull;
import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.model.RangedDiscreteSeries;
import com.android.tools.adtui.model.StateChartData;
import gnu.trove.TIntArrayList;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;

/**
 * A chart component that renders series of state change events as rectangles.
 */
public class StateChart extends AnimatedComponent {

  @NonNull
  private final StateChartData mData;

  @NonNull
  private final Color[] mColors;

  private float mArcWidth;

  private float mArcHeight;

  private float mHeightGap;

  @NonNull
  private final ArrayList<RoundRectangle2D.Float> mPaths;

  @NonNull
  private final TIntArrayList mValues;

  @NonNull
  private Point mMousePosition;

  private boolean mHovered;

  /**
   * @param data   The state chart data.
   * @param colors An array of colors corresponding to the different states from the data. TODO
   *               need a better solution than passing in a Color array, as that has no
   *               correlation to the enum types used by the data.
   */
  public StateChart(@NonNull StateChartData data, @NonNull Color[] colors) {
    mData = data;
    mColors = colors;
    mPaths = new ArrayList<>();
    mValues = new TIntArrayList();
    mMousePosition = new Point();

    // TODO these logic should be moved to the Selection/Overlay Component.
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        mHovered = true;
      }

      @Override
      public void mouseExited(MouseEvent e) {
        mHovered = false;
      }
    });

    addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        mMousePosition.x = e.getX();
        mMousePosition.y = e.getY();
      }
    });
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
    int seriesSize = mData.series().size();
    int rectCount = 0;
    if (seriesSize > 0) {
      // TODO support adding series on the fly and interpolation.
      float height = 1f / seriesSize;
      float gap = height * mHeightGap;
      int seriesCount = 0;

      int size;
      double min, max;
      long x, previousX;
      int y, previousY;
      float startHeight;
      RoundRectangle2D.Float rect;
      for (RangedDiscreteSeries data : mData.series()) {
        min = data.getXRange().getMin();
        max = data.getXRange().getMax();
        size = data.getSeries().size();

        // Add paths as continuous blocks of rectangles.
        previousY = -1;
        previousX = -1;

        for (int i = 0; i < size; i++) {
          x = data.getSeries().getX(i);
          y = data.getSeries().getY(i);
          startHeight = 1 - (height * (seriesCount + 1));

          if (i > 0) {
            // Draw the previous block.
            setRectangleAndValueData(rectCount,
                                     previousX,
                                     x,
                                     min,
                                     max,
                                     previousY,
                                     startHeight + gap * 0.5f,
                                     height - gap);
            rectCount++;
          }

          // Start a new block.
          previousY = y;
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
                                     previousY,
                                     startHeight + gap * 0.5f,
                                     height - gap);
            rectCount++;
            break;
          }
        }
        seriesCount++;
      }
    }

    if (rectCount < mPaths.size() - 1) {
      mPaths.subList(rectCount, mPaths.size()).clear();
      mValues.remove(rectCount, mValues.size() - rectCount);
    }
  }

  @Override
  protected void draw(Graphics2D g2d) {
    Dimension dim = getSize();
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    assert mPaths.size() == mValues.size();
    AffineTransform scale = AffineTransform.getScaleInstance(dim.getWidth(), dim.getHeight());
    for (int i = 0; i < mPaths.size(); i++) {
      g2d.setColor(mColors[mValues.get(i) % mColors.length]);
      Shape shape = scale.createTransformedShape(mPaths.get(i));
      g2d.fill(shape);
    }
  }

  @Override
  protected void debugDraw(Graphics2D g) {
    super.debugDraw(g);

    if (mHovered) {
      Dimension dim = getSize();
      for (RangedDiscreteSeries data : mData.series()) {
        double min = data.getXRange().getMin();
        double max = data.getXRange().getMax();
        double range = max - min;
        long targetX = (long)(range * mMousePosition.x / dim.width + min);
        addDebugInfo("State: %s", data.getSeries().findYFromX(targetX).toString());
      }
    }
  }

  private RoundRectangle2D.Float setRectangleAndValueData(int rectCount,
                                                          double previousX,
                                                          double currentX,
                                                          double minX,
                                                          double maxX,
                                                          int previousY,
                                                          float rectY,
                                                          float rectHeight) {
    RoundRectangle2D.Float rect;

    //Reuse existing Rectangle objects when possible to avoid unnecessary allocations.
    if (rectCount == mPaths.size()) {
      rect = new RoundRectangle2D.Float();
      mPaths.add(rect);
      mValues.add(previousY);

    }
    else {
      rect = mPaths.get(rectCount);
      mValues.set(rectCount, previousY);
    }

    rect.setRoundRect((previousX - minX) / (maxX - minX),
                      rectY,
                      (currentX - previousX) / (maxX - minX),
                      rectHeight,
                      mArcWidth,
                      mArcHeight);

    return rect;
  }
}

