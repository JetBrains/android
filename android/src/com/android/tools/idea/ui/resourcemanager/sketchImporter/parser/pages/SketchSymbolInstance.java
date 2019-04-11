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
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.interfaces.SketchSymbol;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.ResizingConstraint;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.interfaces.SketchSymbol;
import java.awt.Rectangle;
import org.jetbrains.annotations.NotNull;

/**
 * {@link SketchSymbol} that mimics the JSON element with attribute <code>"_class": "symbolInstance"</code> contained within a sketch file.
 */
public class SketchSymbolInstance extends SketchSymbol {
  private final SketchStyle style;
  private final double scale;
  private final String symbolID;

  public SketchSymbolInstance(@NotNull String classType,
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
                              double scale,
                              @NotNull String symbolID,
                              @NotNull ResizingConstraint constraint) {
    super(classType, objectId, booleanOperation, exportOptions, frame, isFlippedHorizontal, isFlippedVertical, isVisible, name, rotation,
          shouldBreakMaskChain, constraint);
    this.style = style;
    this.scale = scale;
    this.symbolID = symbolID;
  }

  @NotNull
  public SketchStyle getStyle() {
    return style;
  }

  public double getScale() {
    return scale;
  }

  @Override
  @NotNull
  public String getSymbolId() {
    return symbolID;
  }
}
