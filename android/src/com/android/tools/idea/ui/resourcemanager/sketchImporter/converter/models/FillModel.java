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

import java.awt.Color;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Class that holds the intermediate model for the fill of a ShapeModel. Needed for modifying its opacity,
 * gradient position and transformation without affecting the SketchModel.
 */
public class FillModel implements TransparentModel {
  @NotNull private Color myColor;
  @Nullable private GradientModel myGradientModel;
  private double myOpacity;

  public FillModel(@NotNull Color color, @Nullable GradientModel gradientModel, double opacity) {
    myColor = color;
    myGradientModel = gradientModel;
    myOpacity = opacity;
  }

  @NotNull
  public Color getColor() {
    return myColor;
  }

  @Nullable
  public GradientModel getGradientModel() {
    return myGradientModel;
  }

  @Override
  public double getOpacity() {
    return myOpacity;
  }

  @Override
  public void applyOpacity(double opacity) {
    myOpacity *= opacity;

    GradientModel gradient = getGradientModel();
    if (gradient != null) {
      GradientStopModel[] stopModels = gradient.getGradientStopModels();
      for (GradientStopModel gradientStop : stopModels) {
        gradientStop.setColor(StyleModel.addAlphaToColor(gradientStop.getColor(), myOpacity));
      }
    }
    else {
      myColor = StyleModel.addAlphaToColor(myColor, myOpacity);
    }
  }
}
