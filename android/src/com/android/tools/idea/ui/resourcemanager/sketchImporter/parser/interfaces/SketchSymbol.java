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
package com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.interfaces;

import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.ResizingConstraint;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchExportOptions;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchExportOptions;
import java.awt.Rectangle;
import org.jetbrains.annotations.NotNull;

/**
 * Holds the fields that are shared between all types of symbols in the sketch JSON file.
 */
public abstract class SketchSymbol extends SketchLayer {

  public SketchSymbol(@NotNull String classType,
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
                      @NotNull ResizingConstraint constraint) {
    super(classType, objectId, booleanOperation, exportOptions, frame, isFlippedHorizontal, isFlippedVertical, isVisible, name, rotation,
          shouldBreakMaskChain, constraint);
  }

  public abstract String getSymbolId();
}
