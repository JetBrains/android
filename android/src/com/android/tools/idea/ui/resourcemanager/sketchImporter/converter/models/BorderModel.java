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

import static com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.InheritedProperties.DEFAULT_OPACITY;

import java.awt.Color;
import org.jetbrains.annotations.NotNull;

/**
 * Class that holds the intermediate model for the border of a ShapeModel. Needed for modifying its opacity
 * without affecting the SketchModel.
 */
public class BorderModel implements TransparentModel {
  private int myWidth;
  @NotNull private Color myColor;

  public BorderModel(int width, @NotNull Color color) {
    myWidth = width;
    myColor = color;
  }

  public int getWidth() {
    return myWidth;
  }

  @NotNull
  public Color getColor() {
    return myColor;
  }

  @Override
  public double getOpacity() {
    return InheritedProperties.DEFAULT_OPACITY;
  }

  @Override
  public void applyOpacity(double opacity) {
    myColor = StyleModel.addAlphaToColor(getColor(), opacity);
  }
}