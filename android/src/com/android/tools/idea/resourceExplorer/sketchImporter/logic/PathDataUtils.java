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

import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchCurvePoint;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.interfaces.SketchLayer;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchShapeGroup;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchShapePath;

import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;

import static com.android.tools.idea.resourceExplorer.sketchImporter.structure.interfaces.SketchLayer.*;
import static java.awt.geom.PathIterator.*;

public class PathDataUtils {

  public static String buildShapeString(SketchShapeGroup shapeGroup, Point2D.Double parentCoordinates) {

    SketchLayer[] shapeGroupLayers = shapeGroup.getLayers();
    Rectangle.Double frame = shapeGroup.getFrame();
    frame.setRect(frame.getX() + parentCoordinates.getX(),
                  frame.getY() + parentCoordinates.getY(),
                  frame.getWidth(),
                  frame.getHeight());

    if (shapeGroupLayers.length == 1) {
      SketchLayer layer = shapeGroupLayers[0];

      SketchShapePath sketchShapePath = (SketchShapePath)layer;

      return buildSingleShapeString(sketchShapePath, frame);
    }
    else {

      return buildCombinedShapeString(shapeGroupLayers, frame);
    }
  }

  private static String buildSingleShapeString(SketchShapePath sketchShapePath, Rectangle.Double parentFrame) {

    Path2D.Double shapePath = sketchShapePath.toPath2D(parentFrame);

    return toPathString(shapePath);
  }

  private static String buildCombinedShapeString(SketchLayer[] shapeGroupLayers, Rectangle.Double parentFrame) {

    SketchShapePath baseSketchShapePath = (SketchShapePath)shapeGroupLayers[0];
    Path2D.Double baseShapePath = baseSketchShapePath.toPath2D(parentFrame);
    Area baseShapeArea = new Area(baseShapePath);

    for (int i = 1; i < shapeGroupLayers.length; i++) {
      SketchShapePath sketchShapePath = (SketchShapePath)shapeGroupLayers[i];
      Path2D.Double shapePath = sketchShapePath.toPath2D(parentFrame);
      Area shapeArea = new Area(shapePath);

      int booleanOperation = sketchShapePath.getBooleanOperation();
      switch (booleanOperation) {
        case BOOLEAN_OPERATION_UNION:
          baseShapeArea.add(shapeArea);
          break;
        case BOOLEAN_OPERATION_SUBSTRACTION:
          baseShapeArea.subtract(shapeArea);
          break;
        case BOOLEAN_OPERATION_DIFFERENCE:
          baseShapeArea.exclusiveOr(shapeArea);
          break;
        case BOOLEAN_OPERATION_INTERSECTION:
          baseShapeArea.intersect(shapeArea);
          break;
      }
    }

    return toPathString(baseShapeArea);
  }

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
