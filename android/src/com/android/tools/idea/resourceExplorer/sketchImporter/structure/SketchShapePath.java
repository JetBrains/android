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

public class SketchShapePath extends SketchLayer {
  private final boolean isClosed;
  private final SketchCurvePoint[] points;

  public SketchShapePath(@NotNull String classType,
                         @NotNull String objectId,
                         int booleanOperation,
                         @NotNull Rectangle.Double frame,
                         boolean isFlippedHorizontal,
                         boolean isFlippedVertical,
                         boolean isVisible,
                         @NotNull String name,
                         int rotation,
                         boolean shouldBreakMaskChain,
                         boolean isClosed,
                         @NotNull SketchCurvePoint[] points) {
    super(classType, objectId, booleanOperation, frame, isFlippedHorizontal, isFlippedVertical, isVisible, name, rotation,
          shouldBreakMaskChain);

    this.isClosed = isClosed;
    this.points = points;
  }

  public boolean isClosed() {
    return isClosed;
  }

  public SketchCurvePoint[] getPoints() {
    return points;
  }
}
