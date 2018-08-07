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
package com.android.tools.idea.resourceExplorer.sketchImporter.structure;

import org.jetbrains.annotations.NotNull;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

public class SketchGradient {
  private final int elipseLength;
  private final SketchPoint2D from;
  /**
   * Linear: 0
   * Radial: 1
   * Angular: 2
   */
  private final int gradientType;
  private final SketchGradientStop[] stops;
  private final SketchPoint2D to;

  public static final String GRADIENT_LINEAR = "linear";
  public static final String GRADIENT_RADIAL = "radial";
  public static final String GRADIENT_SWEEP = "sweep";
  /**
   * The index in the array corresponds to each of the gradient types
   */
  private static final String[] TYPES = new String[]{GRADIENT_LINEAR, GRADIENT_RADIAL, GRADIENT_SWEEP};

  public SketchGradient(int elipseLength,
                        @NotNull SketchPoint2D from,
                        int gradientType,
                        @NotNull SketchGradientStop[] stops,
                        @NotNull SketchPoint2D to) {
    this.elipseLength = elipseLength;
    this.from = from;
    this.gradientType = gradientType;
    this.stops = stops;
    this.to = to;
  }

  public int getElipseLength() {
    return elipseLength;
  }

  @NotNull
  public SketchPoint2D getFrom() {
    return from;
  }

  public int getGradientType() {
    return gradientType;
  }

  public String getDrawableGradientType() {
    return TYPES[getGradientType()];
  }

  @NotNull
  public SketchGradientStop[] getStops() {
    return stops;
  }

  @NotNull
  public SketchPoint2D getTo() {
    return to;
  }

  @NotNull
  public String getGradientEndX() {
    return Double.toString(to.getX());
  }

  @NotNull
  public String getGradientEndY() {
    return Double.toString(to.getY());
  }

  @NotNull
  public String getGradientStartX() {
    return Double.toString(from.getX());
  }

  @NotNull
  public String getGradientStartY() {
    return Double.toString(from.getY());
  }

  @NotNull
  public String getGradientRadius() {
    double radiusX = Math.pow(to.x - from.x, 2);
    double radiusY = Math.pow(to.y - from.y, 2);

    return Double.toString(Math.sqrt(radiusX + radiusY));
  }

  @NotNull
  public String getSweepCenterY() {
    return String.valueOf((to.y + from.y) / 2);
  }

  /**
   * This method does not modify the current object but creates a new instance with coordinates in its parent coordinate system.
   **/
  public SketchGradient toAbsoluteGradient(Point2D.Double parentCoords, Rectangle2D.Double ownFrame) {
    Rectangle2D.Double parentFrame = new Rectangle2D.Double(parentCoords.getX(),
                                                            parentCoords.getY(),
                                                            ownFrame.getWidth(),
                                                            ownFrame.getHeight());
    return new SketchGradient(elipseLength,
                              from.makeAbsolutePosition(parentFrame, ownFrame),
                              gradientType,
                              stops,
                              to.makeAbsolutePosition(parentFrame, ownFrame));
  }
}
