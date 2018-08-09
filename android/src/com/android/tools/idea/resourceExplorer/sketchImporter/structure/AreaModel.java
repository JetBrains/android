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

import com.android.tools.idea.resourceExplorer.sketchImporter.structure.interfaces.SketchLayer;
import com.android.tools.layoutlib.annotations.NotNull;

import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Point2D;

public class AreaModel extends ShapeModel {

  private final Area area;

  public AreaModel(Area shape,
                   SketchFill fill,
                   SketchBorder border,
                   boolean flippedHorizontal,
                   boolean flippedVertical,
                   boolean closed,
                   int rotation,
                   int operation,
                   Point2D.Double framePosition) {
    super(shape, fill, border, flippedHorizontal, flippedVertical, closed, rotation, operation, framePosition);
    area = shape;
  }

  @NotNull
  public Area getModelArea() {
    return area;
  }

  private void addShape(AreaModel model) {
    area.add(model.getModelArea());
  }

  private void subtractShape(AreaModel model) {
    area.subtract(model.getModelArea());
  }

  private void differenceShape(AreaModel model) {
    area.exclusiveOr(model.getModelArea());
  }

  private void intersectShape(AreaModel model) {
    area.intersect(model.getModelArea());
  }

  public void applyOperation(AreaModel model) {
    int booleanOperation = model.getBooleanOperation();
    switch (booleanOperation) {
      case SketchLayer.BOOLEAN_OPERATION_UNION:
        addShape(model);
        break;
      case SketchLayer.BOOLEAN_OPERATION_SUBSTRACTION:
        subtractShape(model);
        break;
      case SketchLayer.BOOLEAN_OPERATION_DIFFERENCE:
        differenceShape(model);
        break;
      case SketchLayer.BOOLEAN_OPERATION_INTERSECTION:
        intersectShape(model);
        break;
    }
  }

  @Override
  public void applyTransformations() {
    AffineTransform transform = computeAffineTransform();

    area.transform(transform);

    if (shapeGradient != null) {
      shapeGradient.applyTransformation(transform);
    }
  }
}
