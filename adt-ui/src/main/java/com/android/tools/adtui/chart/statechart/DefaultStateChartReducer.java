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

import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Implementation of {@link StateChartReducer} which combines all rectangles that are strictly inside a pixel into one rectangle
 * and as a value of the combined rectangle will be taken most occurred value (i.e with the biggest total width).
 */
public class DefaultStateChartReducer<T> implements StateChartReducer<T> {
  @Override
  public void reduce(@NotNull List<Rectangle2D.Float> rectangles, @NotNull List<T> values) {
    int index = 0, keepIndex = 0;
    Map<T, Float> occurrenceWidth = new HashMap<>();

    while (index < rectangles.size()) {
      T value = values.get(index);
      Rectangle2D.Float rect = rectangles.get(index);

      // Crossing several pixels, let's just keep it
      if (Math.floor(rect.x) < Math.floor(rect.x + rect.width)) {
        rectangles.set(keepIndex, rect);
        values.set(keepIndex, value);
        ++keepIndex;
        ++index;
        continue;
      }

      occurrenceWidth.clear();

      // Whole rectangle is within the pixel
      float pixelStart = (float)Math.floor(rect.x);
      float pixelEnd = pixelStart + 1.0f;

      float minX = rect.x;
      float maxX = Float.MIN_VALUE;
      float minY = rect.y;
      float height = rect.height;

      while (index < rectangles.size()) {
        rect = rectangles.get(index);

        if (rect.x < pixelStart || rect.x > pixelEnd || rect.x + rect.width > pixelEnd) {
          // Crossed different pixel
          break;
        }
        maxX = rect.x + rect.width;

        value = values.get(index);
        Float width = occurrenceWidth.get(value);
        occurrenceWidth.put(value, (width == null ? 0 : width) + rect.width);
        ++index;
      }

      T mostOccurred = null;
      float mostOccurredWidth = -1;
      for (Map.Entry<T, Float> entry : occurrenceWidth.entrySet()) {
        if (entry.getValue() > mostOccurredWidth) {
          mostOccurredWidth = entry.getValue().floatValue();
          mostOccurred = entry.getKey();
        }
      }

      if (mostOccurred != null) {
        values.set(keepIndex, mostOccurred);
        // As width of the new rectangle smaller than 1px, arcWidth and arcHeight won't make any difference
        // thus, drawing "Rectangle2D" instead of "RoundRectangle2D"
        rectangles.set(keepIndex, new Rectangle2D.Float(minX, minY, maxX - minX, height));
        ++keepIndex;
      }
    }

    rectangles.subList(keepIndex, rectangles.size()).clear();
    values.subList(keepIndex, values.size()).clear();
  }
}
