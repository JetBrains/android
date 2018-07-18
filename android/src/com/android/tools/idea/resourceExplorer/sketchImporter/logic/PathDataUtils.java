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

import com.android.tools.idea.resourceExplorer.sketchImporter.structure.*;
import com.android.utils.Pair;

import java.awt.*;
import java.util.ArrayList;

import static com.android.tools.idea.resourceExplorer.sketchImporter.logic.CoordinateUtils.*;

public class PathDataUtils {

  private static final boolean CONDITION_FOR_RECTANGLE = true; //TODO: Delete line once _class is implemented

  private static final char MOVE_CURSOR_COMMAND_ABSOLUTE = 'M';
  private static final char HORIZONTAL_LINE_COMMAND_ABSOLUTE = 'H';
  private static final char VERTICAL_LINE_COMMAND_ABSOLUTE = 'V';
  private static final char LINE_COMMAND_ABSOLUTE = 'L';
  private static final char BEZIER_CURVE_COMMAND_ABSOLUTE = 'C';
  private static final char CLOSE_PATH_COMMAND = 'z';

  public static String buildShapeString(SketchShapeGroup shapeGroup) {

    SketchLayer[] shapeGroupLayers = shapeGroup.getLayers();
    Rectangle.Double frame = shapeGroup.getFrame();

    if (shapeGroupLayers.length == 1) {
      SketchLayer layer = shapeGroupLayers[0];

      if (false) {

        SketchShapePath shapePath = (SketchShapePath)layer;
        return buildPathString(shapePath, frame);
      }
      else if (CONDITION_FOR_RECTANGLE) {

        return buildRectangleString(frame);
      }
      //TODO: Write behaviour for other types of sketch objects: oval, star, polygon
    }
    else {
      //TODO: Write behaviour for combined shapes
    }

    return "";
  }

  public static String buildPathString(SketchShapePath shapePath, Rectangle.Double frame) {

    VectorPathBuilder vectorPathBuilder = new VectorPathBuilder();

    SketchCurvePoint[] points = shapePath.getPoints();

    StringPoint startCoords = calculateAbsolutePositionString(points[0], frame);
    vectorPathBuilder.appendCommandAndCoordinates(MOVE_CURSOR_COMMAND_ABSOLUTE, startCoords);


    for (int i = 1; i < points.length; i++) {

      SketchCurvePoint previousPoint = points[i - 1];
      SketchCurvePoint currentPoint = points[i];

      StringPoint previousPointCoords = calculateAbsolutePositionString(previousPoint, frame);
      StringPoint currentPointCoords = calculateAbsolutePositionString(currentPoint, frame);
      StringPoint previousPointCurveFromCoords = calculateAbsolutePositionString(previousPoint, frame);
      StringPoint currentPointCurveToCoords = currentPointCoords;

      if (previousPoint.isHasCurveFrom()) {
        previousPointCurveFromCoords = calculateAbsolutePositionString(SketchParser.getPosition(previousPoint.getCurveFrom()), frame);
      }
      if (currentPoint.isHasCurveTo()) {
        currentPointCurveToCoords = calculateAbsolutePositionString(SketchParser.getPosition(currentPoint.getCurveTo()), frame);
      }

      if (!previousPoint.isHasCurveTo() && !currentPoint.isHasCurveFrom()) {
        if (previousPointCoords.getY().equals(currentPointCoords.getY())) {
          vectorPathBuilder.append(HORIZONTAL_LINE_COMMAND_ABSOLUTE);
        }
        else if (previousPointCoords.getX().equals(currentPointCoords.getX())) {
          vectorPathBuilder.append(VERTICAL_LINE_COMMAND_ABSOLUTE);
        }
        else {
          vectorPathBuilder.append(LINE_COMMAND_ABSOLUTE);
        }
      }
      else {
        vectorPathBuilder.append(BEZIER_CURVE_COMMAND_ABSOLUTE);
        vectorPathBuilder.appendPointCoordinates(previousPointCurveFromCoords);
        vectorPathBuilder.appendPointCoordinates(currentPointCurveToCoords);

        //TODO: Can be optimized by also taking into consideration the S command for bezier curves
      }

      vectorPathBuilder.appendPointCoordinates(currentPointCoords);
    }
    if (shapePath.isClosed()) {
      vectorPathBuilder.append(CLOSE_PATH_COMMAND);
    }

    return vectorPathBuilder.getString();
  }

  public static String buildRectangleString(Rectangle.Double frame) {

    VectorPathBuilder vectorPathBuilder = new VectorPathBuilder();

    ArrayList<StringPoint> frameCorners = calculateStringFrameCorners(frame);

    vectorPathBuilder.appendCommandAndCoordinates(MOVE_CURSOR_COMMAND_ABSOLUTE, frameCorners.get(0));
    vectorPathBuilder.appendCommandAndCoordinate(HORIZONTAL_LINE_COMMAND_ABSOLUTE, frameCorners.get(1).getX());
    vectorPathBuilder.appendCommandAndCoordinate(VERTICAL_LINE_COMMAND_ABSOLUTE, frameCorners.get(2).getY());
    vectorPathBuilder.appendCommandAndCoordinate(HORIZONTAL_LINE_COMMAND_ABSOLUTE, frameCorners.get(3).getX());

    vectorPathBuilder.append(CLOSE_PATH_COMMAND);

    return vectorPathBuilder.getString();
  }
}