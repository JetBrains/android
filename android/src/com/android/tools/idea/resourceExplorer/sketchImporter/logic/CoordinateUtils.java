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
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;

public class CoordinateUtils {

  public static StringPoint calculateAbsolutePositionString(@NotNull SketchCurvePoint point, @NotNull Rectangle.Double frame) {

    Point2D.Double coordsPoint = point.getPoint();
    return calculateAbsolutePositionString(coordsPoint, frame);
  }

  public static StringPoint calculateAbsolutePositionString(@NotNull Point2D.Double percentages,
                                                            @NotNull Rectangle.Double frame) {

    return new StringPoint(Double.toString(percentages.getX() * frame.getWidth() + frame.getX()),
                           Double.toString(percentages.getY() * frame.getHeight() + frame.getY()));
  }

  public static ArrayList<StringPoint> calculateStringFrameCorners(@NotNull Rectangle.Double frame) {

    ArrayList<StringPoint> corners = new ArrayList<>();

    corners.add(new StringPoint(Double.toString(frame.getX()), Double.toString(frame.getY())));
    corners.add(new StringPoint(Double.toString(frame.getWidth() + frame.getX()), Double.toString(frame.getY())));
    corners.add(new StringPoint(Double.toString(frame.getWidth() + frame.getX()), Double.toString(frame.getHeight() + frame.getY())));
    corners.add(new StringPoint(Double.toString(frame.getX()), Double.toString(frame.getHeight() + frame.getY())));

    return corners;
  }
}
