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
package com.android.tools.adtui.common.datareducer;

import com.android.tools.adtui.chart.linechart.LineConfig;
import org.jetbrains.annotations.NotNull;

import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;

/**
 * The {@link LineChartReducer} is used by {@code LineChart} component to be able
 * to render faster by reducing its data.
 * The basic idea behind this algorithm is to reduce number of points to available pixels.
 * For every pixel it draws two points, i.e with minimum Y coordinate and with maximum Y coordinate.
 * It draws similar shape with the original, because of the fact that width of a line is 1px.
 */
public class LineChartReducer implements DataReducer {
  private static final double EPS  = 1e-6;

  /**
   * Number 6 is needed by {@link PathIterator#currentSegment(float[])}.
   */
  private static int PATH_ITERATOR_COORDS_COUNT = 6;

  @Override
  public Path2D reduce(@NotNull Path2D path, @NotNull LineConfig config) {
    if (config.isStepped()) {
      // TODO: implement better reducer for a stepped line
      return path;
    }

    if (path.getCurrentPoint() == null) {
      return path;
    }

    Path2D.Float resultPath = new Path2D.Float();

    double[] coords = new double[PATH_ITERATOR_COORDS_COUNT];
    double pixel = -1;
    double minX = -1, minY = -1, maxX = -1, maxY = -1;

    PathIterator iterator = path.getPathIterator(null);
    // Add the first point
    int segType = iterator.currentSegment(coords);
    assert segType == PathIterator.SEG_MOVETO || segType == PathIterator.SEG_LINETO;
    addToPath(resultPath, coords[0], coords[1]);
    iterator.next();

    double curX = coords[0], curY = coords[1];
    while (!iterator.isDone()) {
      segType = iterator.currentSegment(coords);
      assert segType == PathIterator.SEG_MOVETO || segType == PathIterator.SEG_LINETO;

      double lastX = curX;
      curX = coords[0];
      curY = coords[1];

      if (curX < lastX) {
        // This can happen only for a filled line
        break;
      }

      if (pixel == -1 || curX >= pixel) {
        // We entered into a new pixel

        if (pixel != -1) {
          // Add min and max points from previous pixel
          addMinMaxPoints(resultPath, minX, minY, maxX, maxY);
        }

        pixel = Math.floor(curX) + 1;
        minX = maxX = curX;
        minY = maxY = curY;
      } else {
        // We are in the same pixel

        if (minY > curY) {
          minY = curY;
          minX = curX;
        }

        if (maxY <= curY) {
          // Equality is an optimization when all points inside a pixel have the same y,
          // by doing this we pick up left most and right most points.
          maxY = curY;
          maxX = curX;
        }
      }
      iterator.next();
    }

    addMinMaxPoints(resultPath, minX, minY, maxX, maxY);

    // The path could be a filled line, it means that before calling the reduce we added two vertical segments to the X axis
    // from the first and last points. Ideally, we need to add 2 last points, but note that the algorithm will add the second last
    // point as it has maximum Y coordinate and it is the right most.
    addToPath(resultPath, curX, curY);

    return resultPath;
  }

  private static void addMinMaxPoints(@NotNull Path2D path, double minX, double minY, double maxX, double maxY) {
    if (maxX < minX ){
      addToPath(path, maxX, maxY);
      addToPath(path, minX, minY);
    } else {
      addToPath(path, minX, minY);
      addToPath(path, maxX, maxY);
    }
  }

  private static void addToPath(@NotNull Path2D path, double x, double y) {
    if (path.getCurrentPoint() == null) {
      path.moveTo(x, y);
    } else {
      // Don't repeat the current point
      if (!equals(path.getCurrentPoint().getX(), x) || !equals(path.getCurrentPoint().getY(), y)) {
        path.lineTo(x, y);
      }
    }
  }

  private static boolean equals(double x, double y) {
    return Math.abs(x - y) <= EPS;
  }
}
