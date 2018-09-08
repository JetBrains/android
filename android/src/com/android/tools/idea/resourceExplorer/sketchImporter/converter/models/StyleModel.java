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
package com.android.tools.idea.resourceExplorer.sketchImporter.converter.models;

import java.awt.Color;
import java.awt.Shape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Class that holds the intermediate model for the style of a ShapeModel. Needed for modifying its opacity
 * and gradient without affecting the SketchModel.
 */
public class StyleModel implements TransparentModel{
  @Nullable private FillModel myFillModel;
  @Nullable private BorderModel myBorderModel;
  private double myOpacity;

  public StyleModel(@Nullable FillModel fillModel, @Nullable BorderModel borderModel, double opacity) {
    myBorderModel = borderModel;
    myFillModel = fillModel;
    myOpacity = opacity;
  }

  @Nullable
  public FillModel getFill() {
    return myFillModel;
  }

  @Nullable
  public BorderModel getBorder() {
    return myBorderModel;
  }

  @Override
  public double getOpacity() {
    return myOpacity;
  }

  /**
   * This method transforms the gradient's coordinates from percentages to coordinates
   * relative to the shape itself. The coordinates, however, are not absolute because the
   * shape might need extra translations.
   *
   * @param shape
   */
  public void makeGradientRelative(@NotNull Shape shape) {
    GradientModel shapeGradient = myFillModel != null ? myFillModel.getGradientModel() : null;
    if (shapeGradient != null) {
      shapeGradient.toRelativeGradient(shape.getBounds2D());
    }
  }

  /**
   * This method applies the overall opacity of the group on its fill and border, by modifying
   * the existing {@link StyleModel} object, or creating a new one, if it doesn't exist
   *
   * @param parentOpacity
   */
  @Override
  public void applyOpacity(double parentOpacity) {
    myOpacity *= parentOpacity;

    if (myFillModel != null) {
      myFillModel.applyOpacity(myOpacity);
    }
    if (myBorderModel != null) {
      myBorderModel.applyOpacity(myOpacity);
    }
  }

  @NotNull
  protected static Color addAlphaToColor(@NotNull Color color, double opacity) {
    //noinspection UseJBColor
    return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(color.getAlpha() * opacity));
  }
}
