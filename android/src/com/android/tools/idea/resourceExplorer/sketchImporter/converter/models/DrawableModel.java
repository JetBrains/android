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

import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchGradient;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchGraphicContextSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DrawableModel {

  @NotNull private String pathData;
  private int fillColor;
  @Nullable private SketchGradient gradient;
  private int strokeColor;
  @Nullable private String strokeWidth;
  @Nullable private SketchGraphicContextSettings graphicContextSettings;
  private boolean isClipPath;
  private boolean breaksMaskChain;
  private boolean isLastShape;

  public DrawableModel(@NotNull String pathDataParam,
                       int fillColorParam,
                       @Nullable SketchGraphicContextSettings graphicContextSettingsParam,
                       @Nullable SketchGradient gradientParam,
                       int strokeColorParam,
                       @Nullable String strokeWidthParam,
                       boolean isClipPathParam,
                       boolean breaksMaskChainParam,
                       boolean isLastShapeParam) {
    pathData = pathDataParam;
    fillColor = fillColorParam;
    graphicContextSettings = graphicContextSettingsParam;
    strokeColor = strokeColorParam;
    strokeWidth = strokeWidthParam;
    gradient = gradientParam;
    isClipPath = isClipPathParam;
    breaksMaskChain = breaksMaskChainParam;
    isLastShape = isLastShapeParam;
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

  @Nullable
  public SketchGraphicContextSettings getGraphicContextSettings() {
    return graphicContextSettings;
  }

  /**
   * Method that checks if the model should be used to mask other shapes in the drawable file.
   * Used for generating XML clipping groups.
   *
   * @return true if the DrawableModel is a sketch clipping mask
   */
  public boolean isClipPath() {
    return isClipPath;
  }

  /**
   * There can be cases when a group has multiple clipped shapes, along with shapes that
   * are not clipped. The first shape that follows a chain of masked shapes and is not
   * masked like the previous shapes is known to 'break the mask chain'
   *
   * @return true if the DrawableModel breaks the chain of masked shapes in its group.
   */
  public boolean breaksMaskChain() {
    return breaksMaskChain;
  }

  /**
   * Because the hierarchy of shapes and groups is gone after generating the models,
   * shapes that follow a clipping group and are not included in it do not necessarily
   * break the mask chain, but they were simply placed in a different group in the SketchFile.
   * Method is used to correctly close any needed groups and add the DrawableModel to root.
   *
   * @return true if the DrawableModel is the last shape in the SketchPage's list of layers
   */
  public boolean isLastShape() {
    return isLastShape;
  }
}
