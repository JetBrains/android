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
package com.android.tools.adtui.chart.hchart;

import com.android.tools.adtui.model.HNode;
import org.jetbrains.annotations.NotNull;

import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * Implementation of {@link HTreeChartReducer} which combines all rectangles that are strictly inside a pixel into one rectangle.
 */
class DefaultHTreeChartReducer<T> implements HTreeChartReducer<T> {
  @Override
  public void reduce(@NotNull List<Rectangle2D.Float> rectangles, @NotNull List<HNode<T>> nodes) {
    assert nodes.size() == rectangles.size();
    int n = nodes.size();
    int index = 0;
    int keepIndex = 0;
    while (index < n) {
      Rectangle2D.Float rect = rectangles.get(index);
      HNode<T> node = nodes.get(index);
      if (Math.floor(rect.getMinX()) < Math.floor(rect.getMaxX())) {
        // Crossing several pixels on X axis
        rectangles.set(keepIndex, rect);
        nodes.set(keepIndex, node);
        ++keepIndex;
        ++index;
        continue;
      }

      // Whole rectangle inside a pixel on X axis, let's combine all rectangles inside the pixel
      int pixel = (int)Math.floor(rect.getMaxX());
      int curDepth = node.getDepth();

      HNode<T> keepNode = node;
      Rectangle2D.Float keepRect = rect;

      while (index < n) {
        rect = rectangles.get(index);
        node = nodes.get(index);

        if (node.getDepth() != curDepth || Math.floor(rect.getMaxX()) != pixel) {
          break;
        }
        keepRect.width = (float)(rect.getMaxX() - keepRect.getMinX());
        ++index;
      }

      rectangles.set(keepIndex, keepRect);
      nodes.set(keepIndex, keepNode);
      ++keepIndex;
    }
    rectangles.subList(keepIndex, rectangles.size()).clear();
    nodes.subList(keepIndex, nodes.size()).clear();
  }
}
