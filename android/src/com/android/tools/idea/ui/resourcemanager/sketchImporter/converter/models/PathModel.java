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

import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Class that extends from {@link ShapeModel} and contains functionality for the transformations
 * of the {@code PathModel} and its conversion to {@code AreaModel}
 */
public class PathModel extends ShapeModel {

  @NotNull
  private final Path2D.Double myPath;

  public PathModel(@NotNull Path2D.Double shape,
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
    myPath = shape;
  }

  public AreaModel convertToArea() {
    if (!myIsClosed) {
      closeShape();
    }
    return new AreaModel(new Area(myPath), myShapeStyle, myIsflippedhorizontal, myIsFlippedVertical, myIsClosed, myRotationDegrees,
                         myShapeOperation,
                         myShapeFrameLocation, myHasClippingMask, myShouldBreakMaskChain, myIsLastShape, myResizingConstraint);
  }

  private void closeShape() {
    myPath.closePath();
  }

  /**
   * Takes an {@link InheritedProperties} object, computes the appropriate {@link AffineTransform} and applies it
   * to the path. Also applies opacity from the {@code InheritedProperties} object
   */
  @Override
  public void applyTransformations(@Nullable InheritedProperties properties) {
    AffineTransform transform = computeAffineTransform(properties);
    myPath.transform(transform);
    transformGradient(transform);
    if (properties != null) {
      applyOpacity(properties.getInheritedOpacity());
    }
  }

  /**
   * Takes the scaling ratio on each axis, computes the appropriate {@link AffineTransform} and applies it
   * on the path and its {@link GradientModel}
   */
  @Override
  public void scale(double scaleX, double scaleY) {
    AffineTransform scaleTransform = new AffineTransform();
    scaleTransform.scale(scaleX, scaleY);
    myPath.transform(scaleTransform);
    transformGradient(scaleTransform);
  }

  /**
   * Takes the translating coordinates on each axis, computes the appropriate {@link AffineTransform} and applies it
   * on the path and its {@link GradientModel} to move the shape from the current position to the
   * given coordinates.
   */
  @Override
  public void translateTo(double translateX, double translateY) {
    AffineTransform translateTransform = new AffineTransform();
    translateTransform.translate(-myShapeFrameLocation.getX(), -myShapeFrameLocation.getY());
    translateTransform.translate(translateX, translateY);
    myPath.transform(translateTransform);
    transformGradient(translateTransform);
  }
}