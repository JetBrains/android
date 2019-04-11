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

import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.interfaces.SketchLayer;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.interfaces.SketchLayer;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Class that extends from {@link ShapeModel} and contains functionality for the transformations
 * and operations of the {@code AreaModel}
 */
public class AreaModel extends ShapeModel {

  @NotNull
  private final Area myArea;

  public AreaModel(@NotNull Area shape,
                   @Nullable StyleModel style,
                   boolean flippedHorizontal,
                   boolean flippedVertical,
                   boolean closed,
                   int rotation,
                   int operation,
                   @NotNull Point2D.Double framePosition,
                   boolean hasClippingMask,
                   boolean shouldBreakMaskChain,
                   boolean isLastShapeGroup,
                   @NotNull ResizingConstraint constraint) {
    super(shape, style, flippedHorizontal, flippedVertical, closed, rotation, operation, framePosition, hasClippingMask,
          shouldBreakMaskChain, isLastShapeGroup, constraint);
    myArea = shape;
  }

  @NotNull
  public Area getModelArea() {
    return myArea;
  }

  private void addShape(@NotNull AreaModel model) {
    myArea.add(model.getModelArea());
  }

  private void subtractShape(@NotNull AreaModel model) {
    myArea.subtract(model.getModelArea());
  }

  private void differenceShape(@NotNull AreaModel model) {
    myArea.exclusiveOr(model.getModelArea());
  }

  private void intersectShape(@NotNull AreaModel model) {
    myArea.intersect(model.getModelArea());
  }

  /**
   * Takes an {@code AreaModel} and applies the appropriate operation between the given {@code AreaModel}
   * and the {@code this}
   */
  public void applyOperation(@NotNull AreaModel model) {
    int booleanOperation = model.getBooleanOperation();
    switch (booleanOperation) {
      case SketchLayer.BOOLEAN_OPERATION_UNION:
        addShape(model);
        break;
      case SketchLayer.BOOLEAN_OPERATION_SUBTRACTION:
        subtractShape(model);
        break;
      case SketchLayer.BOOLEAN_OPERATION_DIFFERENCE:
      case SketchLayer.BOOLEAN_OPERATION_NONE:
        differenceShape(model);
        break;
      case SketchLayer.BOOLEAN_OPERATION_INTERSECTION:
        intersectShape(model);
        break;
    }
  }

  /**
   * Takes an {@link InheritedProperties} object, computes the appropriate {@link AffineTransform} and applies it
   * to the area. Also applies opacity from the {@code InheritedProperties} object
   */
  @Override
  public void applyTransformations(@Nullable InheritedProperties properties) {
    AffineTransform transform = computeAffineTransform(properties);
    myArea.transform(transform);
    transformGradient(transform);
    if (properties != null) {
      applyOpacity(properties.getInheritedOpacity());
    }
  }

  /**
   * Takes the scaling ratio on each axis, computes the appropriate {@link AffineTransform} and applies it
   * on the area and its {@link GradientModel}
   */
  @Override
  public void scale(double scaleX, double scaleY) {
    AffineTransform scaleTransform = new AffineTransform();
    scaleTransform.scale(scaleX, scaleY);
    myArea.transform(scaleTransform);
    transformGradient(scaleTransform);
  }

  /**
   * Takes the translating coordinates on each axis, computes the appropriate {@link AffineTransform} and applies it
   * on the area and its {@link GradientModel} to move the shape from the current position to the
   * given coordinates.
   */
  @Override
  public void translateTo(double translateX, double translateY) {
    AffineTransform translateTransform = new AffineTransform();
    translateTransform.translate(-myShapeFrameLocation.getX(), -myShapeFrameLocation.getY());
    translateTransform.translate(translateX, translateY);
    myArea.transform(translateTransform);
    transformGradient(translateTransform);
  }
}