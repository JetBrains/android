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
package com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages;

import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.ResizingConstraint;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.interfaces.SketchLayer;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.interfaces.SketchLayerable;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.deserializers.SketchLayerDeserializer;
import java.awt.Color;
import java.awt.Rectangle;
import org.jetbrains.annotations.NotNull;

/**
 * {@link SketchLayer} that mimics the JSON element with attribute <code>"_class": "artboard"</code> contained within a sketch file.
 * It contains other layers, so it is a {@link SketchLayerable}.
 *
 * @see SketchLayerDeserializer
 */
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
                        boolean includeBackgroundColorInExport,
                        @NotNull ResizingConstraint constraint) {
    super(classType, objectId, booleanOperation, exportOptions, frame, isFlippedHorizontal, isFlippedVertical, isVisible, name, rotation,
          shouldBreakMaskChain, constraint);

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

  public boolean includeBackgroundColorInExport() {
    return includeBackgroundColorInExport;
  }
}
