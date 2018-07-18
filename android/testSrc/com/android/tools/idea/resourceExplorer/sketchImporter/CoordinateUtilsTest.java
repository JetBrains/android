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
package com.android.tools.idea.resourceExplorer.sketchImporter;

import com.android.tools.idea.resourceExplorer.sketchImporter.logic.StringPoint;
import com.android.tools.idea.resourceExplorer.sketchImporter.logic.VectorPathBuilder;
import com.android.utils.Pair;
import org.junit.Test;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;

import static com.android.tools.idea.resourceExplorer.sketchImporter.logic.CoordinateUtils.*;
import static org.junit.Assert.*;

public class CoordinateUtilsTest {

  @Test
  public void calculateAbsolutePositionStringTest() {
    Point2D.Double percentages = new Point2D.Double(0.5, 0.5);
    Rectangle.Double frame = new Rectangle.Double(2, 1, 10, 20);

    assertEquals("7.0", calculateAbsolutePositionString(percentages, frame).getX());
    assertEquals("11.0", calculateAbsolutePositionString(percentages, frame).getY());
  }

  @Test
  public void appendPointCoordinatesTest() {

    VectorPathBuilder vectorPathBuilder = new VectorPathBuilder();

    StringPoint coordinates = new StringPoint("7.0", "11.0");

    vectorPathBuilder.appendPointCoordinates(coordinates);

    assertEquals("7.0,11.0 ", vectorPathBuilder.getString());
  }

  @Test
  public void appendCommandAndCoordinatesTest() {

    char command = 'M';
    StringPoint coordinates = new StringPoint("7.0", "11.0");
    VectorPathBuilder vectorPathBuilder = new VectorPathBuilder();

    vectorPathBuilder.appendCommandAndCoordinates(command, coordinates);

    assertEquals("M7.0,11.0 ", vectorPathBuilder.getString());
  }

  @Test
  public void appendCommandAndCoordinateTest() {

    char command = 'M';
    String coordinate = "7.0";
    VectorPathBuilder vectorPathBuilder = new VectorPathBuilder();

    vectorPathBuilder.appendCommandAndCoordinate(command, coordinate);

    assertEquals("M7.0 ", vectorPathBuilder.getString());
  }

  @Test
  public void calculateStringFrameCornersTest() {
    ArrayList<StringPoint> corners = new ArrayList<>();

    corners.add(new StringPoint("1.0", "5.0"));
    corners.add(new StringPoint("21.0", "5.0"));
    corners.add(new StringPoint("21.0", "15.0"));
    corners.add(new StringPoint("1.0", "15.0"));

    Rectangle.Double frame = new Rectangle.Double(1, 5, 20, 10);

    ArrayList<StringPoint> resultCorners = calculateStringFrameCorners(frame);

    for(int i = 0; i < resultCorners.size(); i++){
      assertEquals(corners.get(i).getX(), resultCorners.get(i).getX());
      assertEquals(corners.get(i).getY(), resultCorners.get(i).getY());
    }
  }
}
