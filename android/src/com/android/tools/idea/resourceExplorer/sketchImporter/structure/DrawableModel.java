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
import org.jetbrains.annotations.Nullable;

public class DrawableModel {

  @NotNull private String pathData;
  private int fillColor;
  @Nullable private SketchGradient gradient;
  private int strokeColor;
  @Nullable private String strokeWidth;

  public DrawableModel(@NotNull String pathDataParam,
                       int fillColorParam,
                       @Nullable SketchGradient gradientParam,
                       int strokeColorParam,
                       @Nullable String strokeWidthParam) {
    pathData = pathDataParam;
    fillColor = fillColorParam;
    strokeColor = strokeColorParam;
    strokeWidth = strokeWidthParam;
    gradient = gradientParam;
  }

  @NotNull
  public String getPathData() {
    return pathData;
  }

  public int getFillColor() {
    return fillColor;
  }

  public int getStrokeColor() {
    return strokeColor;
  }

  @Nullable
  public String getStrokeWidth() {
    return strokeWidth;
  }

  @Nullable
  public SketchGradient getGradient() {
    return gradient;
  }
}
