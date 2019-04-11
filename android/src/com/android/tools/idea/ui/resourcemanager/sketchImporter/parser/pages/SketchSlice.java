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
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.deserializers.SketchLayerDeserializer;
import java.awt.Color;
import java.awt.Rectangle;
import org.jetbrains.annotations.NotNull;

/**
 * {@link SketchLayer} that mimics the JSON element with attribute <code>"_class": "slice"</code> contained within a sketch file.
 *
 * @see SketchLayerDeserializer
 */
public class SketchSlice extends SketchLayer {
  private final Color backgroundColor;
  private final boolean hasBackgroundColor;

  public SketchSlice(@NotNull String classType,
                     @NotNull String do_objectID,
                     int booleanOperation,
                     @NotNull SketchExportOptions exportOptions,
                     @NotNull Rectangle.Double frame,
                     boolean isFlippedHorizontal,
                     boolean isFlippedVertical,
                     boolean isVisible,
                     @NotNull String name,
                     int rotation,
                     boolean shouldBreakMaskChain,
                     @NotNull Color backgroundColor,
                     boolean hasBackgroundColor,
                     @NotNull ResizingConstraint constraint) {
    super(classType, do_objectID, booleanOperation, exportOptions, frame, isFlippedHorizontal, isFlippedVertical, isVisible, name, rotation,
          shouldBreakMaskChain, constraint);
    this.backgroundColor = backgroundColor;
    this.hasBackgroundColor = hasBackgroundColor;
  }

  @NotNull
  public Color getBackgroundColor() {
    return backgroundColor;
  }

  public boolean hasBackgroundColor() {
    return hasBackgroundColor;
  }
}

