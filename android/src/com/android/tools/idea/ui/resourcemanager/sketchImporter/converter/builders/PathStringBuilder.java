/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.builders;

import java.text.DecimalFormat;
import org.jetbrains.annotations.NotNull;

public class PathStringBuilder {

  private static final char MOVE_CURSOR_COMMAND_ABSOLUTE = 'M';
  private static final char LINE_COMMAND_ABSOLUTE = 'L';
  private static final char BEZIER_CURVE_COMMAND_ABSOLUTE = 'C';
  private static final char QUADRATIC_CURVE_COMMAND_ABSOLUTE = 'Q';
  private static final char CLOSE_PATH_COMMAND = 'z';

  // Trims the number to 2 decimals. Used for formatting it as a string
  // To change the number of decimals displayed, add or remove hashes after
  // the dot.
  // If integers are needed, remove the dot as well.
  private static final String COORDINATES_PRECISION = "#.##";

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
    stringBuilder.append(trimDoubles(x)).append(",").append(trimDoubles(y)).append(" ");
  }

  /**
   * Method that trims the doubles to a specified precision. DecimalFormat is used
   * instead of the String.format(..) because DecimalFormat can detect triailing zeros
   * in decimals and remove them, while String.format simply displays the zeros as well.
   *
   * @param number
   * @return trimmed number formatted as a String.
   */
  @NotNull
  private static String trimDoubles(double number) {
    DecimalFormat df = new DecimalFormat(COORDINATES_PRECISION);
    return df.format(number);
  }
}
