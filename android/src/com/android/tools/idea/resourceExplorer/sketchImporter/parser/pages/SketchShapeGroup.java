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
package com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages;

import com.android.tools.idea.resourceExplorer.sketchImporter.converter.models.AreaModel;
import com.android.tools.idea.resourceExplorer.sketchImporter.converter.models.PathModel;
import com.android.tools.idea.resourceExplorer.sketchImporter.converter.models.ShapeModel;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.interfaces.SketchLayer;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.interfaces.SketchLayerable;
import com.google.common.collect.ImmutableList;
import java.awt.Rectangle;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import org.jetbrains.annotations.NotNull;

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

  /**
   * Method that generates the shape model of the shape in the SketchShapeGroup object.
   * <p>
   * Shape operations can only be performed on Area objects, and to make sure that the conversion
   * between {@link Path2D.Double} to {@link java.awt.geom.Area} is correct, the {@link Path2D.Double} object must be closed
   */
  @NotNull
  @Override
  public ImmutableList<ShapeModel> createShapeModels(@NotNull Point2D.Double parentCoords, boolean isLastShapeGroup, double parentOpacity) {
    SketchFill[] fills = getStyle().getFills();
    SketchBorder[] borders = getStyle().getBorders();
    SketchFill shapeGroupFill = fills != null ? fills[0] : null;
    SketchBorder shapeGroupBorder = borders != null ? borders[0] : null;

    // If the shape does not have a fill or border, it will not be visible in the VectorDrawable file. However,
    // clipping paths don't need fills and colors to have an effect, but they still need to be included in the
    // DrawableModel list.
    if (shapeGroupBorder == null && shapeGroupFill == null && !hasClippingMask) {
      return ImmutableList.of();
    }

    Point2D.Double newParentCoords = new Point2D.Double(parentCoords.getX() + getFrame().getX(),
                                                        parentCoords.getY() + getFrame().getY());

    SketchLayer[] layers = getLayers();
    SketchShapePath baseSketchShapePath = (SketchShapePath)layers[0];

    Path2D.Double baseShapePath = baseSketchShapePath.getPath2D();

    PathModel finalShape = new PathModel(baseShapePath,
                                         getStyle(),
                                         baseSketchShapePath.isFlippedHorizontal(),
                                         baseSketchShapePath.isFlippedVertical(),
                                         baseSketchShapePath.isClosed(),
                                         baseSketchShapePath.getRotation(),
                                         getBooleanOperation(),
                                         baseSketchShapePath.getFramePosition(),
                                         hasClippingMask,
                                         shouldBreakMaskChain,
                                         isLastShapeGroup,
                                         parentOpacity);

    // If the shapegroup has just one layer, there will be no shape operation.
    // Therefore, no conversion to area needed.
    // Therefore, the path does not necessarily have to be closed.
    if (layers.length == 1) {
      return ImmutableList.of(transformShapeGroup(finalShape, newParentCoords));
    }

    // If the shapegroup has multiple layers, there definitely are some shape operations to be performed.
    // Therefore, the path needs to be closed and converted into an Area before applying anything.
    AreaModel finalArea = finalShape.convertToArea();
    finalArea.applyTransformations();

    for (int i = 1; i < layers.length; i++) {
      SketchShapePath path = (SketchShapePath)layers[i];

      PathModel pathModel = path.createPathModel();
      AreaModel areaModel = pathModel.convertToArea();
      areaModel.applyTransformations();
      finalArea.applyOperation(areaModel);
    }

    // The shapeGroup itself and its components altogether can be rotated or flipped.
    return ImmutableList.of(transformShapeGroup(finalArea, newParentCoords));
  }

  @NotNull
  private ShapeModel transformShapeGroup(@NotNull ShapeModel finalShape, @NotNull Point2D.Double newParentCoords) {
    finalShape.setRotation(getRotation());
    finalShape.setMirroring(isFlippedHorizontal(), isFlippedVertical());
    finalShape.setTranslation(newParentCoords);
    finalShape.applyTransformations();

    return finalShape;
  }
}

