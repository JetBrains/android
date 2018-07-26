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

import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class SketchSymbolMaster extends SketchLayer {
  private final SketchStyle style;
  private final SketchLayer[] layers;
  private final Color backgroundColor;
  private final boolean hasBackgroundColor;
  private final boolean includeBackgroundColorInInstance;
  private final String symbolID;
  private final int changeIdentifier;

  public SketchSymbolMaster(@NotNull String classType,
                            @NotNull String objectId,
                            int booleanOperation,
                            @NotNull Rectangle.Double frame,
                            boolean isFlippedHorizontal,
                            boolean isFlippedVertical,
                            boolean isVisible,
                            @NotNull String name,
                            int rotation,
                            boolean shouldBreakMaskChain,
                            @NotNull SketchStyle style,
                            @NotNull SketchLayer[] layers,
                            @NotNull Color color,
                            boolean hasBackgroundColor,
                            boolean includeBackgroundColorInInstance, String symbolID, int changeIdentifier) {
    super(classType, objectId, booleanOperation, frame, isFlippedHorizontal, isFlippedVertical, isVisible, name, rotation,
          shouldBreakMaskChain);
    this.style = style;
    this.layers = layers;
    backgroundColor = color;
    this.hasBackgroundColor = hasBackgroundColor;
    this.includeBackgroundColorInInstance = includeBackgroundColorInInstance;
    this.symbolID = symbolID;
    this.changeIdentifier = changeIdentifier;
  }

  @NotNull
  public SketchStyle getStyle() {
    return style;
  }

  @NotNull
  public SketchLayer[] getLayers() {
    return layers;
  }

  @NotNull
  public Color getBackgroundColor() {
    return backgroundColor;
  }

  public boolean isHasBackgroundColor() {
    return hasBackgroundColor;
  }

  public boolean isIncludeBackgroundColorInInstance() {
    return includeBackgroundColorInInstance;
  }

  @NotNull
  public String getSymbolID() {
    return symbolID;
  }

  public int getChangeIdentifier() {
    return changeIdentifier;
  }
}
