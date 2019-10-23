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

import static java.awt.geom.PathIterator.SEG_CLOSE;
import static java.awt.geom.PathIterator.SEG_CUBICTO;
import static java.awt.geom.PathIterator.SEG_LINETO;
import static java.awt.geom.PathIterator.SEG_MOVETO;
import static java.awt.geom.PathIterator.SEG_QUADTO;

import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.builders.PathStringBuilder;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Abstract class that contains all the shape details that will be used to generate the
 * vector drawable shapes: the {@link Shape} object, its {@link StyleModel}, clipping data,
 * shape operations or resizing constraints.
 */
public abstract class ShapeModel {

  private static final int DEFAULT_BORDER_WIDTH_VALUE = 0;
  private static final int DEFAULT_COLOR_VALUE = 0;

  protected boolean isFlippedHorizontal;
  protected boolean isFlippedVertical;
  protected boolean isClosed;
  protected boolean hasClippingMask;
  protected boolean shouldBreakMaskChain;
  @Nullable protected StyleModel myShapeStyle;
  protected boolean myIsClosed;
  protected int myShapeOperation;
  protected boolean myHasClippingMask;
  protected boolean myShouldBreakMaskChain;
  protected boolean myIsLastShape;
  @NotNull protected ResizingConstraint myResizingConstraint;
  @NotNull protected Point2D.Double myShapeFrameLocation;
  protected boolean myIsFlippedVertical;
  protected boolean myIsflippedhorizontal;
  protected int myRotationDegrees;
  @NotNull private Shape myShape;
  @NotNull private Rectangle2D myShapeBounds;

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
                    boolean isLastShapeGroup,
                    @NotNull ResizingConstraint constraint) {
    myShape = shape;
    myIsClosed = closed;
    myShapeOperation = operation;
    myHasClippingMask = hasClippingMask;
    myShouldBreakMaskChain = shouldBreakMaskChain;
    myIsLastShape = isLastShapeGroup;
    myShapeStyle = style;
    myResizingConstraint = constraint;

    myIsflippedhorizontal = flippedHorizontal;
    myIsFlippedVertical = flippedVertical;
    myShapeFrameLocation = framePosition;
    myRotationDegrees = rotation;
    myShapeBounds = shape.getBounds2D();
  }

  @NotNull
  public Shape getShape() {
    return myShape;
  }

  public int getBooleanOperation() {
    return myShapeOperation;
  }

  @Nullable
  public BorderModel getShapeBorder() {
    return myShapeStyle != null ? myShapeStyle.getBorder() : null;
  }

  public int getBorderColor() {
    return getShapeBorder() != null ? getShapeBorder().getColor().getRGB() : DEFAULT_COLOR_VALUE;
  }

  public int getBorderWidth() {
    return getShapeBorder() != null ? getShapeBorder().getWidth() : DEFAULT_BORDER_WIDTH_VALUE;
  }

  @Nullable
  public FillModel getFill() {
    return myShapeStyle != null ? myShapeStyle.getFill() : null;
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
    return myHasClippingMask;
  }

  /**
   * There can be cases when a group has multiple clipped shapes, along with shapes that
   * are not clipped. The first shape that follows a chain of masked shapes and is not
   * masked like the previous shapes is known to 'break the mask chain'
   *
   * @return true if the DrawableModel breaks the chain of masked shapes in its group.
   */
  public boolean shouldBreakMaskChain() {
    return myShouldBreakMaskChain;
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
    return myIsLastShape;
  }

  @NotNull
  public ResizingConstraint getResizingConstraint() {
    return myResizingConstraint;
  }

  @NotNull
  public Point2D.Double getShapeFrameLocation() {
    return myShapeFrameLocation;
  }

  /**
   * Takes the {@link InheritedProperties} object and computes the appropriate {@link AffineTransform}
   * If the {@code InheritedProperties} is null, the method uses the shape's properties (case used when
   * calling {@code createPathModel()})
   * Otherwise, the {@code AffineTransform} is computed using the properties inherited from the parents.
   */
  @NotNull
  protected AffineTransform computeAffineTransform(@Nullable InheritedProperties inheritedProperties) {
    if (inheritedProperties != null) {
      myShapeFrameLocation.setLocation(inheritedProperties.getInheritedTranslation());
      myIsflippedhorizontal = inheritedProperties.isInheritedFlipX();
      myIsFlippedVertical = inheritedProperties.isInheritedFlipY();
      myRotationDegrees = inheritedProperties.getInheritedRotation();
    }

    AffineTransform shapeTransform = new AffineTransform();
    shapeTransform.setToIdentity();

    shapeTransform.translate(myShapeFrameLocation.getX(), myShapeFrameLocation.getY());

    if (myIsflippedhorizontal) {
      shapeTransform.scale(-1, 1);
      shapeTransform.translate(-(myShapeBounds.getWidth() + 2 * myShapeBounds.getX()), 0);
    }
    if (myIsFlippedVertical) {
      shapeTransform.scale(1, -1);
      shapeTransform.translate(0, -(myShapeBounds.getHeight() + 2 * myShapeBounds.getY()));
    }

    shapeTransform.rotate(Math.toRadians(-myRotationDegrees), myShapeBounds.getCenterX(), myShapeBounds.getCenterY());

    return shapeTransform;
  }

  @NotNull
  public String getPathString() {
    PathStringBuilder pathStringBuilder = new PathStringBuilder();
    PathIterator pathIterator = myShape.getPathIterator(null);

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
   */
  protected void transformGradient(@NotNull AffineTransform transform) {
    FillModel shapeFill = myShapeStyle != null ? myShapeStyle.getFill() : null;
    GradientModel shapeGradient = shapeFill != null ? shapeFill.getGradientModel() : null;
    if (shapeGradient != null) {
      shapeGradient.applyTransformation(transform);
    }
  }

  /**
   * Takes the opacity accumulated from the upper layers and applies it on the shape's {@link StyleModel}
   */
  public void applyOpacity(double parentOpacity) {
    if (myShapeStyle != null) {
      myShapeStyle.applyOpacity(parentOpacity);
    }
  }

  public void setFramePosition(@NotNull Point2D.Double position) {
    myShapeFrameLocation = position;
  }

  public abstract void applyTransformations(@Nullable InheritedProperties inheritedProperties);

  public abstract void scale(double scaleX, double scaleY);

  public abstract void translateTo(double scaleX, double scaleY);
}