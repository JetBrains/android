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

import com.android.tools.idea.resourceExplorer.sketchImporter.converter.models.DrawableModel;
import com.android.tools.idea.resourceExplorer.sketchImporter.converter.models.ShapeModel;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.interfaces.SketchLayer;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.interfaces.SketchLayerable;
import com.google.common.collect.ImmutableList;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import org.jetbrains.annotations.NotNull;

public class SketchArtboard extends SketchLayer implements SketchLayerable {
  private final SketchStyle style;
  private final SketchLayer[] layers;
  private final Color backgroundColor;
  private final boolean hasBackgroundColor;
  private final boolean includeBackgroundColorInExport;

  public SketchArtboard(@NotNull String classType,
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
                        @NotNull Color backgroundColor,
                        boolean hasBackgroundColor,
                        boolean includeBackgroundColorInExport) {
    super(classType, objectId, booleanOperation, exportOptions, frame, isFlippedHorizontal, isFlippedVertical, isVisible, name, rotation,
          shouldBreakMaskChain);

    this.style = style;
    this.layers = layers;
    this.backgroundColor = backgroundColor;
    this.hasBackgroundColor = hasBackgroundColor;
    this.includeBackgroundColorInExport = includeBackgroundColorInExport;
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

  @NotNull
  public Color getBackgroundColor() {
    return backgroundColor;
  }

  public boolean hasBackgroundColor() {
    return hasBackgroundColor;
  }

  @NotNull
  private ImmutableList<ShapeModel> createAllShapeModels() {
    ImmutableList.Builder<ShapeModel> shapes = new ImmutableList.Builder<>();
    SketchLayer[] layers = getLayers();

    for (SketchLayer layer : layers) {
      shapes.addAll(layer.createShapeModels(new Point2D.Double(), false, 1));
    }

    return shapes.build();
  }

  @NotNull
  public ImmutableList<DrawableModel> createAllDrawableShapes() {
    ImmutableList.Builder<DrawableModel> drawableShapes = new ImmutableList.Builder<>();

    for (ShapeModel shapeModel : createAllShapeModels()) {
      drawableShapes.add(shapeModel.toDrawableShape());
    }

    return drawableShapes.build();
  }


  public boolean includeBackgroundColorInExport() {
    return includeBackgroundColorInExport;
  }
}
