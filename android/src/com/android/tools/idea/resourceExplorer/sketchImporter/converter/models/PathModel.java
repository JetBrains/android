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

import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchStyle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PathModel extends ShapeModel {

  @NotNull
  private final Path2D.Double path;

  public PathModel(@NotNull Path2D.Double shape,
                   @Nullable SketchStyle style,
                   boolean flippedHorizontal,
                   boolean flippedVertical,
                   boolean closed,
                   int rotation,
                   int operation,
                   @NotNull Point2D.Double framePosition,
                   boolean hasClippingMask,
                   boolean shouldBreakMaskChain,
                   boolean isLastShapeGroup, double parentOpacity) {
    super(shape, style, flippedHorizontal, flippedVertical, closed, rotation, operation, framePosition, hasClippingMask,
          shouldBreakMaskChain, isLastShapeGroup, parentOpacity);
    path = shape;
  }

  public AreaModel convertToArea() {
    if (!isClosed) {
      closeShape();
    }
    return new AreaModel(new Area(path), shapeStyle, isFlippedHorizontal, isFlippedVertical, isClosed, rotationDegrees,
                         shapeOperation,
                         shapeFrameCoordinates, hasClippingMask, shouldBreakMaskChain, isLastShape, myParentOpacity);
  }

  private void closeShape() {
    path.closePath();
  }

  @Override
  public void applyTransformations() {
    AffineTransform transform = computeAffineTransform();
    path.transform(transform);
    transformGradient(transform);
  }

  @Override
  public void scale(double scaleX, double scaleY) {
    AffineTransform scaleTransform = new AffineTransform();
    scaleTransform.scale(scaleX, scaleY);
    path.transform(scaleTransform);
  }


  @Override
  public void translate(double translateX, double translateY) {
    AffineTransform translateTransform = new AffineTransform();
    translateTransform.translate(translateX, translateY);
    path.transform(translateTransform);
  }
}
