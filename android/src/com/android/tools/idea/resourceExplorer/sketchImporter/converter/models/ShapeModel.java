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

import com.android.tools.idea.resourceExplorer.sketchImporter.converter.builders.PathStringBuilder;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchBorder;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchFill;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchGradient;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchGraphicContextSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import static java.awt.geom.PathIterator.*;

public abstract class ShapeModel {

  @NotNull private Shape shape;
  @Nullable protected SketchFill shapeFill;
  @Nullable protected SketchBorder shapeBorder;
  protected boolean isFlippedHorizontal;
  protected boolean isFlippedVertical;
  protected boolean isClosed;
  protected int rotationDegrees;
  protected int shapeOperation;
  @NotNull protected Point2D.Double shapeFrameCoordinates;
  protected boolean hasClippingMask;
  protected boolean shouldBreakMaskChain;
  protected boolean isLastShape;
  @Nullable protected SketchGradient shapeGradient;

  public ShapeModel(@NotNull Shape shape,
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
    this.shape = shape;
    shapeFill = fill;
    if (shapeFill != null) {
      shapeGradient = shapeFill.getGradient();
      if (shapeGradient != null) {
        shapeGradient.toRelativeGradient(this.shape.getBounds2D());
      }
    }
    shapeBorder = border;
    isFlippedHorizontal = flippedHorizontal;
    isFlippedVertical = flippedVertical;
    isClosed = closed;
    rotationDegrees = rotation;
    shapeOperation = operation;
    shapeFrameCoordinates = framePosition;
    this.hasClippingMask = hasClippingMask;
    this.shouldBreakMaskChain = shouldBreakMaskChain;
    isLastShape = isLastShapeGroup;
  }

  public int getBooleanOperation() {
    return shapeOperation;
  }

  @NotNull
  private String getPathString() {
    PathStringBuilder pathStringBuilder = new PathStringBuilder();
    PathIterator pathIterator = shape.getPathIterator(null);

    while (!pathIterator.isDone()) {
      double[] coordinates = new double[6];
      int type = pathIterator.currentSegment(coordinates);

      switch (type) {
        case SEG_MOVETO:
          pathStringBuilder.startPath(coordinates[0], coordinates[1]);
          break;
        case SEG_LINETO:
          pathStringBuilder.createLine(coordinates[0], coordinates[1]);
          break;
        case SEG_CUBICTO:
          pathStringBuilder.createBezierCurve(coordinates);
          break;
        case SEG_QUADTO:
          pathStringBuilder.createQuadCurve(coordinates[0], coordinates[1], coordinates[2], coordinates[3]);
          break;
        case SEG_CLOSE:
          pathStringBuilder.endPath();
          break;
      }

      pathIterator.next();
    }
    return pathStringBuilder.build();
  }

  public void setRotation(int rotation) {
    rotationDegrees = rotation;
  }

  public void setMirroring(boolean flippedHorizontal, boolean flippedVertical) {
    isFlippedHorizontal = flippedHorizontal;
    isFlippedVertical = flippedVertical;
  }

  public void setTranslation(@NotNull Point2D.Double coords) {
    shapeFrameCoordinates = coords;
  }

  @NotNull
  protected AffineTransform computeAffineTransform() {
    AffineTransform shapeTransform = new AffineTransform();
    shapeTransform.setToIdentity();

    shapeTransform.translate(shapeFrameCoordinates.getX(), shapeFrameCoordinates.getY());

    Rectangle2D bounds = shape.getBounds2D();
    if (isFlippedHorizontal) {
      shapeTransform.scale(-1, 1);
      shapeTransform.translate(-(bounds.getWidth() + 2 * bounds.getX()), 0);
    }
    if (isFlippedVertical) {
      shapeTransform.scale(1, -1);
      shapeTransform.translate(0, -(bounds.getHeight() + 2 * bounds.getY()));
    }

    double anchorPointX = shape.getBounds2D().getCenterX();
    double anchorPointY = shape.getBounds2D().getCenterY();
    shapeTransform.rotate(Math.toRadians(-rotationDegrees), anchorPointX, anchorPointY);

    return shapeTransform;
  }

  public abstract void applyTransformations();

  @NotNull
  public DrawableModel toDrawableShape() {
    String shapePathData = getPathString();
    String shapeBorderWidth = null;
    int shapeBorderColor = 0;
    int shapeFillColor = 0;
    SketchGradient shapeGradient = null;
    SketchGraphicContextSettings shapeGraphicContextSettings = null;

    if (shapeBorder != null) {
      shapeBorderWidth = Integer.toString(shapeBorder.getThickness());
      shapeBorderColor = shapeBorder.getColor().getRGB();
    }

    if (shapeFill != null && shapeFill.isEnabled()) {
      shapeGradient = shapeFill.getGradient();
      shapeGraphicContextSettings = shapeFill.getContextSettings();
      if (shapeGradient == null) {
        shapeFillColor = shapeFill.getColor().getRGB();
      }
    }

    return new DrawableModel(shapePathData, shapeFillColor, shapeGraphicContextSettings, shapeGradient, shapeBorderColor, shapeBorderWidth, hasClippingMask,
                             shouldBreakMaskChain, isLastShape);
  }
}
