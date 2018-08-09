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

import org.jetbrains.annotations.NotNull;

public class PathStringBuilder {

  //TODO Take into consideration max string length: 32,767 characters
  private static final char MOVE_CURSOR_COMMAND_ABSOLUTE = 'M';
  private static final char LINE_COMMAND_ABSOLUTE = 'L';
  private static final char BEZIER_CURVE_COMMAND_ABSOLUTE = 'C';
  private static final char QUADRATIC_CURVE_COMMAND_ABSOLUTE = 'Q';
  private static final char CLOSE_PATH_COMMAND = 'z';

  @NotNull
  private StringBuilder stringBuilder;

  public PathStringBuilder() {
    stringBuilder = new StringBuilder();
  }

  @NotNull
  public String build() {
    return stringBuilder.toString();
  }

  public void createBezierCurve(@NotNull double[] coordinates) {
    appendCommand(BEZIER_CURVE_COMMAND_ABSOLUTE);
    appendPointCoordinates(coordinates[0], coordinates[1]);
    appendPointCoordinates(coordinates[2], coordinates[3]);
    appendPointCoordinates(coordinates[4], coordinates[5]);
  }

  public void createQuadCurve(double controlPointX,
                              double controlPointY,
                              double endPointX,
                              double endPointY) {
    appendCommand(QUADRATIC_CURVE_COMMAND_ABSOLUTE);
    appendPointCoordinates(controlPointX, controlPointY);
    appendPointCoordinates(endPointX, endPointY);
  }

  public void createLine(double x, double y) {
    appendCommand(LINE_COMMAND_ABSOLUTE);
    appendPointCoordinates(x, y);
  }

  public void startPath(double x, double y) {
    appendCommand(MOVE_CURSOR_COMMAND_ABSOLUTE);
    appendPointCoordinates(x, y);
  }

  public void endPath() {
    appendCommand(CLOSE_PATH_COMMAND);
  }

  private void appendCommand(char command) {
    stringBuilder.append(command);
  }

  private void appendPointCoordinates(double x, double y) {
    stringBuilder.append(String.valueOf(x)).append(",").append(String.valueOf(y)).append(" ");
  }
}
