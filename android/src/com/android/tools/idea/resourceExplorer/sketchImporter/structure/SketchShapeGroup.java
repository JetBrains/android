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
import java.util.Objects;

import static com.android.tools.idea.resourceExplorer.sketchImporter.logic.PathUtils.toPathString;

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
  public List<DrawableShape> getTranslatedShapes(Point2D.Double parentCoords) {
    String shapePathData = buildShapeString(parentCoords);
    String shapeName = getName();
    String shapeBorderWidth = null;
    String shapeBorderColor = null;
    String shapeFillColor = null;
    SketchStyle style = getStyle();
    SketchBorder border = Objects.requireNonNull(style.getBorders())[0];
    if (border.isEnabled()) {
      shapeBorderWidth = Integer.toString(border.getThickness());
      shapeBorderColor = "#" + Integer.toHexString(border.getColor().getRGB());
    }

    if (Objects.requireNonNull(style.getFills())[0].isEnabled()) {
      shapeFillColor = "#" + Integer.toHexString(style.getFills()[0].getColor().getRGB());
    }
    return ImmutableList.of(new DrawableShape(shapeName, shapePathData, shapeFillColor, shapeBorderColor, shapeBorderWidth));
  }

  @NotNull
  private String buildShapeString(@NotNull Point2D.Double parentCoordinates) {

    SketchLayer[] shapeGroupLayers = getLayers();
    Rectangle2D.Double frame = getFrame();
    Rectangle.Double newFrame = new Rectangle2D.Double();
    newFrame.setRect(frame.getX() + parentCoordinates.getX(),
                     frame.getY() + parentCoordinates.getY(),
                     frame.getWidth(),
                     frame.getHeight());

    if (shapeGroupLayers.length == 1) {
      SketchLayer layer = shapeGroupLayers[0];

      SketchShapePath sketchShapePath = (SketchShapePath)layer;

      return buildSingleShapeString(sketchShapePath, newFrame);
    }
    else {

      return buildCombinedShapeString(shapeGroupLayers, newFrame);
    }
  }

  private static String buildSingleShapeString(SketchShapePath sketchShapePath, Rectangle.Double parentFrame) {

    Path2D.Double shapePath = sketchShapePath.toPath2D(parentFrame);

    return toPathString(shapePath);
  }

  private static String buildCombinedShapeString(SketchLayer[] shapeGroupLayers, Rectangle.Double parentFrame) {

    SketchShapePath baseSketchShapePath = (SketchShapePath)shapeGroupLayers[0];
    Path2D.Double baseShapePath = baseSketchShapePath.toPath2D(parentFrame);
    Area baseShapeArea = new Area(baseShapePath);

    for (int i = 1; i < shapeGroupLayers.length; i++) {
      SketchShapePath sketchShapePath = (SketchShapePath)shapeGroupLayers[i];
      Path2D.Double shapePath = sketchShapePath.toPath2D(parentFrame);
      Area shapeArea = new Area(shapePath);

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

    return toPathString(baseShapeArea);
  }
}

