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

public class DrawableShape {

  @NotNull private String name;
  @NotNull private String pathData;
  @NotNull private String fillColor;
  @NotNull private String strokeColor;
  @NotNull private String strokeWidth;

  public DrawableShape(@Nullable String nameParam,
                       @NotNull String pathDataParam,
                       @Nullable String fillColorParam,
                       @Nullable String strokeColorParam,
                       @Nullable String strokeWidthParam) {
    pathData = pathDataParam;
    name = nameParam == null ? "" : nameParam;
    fillColor = fillColorParam == null ? "" : fillColorParam;
    strokeColor = strokeColorParam == null ? "" : strokeColorParam;
    strokeWidth = strokeWidthParam == null ? "" : strokeWidthParam;
  }

  @NotNull
  public String getName() {
    return name;
  }

  @NotNull
  public String getPathData() {
    return pathData;
  }

  @NotNull
  public String getFillColor() {
    return fillColor;
  }

  @NotNull
  public String getStrokeColor() {
    return strokeColor;
  }

  @NotNull
  public String getStrokeWidth() {
    return strokeWidth;
  }
}
