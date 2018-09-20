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

import static java.awt.geom.PathIterator.SEG_CLOSE;
import static java.awt.geom.PathIterator.SEG_CUBICTO;
import static java.awt.geom.PathIterator.SEG_LINETO;
import static java.awt.geom.PathIterator.SEG_MOVETO;
import static java.awt.geom.PathIterator.SEG_QUADTO;

import com.android.tools.idea.resourceExplorer.sketchImporter.converter.builders.PathStringBuilder;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ShapeModel {

  private static final int DEFAULT_BORDER_WIDTH_VALUE = 0;
  private static final int DEFAULT_COLOR_VALUE = 0;

  @Nullable protected StyleModel shapeStyle;
  protected boolean isFlippedHorizontal;
  protected boolean isFlippedVertical;
  protected boolean isClosed;
  protected int rotationDegrees;
  protected int shapeOperation;
  @NotNull protected Point2D.Double shapeFrameCoordinates;
  protected boolean hasClippingMask;
  protected boolean shouldBreakMaskChain;
  protected boolean isLastShape;
  @NotNull private Shape shape;

  public ShapeModel(@NotNull Shape shape,
                    @Nullable StyleModel style,
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
    isFlippedHorizontal = flippedHorizontal;
    isFlippedVertical = flippedVertical;
    isClosed = closed;
    rotationDegrees = rotation;
    shapeOperation = operation;
    shapeFrameCoordinates = framePosition;
    this.hasClippingMask = hasClippingMask;
    this.shouldBreakMaskChain = shouldBreakMaskChain;
    isLastShape = isLastShapeGroup;
    shapeStyle = style;
  }

  @NotNull
  public Shape getShape() {
    return shape;
  }

  public int getBooleanOperation() {
    return shapeOperation;
  }

  @Nullable
  public BorderModel getShapeBorder() {
    return shapeStyle != null ? shapeStyle.getBorder() : null;
  }

  public int getBorderColor() {
    return getShapeBorder() != null ? getShapeBorder().getColor().getRGB() : DEFAULT_COLOR_VALUE;
  }

  public int getBorderWidth() {
    return getShapeBorder() != null ? getShapeBorder().getWidth() : DEFAULT_BORDER_WIDTH_VALUE;
  }

  @Nullable
  public FillModel getFill() {
    return shapeStyle != null ? shapeStyle.getFill() : null;
  }

  public int getFillColor() {
    return getFill() != null ? getFill().getColor().getRGB() : DEFAULT_COLOR_VALUE;
  }

  @Nullable
  public GradientModel getGradient() {
    return getFill() != null ? getFill().getGradientModel() : null;
  }

  /**
   * Method that checks if the model should be used to mask other shapes in the drawable file.
   * Used for generating XML clipping groups.
   *
   * @return true if the DrawableModel is a sketch clipping mask
   */
  public boolean hasClippingMask() {
    return hasClippingMask;
  }

  /**
   * There can be cases when a group has multiple clipped shapes, along with shapes that
   * are not clipped. The first shape that follows a chain of masked shapes and is not
   * masked like the previous shapes is known to 'break the mask chain'
   *
   * @return true if the DrawableModel breaks the chain of masked shapes in its group.
   */
  public boolean shouldBreakMaskChain() {
    return shouldBreakMaskChain;
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

  @NotNull
  public String getPathString() {
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

  /**
   * Applies all the transformations that have been done on a shape to its corresponding gradient
   * by modifying the style property of the ShapeModel.
   *
   * @param transform
   */
  protected void transformGradient(@NotNull AffineTransform transform) {
    FillModel shapeFill = shapeStyle != null ? shapeStyle.getFill() : null;
    GradientModel shapeGradient = shapeFill != null ? shapeFill.getGradientModel() : null;
    if (shapeGradient != null) {
      shapeGradient.applyTransformation(transform);
    }
  }

  public void applyOpacity(double parentOpacity) {
    if (shapeStyle != null) {
      shapeStyle.applyOpacity(parentOpacity);
    }
  }

  public abstract void applyTransformations();

  public abstract void scale(double scaleX, double scaleY);

  public abstract void translate(double translateX, double translateY);
}
