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
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchShapePath;
import org.jetbrains.annotations.NotNull;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import static com.android.tools.idea.resourceExplorer.sketchImporter.logic.CoordinateUtils.makeAbsolutePositionString;
import static com.android.tools.idea.resourceExplorer.sketchImporter.logic.CoordinateUtils.percentagesToRelativePosition;

public class VectorPathBuilder {
  private static final char MOVE_CURSOR_COMMAND_ABSOLUTE = 'M';
  private static final char HORIZONTAL_LINE_COMMAND_ABSOLUTE = 'H';
  private static final char VERTICAL_LINE_COMMAND_ABSOLUTE = 'V';
  private static final char LINE_COMMAND_ABSOLUTE = 'L';
  private static final char BEZIER_CURVE_COMMAND_ABSOLUTE = 'C';
  private static final char QUADRATIC_CURVE_COMMAND_ABSOLUTE = 'Q';
  private static final char CLOSE_PATH_COMMAND = 'z';

  private StringBuilder stringBuilder;

  public VectorPathBuilder() {
    stringBuilder = new StringBuilder();
  }

  public StringBuilder getStringBuilder() {
    return stringBuilder;
  }

  public String getVectorString() {
    return stringBuilder.toString();
  }

  public void createBezierCurve(@NotNull StringPoint firstControlPoint,
                                @NotNull StringPoint secondControlPoint,
                                @NotNull StringPoint endPoint) {
    appendCommand(BEZIER_CURVE_COMMAND_ABSOLUTE);
    appendPointCoordinates(firstControlPoint);
    appendPointCoordinates(secondControlPoint);
    appendPointCoordinates(endPoint);
  }

  public void createQuadCurve(@NotNull StringPoint controlPoint, @NotNull StringPoint endPoint) {
    appendCommand(QUADRATIC_CURVE_COMMAND_ABSOLUTE);
    appendPointCoordinates(controlPoint);
    appendPointCoordinates(endPoint);
  }

  public void createHorizontalLine(@NotNull StringPoint endPoint) {
    appendCommandAndCoordinate(HORIZONTAL_LINE_COMMAND_ABSOLUTE, endPoint.getX());
  }

  public void createVerticalLine(@NotNull StringPoint endPoint) {
    appendCommandAndCoordinate(VERTICAL_LINE_COMMAND_ABSOLUTE, endPoint.getY());
  }

  public void createLine(@NotNull StringPoint endPoint) {
    appendCommand(LINE_COMMAND_ABSOLUTE);
    appendPointCoordinates(endPoint);
  }

  public void startPath(@NotNull StringPoint startPoint) {
    appendCommand(MOVE_CURSOR_COMMAND_ABSOLUTE);
    appendPointCoordinates(startPoint);
  }

  public void startPath(@NotNull Point2D.Double startPoint) {
    appendCommand(MOVE_CURSOR_COMMAND_ABSOLUTE);
    appendPointCoordinates(new StringPoint(startPoint));
  }

  public void endPath() {
    appendCommand(CLOSE_PATH_COMMAND);
  }

  public void closeOvalShape(SketchShapePath shapePath, SketchCurvePoint currentPoint, Rectangle2D.Double parentFrame) {
    StringPoint currentPointCurveFromCoords =
      makeAbsolutePositionString(percentagesToRelativePosition(currentPoint.getCurveFrom(), shapePath.getFrame()),
                                 parentFrame,
                                 shapePath.getFrame());
    StringPoint firstPointCurveToCoords =
      makeAbsolutePositionString(percentagesToRelativePosition(shapePath.getPoints()[0].getCurveTo(), shapePath.getFrame()),
                                 parentFrame,
                                 shapePath.getFrame());
    StringPoint firstPointCoords =
      makeAbsolutePositionString(percentagesToRelativePosition(shapePath.getPoints()[0].getPoint(), shapePath.getFrame()),
                                 parentFrame,
                                 shapePath.getFrame());
    createBezierCurve(currentPointCurveFromCoords, firstPointCurveToCoords, firstPointCoords);
  }

  private void appendCommand(char command) {
    stringBuilder.append(command);
  }

  private void appendPointCoordinates(StringPoint coords) {
    stringBuilder.append(coords.getX()).append(",").append(coords.getY()).append(" ");
  }

  private void appendCommandAndCoordinate(char command, String coord) {
    stringBuilder.append(command).append(coord).append(" ");
  }
}
