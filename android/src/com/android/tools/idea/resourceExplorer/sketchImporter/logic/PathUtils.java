/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.sketchImporter.logic;

import java.awt.*;
import java.awt.geom.PathIterator;

import static java.awt.geom.PathIterator.*;
import static java.awt.geom.PathIterator.SEG_CLOSE;

public class PathUtils {

  public static String toPathString(Shape shape) {

    PathStringBuilder pathStringBuilder = new PathStringBuilder();

    PathIterator pathIterator = shape.getPathIterator(null);

    while (!pathIterator.isDone()) {
      double[] coordinates = new double[6];
      int type = pathIterator.currentSegment(coordinates);

      switch (type) {
        case SEG_MOVETO:
          pathStringBuilder.startPath(coordinates[0], coordinates[1]);
          break;
        case SEG_LINETO:
          pathStringBuilder.createLine(coordinates[0], coordinates[1]);
          break;
        case SEG_CUBICTO:
          pathStringBuilder.createBezierCurve(coordinates);
          break;
        case SEG_QUADTO:
          pathStringBuilder.createQuadCurve(coordinates[0], coordinates[1], coordinates[2], coordinates[3]);
          break;
        case SEG_CLOSE:
          pathStringBuilder.endPath();
          break;
      }

      pathIterator.next();
    }
    return pathStringBuilder.build();
  }
}
