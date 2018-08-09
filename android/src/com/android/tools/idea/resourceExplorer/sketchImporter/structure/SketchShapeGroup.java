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
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.interfaces.SketchLayerable;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

import static com.android.tools.idea.resourceExplorer.sketchImporter.logic.PathUtils.*;

public class SketchShapeGroup extends SketchLayer implements SketchLayerable {
  private final SketchStyle style;
  private final SketchLayer[] layers;
  private final short clippingMaskMode;
  private final boolean hasClippingMask;
  private final short windingRule;

  public SketchShapeGroup(@NotNull String classType,
                          @NotNull String objectId,
                          int booleanOperation,
                          @NotNull SketchExportOptions exportOptions,
                          @NotNull Rectangle.Double frame,
                          boolean isFlippedHorizontal,
                          boolean isFlippedVertical,
                          boolean isVisible,
                          @NotNull String name,
                          int rotation,
                          boolean shouldBreakMaskChain,
                          @NotNull SketchStyle style,
                          @NotNull SketchLayer[] layers,
                          short clippingMaskMode,
                          boolean hasClippingMask,
                          short windingRule) {
    super(classType, objectId, booleanOperation, exportOptions, frame, isFlippedHorizontal, isFlippedVertical, isVisible, name, rotation,
          shouldBreakMaskChain);
    this.style = style;
    this.layers = layers;
    this.clippingMaskMode = clippingMaskMode;
    this.hasClippingMask = hasClippingMask;
    this.windingRule = windingRule;
  }

  @Override
  @NotNull
  public SketchStyle getStyle() {
    return style;
  }

  @Override
  @NotNull
  public SketchLayer[] getLayers() {
    return layers;
  }

  public short getClippingMaskMode() {
    return clippingMaskMode;
  }

  public boolean hasClippingMask() {
    return hasClippingMask;
  }

  public short getWindingRule() {
    return windingRule;
  }

  @NotNull
  @Override
  public List<DrawableShape> getTranslatedShapes(@NotNull Point2D.Double parentCoords) {
    String shapePathData = buildShapeString(parentCoords);
    String shapeName = getName();
    String shapeBorderWidth = null;
    String shapeBorderColor = null;
    String shapeFillColor = null;
    SketchStyle style = getStyle();
    SketchBorder[] borders = style.getBorders();
    if (borders != null && borders.length != 0) {
      SketchBorder border = borders[0];
      if (border.isEnabled()) {
        shapeBorderWidth = Integer.toString(border.getThickness());
        shapeBorderColor = "#" + Integer.toHexString(border.getColor().getRGB());
      }
    }

    SketchFill[] fills = style.getFills();

    SketchGradient shapeGradient = null;
    if (fills != null && fills.length != 0) {
      SketchFill fill = fills[0];
      if (fill.isEnabled()) {
        if (fill.getGradient() == null) {
          shapeFillColor = "#" + Integer.toHexString(fill.getColor().getRGB());
        }
        else {
          shapeGradient = fill.getGradient();
          shapeGradient = shapeGradient.toAbsoluteGradient(parentCoords, getFrame());
        }
      }
    }
    return ImmutableList.of(new DrawableShape(shapeName, shapePathData, shapeFillColor, shapeGradient, shapeBorderColor, shapeBorderWidth));
  }


  /*
   * Method that computes the pathData string of the shape in the SketchShapeGroup object.
   *
   * Shape operations can only be performed on Area objects, and to make sure that the conversion
   * between Path2D.Double to Area is correct, the Path2D.Double object MUST be closed
   * */
  @NotNull
  private String buildShapeString(@NotNull Point2D.Double parentCoordinates) {
    SketchLayer[] shapeGroupLayers = getLayers();
    Rectangle2D.Double parentFrame = getFrame();
    Rectangle.Double newFrame = new Rectangle2D.Double();
    newFrame.setRect(parentFrame.getX() + parentCoordinates.getX(),
                     parentFrame.getY() + parentCoordinates.getY(),
                     parentFrame.getWidth(),
                     parentFrame.getHeight());

    SketchShapePath baseSketchShapePath = (SketchShapePath)shapeGroupLayers[0];
    Area baseShapeArea;
    Path2D.Double baseShapePath = baseSketchShapePath.getPath2D(newFrame);


    // However, if the path is not closed
    if (shapeGroupLayers.length == 1) {
      if (getRotation() != 0) {
        baseShapePath = rotatePath(baseShapePath, baseSketchShapePath.getRotation());
      }
      if (baseSketchShapePath.isFlippedHorizontal() || baseSketchShapePath.isFlippedVertical()) {
        baseShapePath = flipPath(baseShapePath, baseSketchShapePath.isFlippedHorizontal(), baseSketchShapePath.isFlippedVertical());
      }
      return toStringPath(baseShapePath);
    }
    else {
      // If the path is already closed, the conversion to Area is completely safe
      if (baseSketchShapePath.isClosed()) {
        baseShapeArea = new Area(baseShapePath);
      }
      else {
        baseShapePath.closePath();
        baseShapeArea = new Area(baseShapePath);
      }
    }
    if (baseSketchShapePath.getRotation() != 0) {
      baseShapeArea = rotateArea(baseShapeArea, baseSketchShapePath.getRotation());
    }
    if (baseSketchShapePath.isFlippedHorizontal() || baseSketchShapePath.isFlippedVertical()) {
      baseShapeArea = flipArea(baseShapeArea, baseSketchShapePath.isFlippedHorizontal(), baseSketchShapePath.isFlippedVertical());
    }

    for (int i = 1; i < shapeGroupLayers.length; i++) {
      SketchShapePath sketchShapePath = (SketchShapePath)shapeGroupLayers[i];
      Path2D.Double shapePath = sketchShapePath.getPath2D(newFrame);
      if (!sketchShapePath.isClosed()) {
        shapePath.closePath();
      }
      Area shapeArea = new Area(shapePath);
      if (sketchShapePath.getRotation() != 0) {
        shapeArea = rotateArea(shapeArea, sketchShapePath.getRotation());
      }
      if (sketchShapePath.isFlippedHorizontal() || sketchShapePath.isFlippedVertical()) {
        shapeArea = flipArea(shapeArea, sketchShapePath.isFlippedHorizontal(), sketchShapePath.isFlippedVertical());
      }

      int booleanOperation = sketchShapePath.getBooleanOperation();
      switch (booleanOperation) {
        case BOOLEAN_OPERATION_UNION:
          baseShapeArea.add(shapeArea);
          break;
        case BOOLEAN_OPERATION_SUBSTRACTION:
          baseShapeArea.subtract(shapeArea);
          break;
        case BOOLEAN_OPERATION_DIFFERENCE:
          baseShapeArea.exclusiveOr(shapeArea);
          break;
        case BOOLEAN_OPERATION_INTERSECTION:
          baseShapeArea.intersect(shapeArea);
          break;
      }
    }
    if (getRotation() != 0) {
      baseShapeArea = rotateArea(baseShapeArea, getRotation());
    }
    if (isFlippedHorizontal() || isFlippedVertical()) {
      baseShapeArea = flipArea(baseShapeArea, isFlippedHorizontal(), isFlippedVertical());
    }
    return toStringPath(baseShapeArea);
  }
}

