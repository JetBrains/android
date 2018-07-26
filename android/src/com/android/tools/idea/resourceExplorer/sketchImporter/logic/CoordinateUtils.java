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

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;

public class CoordinateUtils {

  public static Point2D.Double percentagesToRelativePosition(@NotNull Point2D.Double percentage,
                                                             @NotNull Rectangle.Double frame) {
    return new Point2D.Double(percentage.getX() * frame.getWidth(),
                              percentage.getY() * frame.getHeight());
  }

  public static StringPoint makeAbsolutePositionString(@NotNull StringPoint relativePoint,
                                                       @NotNull Rectangle.Double shapeGroupFrame,
                                                       @NotNull Rectangle.Double ownFrame) {
    return new StringPoint(Double.toString(Double.parseDouble(relativePoint.getX()) + shapeGroupFrame.getX() + ownFrame.getX()),
                           Double.toString(Double.parseDouble(relativePoint.getY()) + shapeGroupFrame.getY() + ownFrame.getY()));
  }

  public static StringPoint makeAbsolutePositionString(@NotNull Point2D.Double relativePoint,
                                                       @NotNull Rectangle.Double shapeGroupFrame,
                                                       @NotNull Rectangle.Double ownFrame) {
    return new StringPoint(Double.toString(relativePoint.getX() + shapeGroupFrame.getX() + ownFrame.getX()),
                           Double.toString(relativePoint.getY() + shapeGroupFrame.getY() + ownFrame.getY()));
  }


  public static Point2D.Double makeAbsolutePositionDouble(@NotNull Point2D.Double relativePoint,
                                                          @NotNull Rectangle.Double shapeGroupFrame,
                                                          @NotNull Rectangle.Double ownFrame) {
    return new Point2D.Double(relativePoint.getX() + shapeGroupFrame.getX() + ownFrame.getX(),
                              relativePoint.getY() + shapeGroupFrame.getY() + ownFrame.getY());
  }

  public static Point2D.Double makeAbsolutePositionDoubleRectangle(@NotNull Point2D.Double relativePoint,
                                                                   @NotNull Rectangle.Double shapeGroupFrame) {
    return new Point2D.Double(relativePoint.getX() + shapeGroupFrame.getX(),
                              relativePoint.getY() + shapeGroupFrame.getY());
  }

  public static ArrayList<StringPoint> calculateStringFrameCorners(@NotNull Rectangle.Double rectangleFrame,
                                                                   @NotNull Rectangle.Double shapeGroupFrame) {

    ArrayList<StringPoint> corners = new ArrayList<>();

    Point2D.Double upLeftCorner =
      makeAbsolutePositionDoubleRectangle(new Point2D.Double(rectangleFrame.getX(), rectangleFrame.getY()), shapeGroupFrame);
    Point2D.Double upRightCorner =
      makeAbsolutePositionDoubleRectangle(new Point2D.Double(rectangleFrame.getX() + rectangleFrame.getWidth(), rectangleFrame.getY()),
                                          shapeGroupFrame);
    Point2D.Double downRightCorner = makeAbsolutePositionDoubleRectangle(
      new Point2D.Double(rectangleFrame.getX() + rectangleFrame.getWidth(), rectangleFrame.getY() + rectangleFrame.getHeight()),
      shapeGroupFrame);
    Point2D.Double downLeftCorner =
      makeAbsolutePositionDoubleRectangle(new Point2D.Double(rectangleFrame.getX(), rectangleFrame.getY() + rectangleFrame.getHeight()),
                                          shapeGroupFrame);

    corners.add(new StringPoint(upLeftCorner));
    corners.add(new StringPoint(upRightCorner));
    corners.add(new StringPoint(downRightCorner));
    corners.add(new StringPoint(downLeftCorner));

    return corners;
  }
}
