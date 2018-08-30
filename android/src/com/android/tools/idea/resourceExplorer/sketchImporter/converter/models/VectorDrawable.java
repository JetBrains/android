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

import com.android.tools.idea.resourceExplorer.sketchImporter.converter.models.DrawableModel;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchArtboard;
import com.android.tools.layoutlib.annotations.NotNull;

import java.awt.*;
import java.util.List;

public class VectorDrawable {

  @NotNull private final List<DrawableModel> myDrawableModels;
  @NotNull private final Rectangle.Double myArtboardDimension;
  @NotNull private final Rectangle.Double myViewportDimension;
  @NotNull private String myName;

  public VectorDrawable(@NotNull SketchArtboard artboard) {
    myDrawableModels = artboard.createAllDrawableShapes();
    myViewportDimension = myArtboardDimension = artboard.getFrame();
    myName = artboard.getName();
  }

  public double getArtboardHeight() {
    return myArtboardDimension.getHeight();
  }

  public double getArtboardWidth() {
    return myArtboardDimension.getWidth();
  }

  public double getViewportHeight() {
    return myViewportDimension.getHeight();
  }

  public double getViewportWidth() {
    return myViewportDimension.getWidth();
  }

  @NotNull
  public List<DrawableModel> getDrawableModels() {
    return myDrawableModels;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  public void setName(@NotNull String name) {
    myName = name;
  }
}