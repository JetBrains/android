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

import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchBorder;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchFill;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;

public class PathModel extends ShapeModel {

  @NotNull
  private final Path2D.Double path;

  public PathModel(@NotNull Path2D.Double shape,
                   @Nullable SketchFill fill,
                   @Nullable SketchBorder border,
                   boolean flippedHorizontal,
                   boolean flippedVertical,
                   boolean closed,
                   int rotation,
                   int operation,
                   @NotNull Point2D.Double framePosition,
                   boolean hasClippingMask,
                   boolean shouldBreakMaskChain,
                   boolean isLastShapeGroup) {
    super(shape, fill, border, flippedHorizontal, flippedVertical, closed, rotation, operation, framePosition, hasClippingMask,
          shouldBreakMaskChain, isLastShapeGroup);
    path = shape;
  }

  public AreaModel convertToArea() {
    if (!isClosed) {
      closeShape();
    }
    return new AreaModel(new Area(path), shapeFill, shapeBorder, isFlippedHorizontal, isFlippedVertical, isClosed, rotationDegrees,
                         shapeOperation,
                         shapeFrameCoordinates, hasClippingMask, shouldBreakMaskChain, isLastShape);
  }

  private void closeShape() {
    path.closePath();
  }

  @Override
  public void applyTransformations() {
    AffineTransform transform = computeAffineTransform();

    path.transform(transform);

    if (shapeGradient != null) {
      shapeGradient.applyTransformation(transform);
    }
  }
}
