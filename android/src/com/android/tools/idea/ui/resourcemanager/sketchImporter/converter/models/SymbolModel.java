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

import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchSymbolInstance;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchSymbolMaster;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchSymbolMaster;
import com.google.common.collect.ImmutableList;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import org.jetbrains.annotations.NotNull;

/**
 * Intermediate model that holds information about the symbol master, instance
 * and the {@link ShapeModel} objects it consists of.
 * <p>
 * Used for customizing the symbol instances through transformations or opacity,
 * by applying them on each {@code ShapeModel} in the list.
 */
public class SymbolModel {
  @NotNull ImmutableList<ShapeModel> myShapeModels;
  @NotNull SketchSymbolMaster mySymbolMaster;
  @NotNull SketchSymbolInstance mySymbolInstance;

  public SymbolModel(@NotNull ImmutableList<ShapeModel> shapeModels, @NotNull SketchSymbolMaster symbolMaster) {
    myShapeModels = shapeModels;
    mySymbolMaster = symbolMaster;
  }

  /**
   * Applies the opacity and transformation properties from the {@link InheritedProperties} given
   * as parameter to the {@code ShapeModel}s inside the {@code SymbolModel}
   */
  public void applyProperties(@NotNull InheritedProperties properties) {
    for (ShapeModel shapeModel : myShapeModels) {
      shapeModel.applyOpacity(properties.getInheritedOpacity());
      shapeModel.applyTransformations(properties);
    }
  }

  @NotNull
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

  /**
   * Scales the {@link ShapeModel}s inside to fit the {@link SketchSymbolInstance} dimensions
   * instead of the {@link SketchSymbolMaster}.
   */
  public void scaleShapes() {
    if (isInstanceScaled()) {
      for (ShapeModel shapeModel : myShapeModels) {
        ResizingConstraint constraint = shapeModel.getResizingConstraint();
        if (constraint.isNoConstraint()) {
          shapeModel.scale(getScaleRatioX(), getScaleRatioY());
        }
        else if (constraint.isOnlyConstraintWidth()) {
          shapeModel.scale(1, getScaleRatioY());
        }
        else if (constraint.isOnlyConstraintHeight()) {
          shapeModel.scale(getScaleRatioX(), 1);
        }

        // If any of the shapes has a constraint enabled, it will not be affected by the scaling,
        // but its position will not be correct relative to the new size of the symbol, therefore
        // it needs to be translated to a coordinate that has been scaled acordingly.
        AffineTransform transform = new AffineTransform();
        transform.scale(getScaleRatioX(), getScaleRatioY());
        Point2D.Double newPoint = new Point2D.Double();
        transform.transform(shapeModel.getShapeFrameLocation(), newPoint);
        shapeModel.translateTo(newPoint.getX(), newPoint.getY());
      }
    }
  }
}