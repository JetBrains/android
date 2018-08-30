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

import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchSymbolInstance;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchSymbolMaster;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

/**
 * Intermediate model that holds information about the symbol master, instance
 * and the {@link ShapeModel} objects it consists of.
 * <p>
 * Used for customizing the symbol instances through transformations or opacity,
 * by applying them on each {@link ShapeModel} in the list.
 */
public class SymbolModel {
  ImmutableList<ShapeModel> myShapeModels;
  SketchSymbolMaster mySymbolMaster;
  SketchSymbolInstance mySymbolInstance;

  public SymbolModel(@NotNull ImmutableList<ShapeModel> shapeModels, @NotNull SketchSymbolMaster symbolMaster) {
    myShapeModels = shapeModels;
    mySymbolMaster = symbolMaster;
  }

  public void scaleShapes() {
    if (isInstanceScaled()) {
      for (ShapeModel shapeModel : myShapeModels) {
        shapeModel.scale(getScaleRatioX(), getScaleRatioY());
      }
    }
  }

  public void translateShapes() {
    for (ShapeModel shapeModel : myShapeModels) {
      shapeModel.translate(mySymbolInstance.getFrame().getX(), mySymbolInstance.getFrame().getY());
    }
  }

  public ImmutableList<ShapeModel> getShapeModels() {
    return myShapeModels;
  }

  private double getScaleRatioX() {
    return mySymbolInstance.getFrame().getWidth() / mySymbolMaster.getFrame().getWidth();
  }

  private double getScaleRatioY() {
    return mySymbolInstance.getFrame().getHeight() / mySymbolMaster.getFrame().getHeight();
  }

  private boolean isInstanceScaled() {
    return (Double.compare(getScaleRatioX(), 1) != 0 || Double.compare(getScaleRatioY(), 1) != 0);
  }

  public void setSymbolInstance(@NotNull SketchSymbolInstance symbolInstance) {
    mySymbolInstance = symbolInstance;
  }
}
