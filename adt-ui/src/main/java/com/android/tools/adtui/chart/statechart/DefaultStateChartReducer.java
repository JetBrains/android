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

import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of {@link StateChartReducer} which combines all rectangles that are strictly inside a pixel into one rectangle
 * and as a value of the combined rectangle will be taken most occurred value (i.e with the biggest total width).
 */
public class DefaultStateChartReducer<E extends Enum<E> > implements StateChartReducer<E> {
  @Override
  public void reduce(@NotNull List<Shape> rectangles,
                     @NotNull List<E> values) {
    int index = 0, keepIndex = 0;
    while (index < rectangles.size()) {
      Shape shape = rectangles.get(index);
      E value = values.get(index);
      Rectangle2D bounds = shape.getBounds2D();

      // Crossing several pixels, let's just keep it
      if (Math.floor(bounds.getMinX()) < Math.floor(bounds.getMaxX())) {
        rectangles.set(keepIndex, shape);
        values.set(keepIndex, value);
        ++keepIndex;
        ++index;
        continue;
      }
      // Whole rectangle is within the pixel

      int pixel = (int)(Math.floor(bounds.getMaxX()));

      Map<E, Float> occurrenceWidth = new HashMap<>();
      float minX = (float)bounds.getMinX(), maxX = Float.MIN_VALUE;
      float minY = (float)bounds.getMinY(), maxY = (float)bounds.getMaxY();

      while (index < rectangles.size()) {
        shape = rectangles.get(index);
        value = values.get(index);
        bounds = shape.getBounds2D();

        if ((int)(Math.floor(bounds.getMinX())) != pixel || (int)(Math.floor(bounds.getMaxX())) != pixel) {
          // Crossed different pixel
          break;
        }
        maxX = (float)bounds.getMaxX();

        Float width = occurrenceWidth.get(value);
        occurrenceWidth.put(value, (width == null ? 0: width) + (float)bounds.getWidth());
        ++index;
      }

      E mostOccurred = null;
      float mostOccurredWidth = -1;
      for (Map.Entry<E, Float> entry: occurrenceWidth.entrySet()) {
        if (entry.getValue() > mostOccurredWidth) {
          mostOccurredWidth = entry.getValue().floatValue();
          mostOccurred = entry.getKey();
        }
      }

      if (mostOccurred != null) {
        values.set(keepIndex, mostOccurred);
        // As width of the new rectangle smaller than 1px, arcWidth and arcHeight won't make any difference
        // thus, drawing "Rectangle2D" instead of "RoundRectangle2D"
        rectangles.set(keepIndex, new Rectangle2D.Float(minX, minY, maxX - minX, maxY - minY));
        ++keepIndex;
      }
    }

    rectangles.subList(keepIndex, rectangles.size()).clear();
    values.subList(keepIndex, values.size()).clear();
  }
}
