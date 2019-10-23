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
package com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models;

import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchGradient;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchPoint2D;
import com.intellij.openapi.diagnostic.Logger;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Class that holds the intermediate model for the gradient of a ShapeModel. Needed for modifying its opacity,
 * transformation and start/end/center coordinates without affecting the SketchModel.
 */
public class GradientModel {
  public static final Logger LOG = Logger.getInstance(SketchGradient.class);
  public static final String GRADIENT_LINEAR = "linear";
  public static final String GRADIENT_RADIAL = "radial";
  public static final String GRADIENT_SWEEP = "sweep";
  /**
   * The index in the array corresponds to each of the gradient types
   */
  private static final String[] TYPES = new String[]{GRADIENT_LINEAR, GRADIENT_RADIAL, GRADIENT_SWEEP};

  private int myGradientType;
  @NotNull private SketchPoint2D myFrom;
  @NotNull private SketchPoint2D myTo;
  @NotNull private GradientStopModel[] myGradientStopModels;

  public GradientModel(int gradientType,
                       @NotNull SketchPoint2D from,
                       @NotNull SketchPoint2D to,
                       @NotNull GradientStopModel[] gradientStopModels) {
    myGradientType = gradientType;
    myFrom = from;
    myTo = to;
    myGradientStopModels = gradientStopModels;
  }

  public int getGradientType() {
    return myGradientType;
  }

  @NotNull
  public SketchPoint2D getFrom() {
    return myFrom;
  }

  @NotNull
  public SketchPoint2D getTo() {
    return myTo;
  }

  @NotNull
  public GradientStopModel[] getGradientStopModels() {
    return myGradientStopModels;
  }

  @Nullable
  public String getDrawableGradientType() {
    if (myGradientType >= 0 && myGradientType < TYPES.length) {
      return TYPES[getGradientType()];
    }
    else {
      LOG.error("Unknown gradient type. Array index is " + myGradientType);
    }

    return null;
  }

  @NotNull
  public String getGradientEndX() {
    return Double.toString(myTo.getX());
  }

  @NotNull
  public String getGradientEndY() {
    return Double.toString(myTo.getY());
  }

  @NotNull
  public String getGradientStartX() {
    return Double.toString(myFrom.getX());
  }

  @NotNull
  public String getGradientStartY() {
    return Double.toString(myFrom.getY());
  }

  @NotNull
  public String getGradientRadius() {
    double radiusX = Math.pow(myTo.x - myFrom.x, 2);
    double radiusY = Math.pow(myTo.y - myFrom.y, 2);

    return Double.toString(Math.sqrt(radiusX + radiusY));
  }

  @NotNull
  public String getSweepCenterY() {
    return String.valueOf((myTo.y + myFrom.y) / 2);
  }

  public void toRelativeGradient(@NotNull Rectangle2D ownFrame) {
    myFrom = myFrom.makeAbsolutePosition(ownFrame);
    myTo = myTo.makeAbsolutePosition(ownFrame);
  }

  public void applyTransformation(@NotNull AffineTransform transformation) {
    Point2D[] origin = {myFrom, myTo};
    Point2D[] newPoints = new Point2D[2];
    transformation.transform(origin, 0, newPoints, 0, 2);
    myFrom.setLocation(newPoints[0]);
    myTo.setLocation(newPoints[1]);
  }
}
