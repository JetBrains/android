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
import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.ResizingConstraint;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.deserializers.SketchLayerDeserializer;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.interfaces.SketchLayer;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.interfaces.SketchLayerable;
import java.awt.Rectangle;
import org.jetbrains.annotations.NotNull;

/**
 * {@link SketchLayer} that mimics the JSON element with attribute <code>"_class": "shapeGroup"</code> contained within a sketch file.
 * It contains other layers, so it is a {@link SketchLayerable}.
 *
 * @see SketchLayerDeserializer
 */
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
                          short windingRule,
                          @NotNull ResizingConstraint constraint) {
    super(classType, objectId, booleanOperation, exportOptions, frame, isFlippedHorizontal, isFlippedVertical, isVisible, name, rotation,
          shouldBreakMaskChain, constraint);
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
}

