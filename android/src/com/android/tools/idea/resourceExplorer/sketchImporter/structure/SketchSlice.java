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

public class SketchSlice extends SketchLayer {
  private final Color backgroundColor;
  private final boolean hasBackgroundColor;

  public SketchSlice(@NotNull String do_objectID,
                     int booleanOperation,
                     @NotNull Rectangle frame,
                     boolean isFlippedHorizontal,
                     boolean isFlippedVertical,
                     boolean isVisible,
                     @NotNull String name,
                     int rotation,
                     boolean shouldBreakMaskChain,
                     @NotNull Color backgroundColor,
                     boolean hasBackgroundColor) {
    super(do_objectID, booleanOperation, frame, isFlippedHorizontal, isFlippedVertical, isVisible, name, rotation, shouldBreakMaskChain);
    this.backgroundColor = backgroundColor;
    this.hasBackgroundColor = hasBackgroundColor;
  }

  public Color getBackgroundColor() {
    return backgroundColor;
  }

  public boolean hasBackgroundColor() {
    return hasBackgroundColor;
  }
}

