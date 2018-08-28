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
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchBorder;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchFill;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchGradient;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchStyle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ShapeModel {

  protected final double myParentOpacity;
  @NotNull private Shape shape;
  @Nullable protected SketchStyle shapeStyle;
  protected boolean isFlippedHorizontal;
  protected boolean isFlippedVertical;
  protected boolean isClosed;
  protected int rotationDegrees;
  protected int shapeOperation;
  @NotNull protected Point2D.Double shapeFrameCoordinates;
  protected boolean hasClippingMask;
  protected boolean shouldBreakMaskChain;
  protected boolean isLastShape;

  public ShapeModel(@NotNull Shape shape,
                    @Nullable SketchStyle style,
                    boolean flippedHorizontal,
                    boolean flippedVertical,
                    boolean closed,
                    int rotation,
                    int operation,
                    @NotNull Point2D.Double framePosition,
                    boolean hasClippingMask,
                    boolean shouldBreakMaskChain,
                    boolean isLastShapeGroup,
                    double parentOpacity) {
    this.shape = shape;
    myParentOpacity = parentOpacity;
    isFlippedHorizontal = flippedHorizontal;
    isFlippedVertical = flippedVertical;
    isClosed = closed;
    rotationDegrees = rotation;
    shapeOperation = operation;
    shapeFrameCoordinates = framePosition;
    this.hasClippingMask = hasClippingMask;
    this.shouldBreakMaskChain = shouldBreakMaskChain;
    isLastShape = isLastShapeGroup;

    if (style != null) {
      shapeStyle = style;
      shapeStyle.makeGradientRelative(this.shape);
      shapeStyle.applyParentOpacity(myParentOpacity);
    }
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

  /**
   * Applies all the transformations that have been done on a shape to its corresponding gradient
   * by modifying the style property of the ShapeModel.
   *
   * @param transform
   */
  protected void transformGradient(@NotNull AffineTransform transform) {
    SketchFill[] shapeFills = shapeStyle != null ? shapeStyle.getFills() : null;
    SketchFill shapeFill = shapeFills != null && shapeFills.length != 0 ? shapeFills[0] : null;
    SketchGradient shapeGradient = shapeFill != null ? shapeFill.getGradient() : null;
    if (shapeGradient != null) {
      shapeGradient.applyTransformation(transform);
    }
  }

  @NotNull
  public DrawableModel toDrawableShape() {
    String shapePathData = getPathString();
    String shapeBorderWidth = null;
    int shapeBorderColor = 0;
    int shapeFillColor = 0;

    SketchBorder[] shapeBorders = shapeStyle != null ? shapeStyle.getBorders() : null;
    SketchBorder shapeBorder = shapeBorders != null && shapeBorders.length != 0 ? shapeBorders[0] : null;
    if (shapeBorder != null) {
      shapeBorderWidth = Integer.toString(shapeBorder.getThickness());
      shapeBorderColor = shapeBorder.getColor().getRGB();
    }

    SketchFill[] shapeFills = shapeStyle != null ? shapeStyle.getFills() : null;
    SketchFill shapeFill = shapeFills != null && shapeFills.length != 0 ? shapeFills[0] : null;
    SketchGradient shapeGradient = shapeFill != null && shapeFill.isEnabled() ? shapeFill.getGradient() : null;
    if (shapeGradient == null && shapeFill != null && shapeFill.isEnabled()) {
      shapeFillColor = shapeFill.getColor().getRGB();
    }


    return new DrawableModel(shapePathData, shapeFillColor, shapeGradient, shapeBorderColor, shapeBorderWidth, hasClippingMask,
                             shouldBreakMaskChain, isLastShape);
  }
}
