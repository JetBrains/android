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
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchLayer;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchShapeGroup;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchShapePath;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;

import static com.android.tools.idea.resourceExplorer.sketchImporter.logic.CoordinateUtils.*;
import static com.android.tools.idea.resourceExplorer.sketchImporter.structure.deserializers.SketchLayerDeserializer.OVAL_CLASS_TYPE;
import static com.android.tools.idea.resourceExplorer.sketchImporter.structure.deserializers.SketchLayerDeserializer.RECTANGLE_CLASS_TYPE;

public class PathDataUtils {

  public static String buildShapeString(SketchShapeGroup shapeGroup) {

    SketchLayer[] shapeGroupLayers = shapeGroup.getLayers();
    Rectangle.Double frame = shapeGroup.getFrame();

    if (shapeGroupLayers.length == 1) {
      SketchLayer layer = shapeGroupLayers[0];

      SketchShapePath shapePath = (SketchShapePath)layer;
      return buildSingleShapeString(shapePath, frame);
    }
    else {
      //TODO: Write behaviour for combined shapes
    }

    return "";
  }

  public static String buildSingleShapeString(SketchShapePath shapePath, Rectangle.Double frame) {

    if (RECTANGLE_CLASS_TYPE.equals(shapePath.getClassType())) {

      if (hasRoundCorners(shapePath)) {

        return buildRoundRectanglePathString(shapePath, frame);
      }
      else {

        return buildRectanglePathString(shapePath.getFrame(), frame);
      }
    }
    else {
      return buildGenericPathString(shapePath, frame);
    }
  }

  public static String buildGenericPathString(SketchShapePath shapePath, Rectangle.Double parentFrame) {

    VectorPathBuilder vectorPathBuilder = new VectorPathBuilder();

    SketchCurvePoint[] points = shapePath.getPoints();

    Point2D.Double startCoordsDouble =
      makeAbsolutePositionDouble(percentagesToRelativePosition(points[0].getPoint(), shapePath.getFrame()), parentFrame,
                                 shapePath.getFrame());
    vectorPathBuilder.startPath(startCoordsDouble);

    SketchCurvePoint previousPoint;
    SketchCurvePoint currentPoint = points[0];

    for (int i = 1; i < points.length; i++) {

      previousPoint = points[i - 1];
      currentPoint = points[i];


      Point2D.Double previousPointCoordsDouble = percentagesToRelativePosition(previousPoint.getPoint(), shapePath.getFrame());
      StringPoint previousPointCoords =
        makeAbsolutePositionString(previousPointCoordsDouble, parentFrame, shapePath.getFrame());

      Point2D.Double currentPointCoordsDouble = percentagesToRelativePosition(currentPoint.getPoint(), shapePath.getFrame());
      StringPoint currentPointCoords =
        makeAbsolutePositionString(currentPointCoordsDouble, parentFrame, shapePath.getFrame());

      StringPoint previousPointCurveFromCoords = previousPointCoords;
      StringPoint currentPointCurveToCoords = currentPointCoords;

      if (previousPoint.hasCurveFrom()) {
        previousPointCurveFromCoords =
          makeAbsolutePositionString(percentagesToRelativePosition(previousPoint.getCurveFrom(), shapePath.getFrame()),
                                     parentFrame, shapePath.getFrame());
      }
      if (currentPoint.hasCurveFrom()) {
        currentPointCurveToCoords =
          makeAbsolutePositionString(percentagesToRelativePosition(currentPoint.getCurveTo(), shapePath.getFrame()),
                                     parentFrame, shapePath.getFrame());
      }

      if (!previousPoint.hasCurveTo() && !currentPoint.hasCurveFrom()) {
        if (previousPointCoords.getY().equals(currentPointCoords.getY())) {
          vectorPathBuilder.createHorizontalLine(currentPointCoords);
        }
        else if (previousPointCoords.getX().equals(currentPointCoords.getX())) {
          vectorPathBuilder.createVerticalLine(currentPointCoords);
        }
        else {
          vectorPathBuilder.createLine(currentPointCoords);
        }
      }
      else {
        vectorPathBuilder.createBezierCurve(previousPointCurveFromCoords, currentPointCurveToCoords, currentPointCoords);

        //TODO: Can be optimized by also taking into consideration the S command for bezier curves
      }
    }

    if (OVAL_CLASS_TYPE.equals(shapePath.getClassType())) {
      vectorPathBuilder.closeOvalShape(shapePath, currentPoint, parentFrame);
    }

    if (shapePath.isClosed()) {
      vectorPathBuilder.endPath();
    }

    return vectorPathBuilder.getVectorString();
  }

  public static String buildRectanglePathString(Rectangle.Double rectangleFrame, Rectangle.Double shapeGroupFrame) {

    VectorPathBuilder vectorPathBuilder = new VectorPathBuilder();

    ArrayList<StringPoint> frameCorners = calculateStringFrameCorners(rectangleFrame, shapeGroupFrame);

    vectorPathBuilder.startPath(frameCorners.get(0));
    vectorPathBuilder.createHorizontalLine(frameCorners.get(1));
    vectorPathBuilder.createVerticalLine(frameCorners.get(2));
    vectorPathBuilder.createHorizontalLine(frameCorners.get(3));

    vectorPathBuilder.endPath();

    return vectorPathBuilder.getVectorString();
  }

  public static String buildRoundRectanglePathString(SketchShapePath shapePath, Rectangle.Double shapeGroupFrame) {

    VectorPathBuilder vectorPathBuilder = new VectorPathBuilder();

    SketchCurvePoint[] points = shapePath.getPoints();

    Point2D.Double startPoint = new Point2D.Double(0, 0);
    Point2D.Double endPoint = new Point2D.Double(0, 0);
    Point2D.Double previousPoint = new Point2D.Double(0, 0);

    for (int i = 0; i < points.length; i++) {
      switch (i) {
        case 0:
          startPoint.setLocation(0, points[i].getCornerRadius());
          endPoint.setLocation(points[i].getCornerRadius(), 0);
          break;
        case 1:
          startPoint.setLocation(shapeGroupFrame.getWidth() - points[i].getCornerRadius(), 0);
          endPoint.setLocation(shapeGroupFrame.getWidth(), points[i].getCornerRadius());
          break;
        case 2:
          startPoint.setLocation(shapeGroupFrame.getWidth(), shapeGroupFrame.getHeight() - points[i].getCornerRadius());
          endPoint.setLocation(shapeGroupFrame.getWidth() - points[i].getCornerRadius(), shapeGroupFrame.getHeight());
          break;
        case 3:
          startPoint.setLocation(points[i].getCornerRadius(), shapeGroupFrame.getHeight());
          endPoint.setLocation(0, shapeGroupFrame.getHeight() - points[i].getCornerRadius());
          break;
      }

      if (points[i].getCornerRadius() != 0) {
        if (!previousPoint.equals(startPoint) && i != 0) {
          vectorPathBuilder.createLine(makeAbsolutePositionString(startPoint, shapeGroupFrame, shapePath.getFrame()));
        }

        Point2D.Double relativePoint = percentagesToRelativePosition(points[i].getPoint(), shapeGroupFrame);
        vectorPathBuilder.createQuadCurve(makeAbsolutePositionString(relativePoint, shapeGroupFrame, shapePath.getFrame()),
                                          makeAbsolutePositionString(endPoint, shapeGroupFrame, shapePath.getFrame()));
      }
      else {
        if (i == 0) {
          vectorPathBuilder.startPath(makeAbsolutePositionString(startPoint, shapeGroupFrame, shapePath.getFrame()));
        }
        else {
          vectorPathBuilder.createLine(makeAbsolutePositionString(startPoint, shapeGroupFrame, shapePath.getFrame()));
        }
      }

      previousPoint.setLocation(endPoint);
    }

    vectorPathBuilder.endPath();

    return vectorPathBuilder.getVectorString();
  }

  public static boolean hasRoundCorners(SketchShapePath rectangle) {

    SketchCurvePoint[] points = rectangle.getPoints();

    for (SketchCurvePoint point : points) {
      if (point.getCornerRadius() != 0) {
        return true;
      }
    }

    return false;
  }
}
