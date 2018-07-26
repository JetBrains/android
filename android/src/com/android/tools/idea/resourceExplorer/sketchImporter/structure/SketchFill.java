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
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class SketchFill {
  private final boolean isEnabled;
  private final Color color;
  /**
   * Flat Color: 0
   * Gradient: 1
   */
  private final short fillType;
  private final SketchGradient gradient;

  public SketchFill(boolean isEnabled,
                    @NotNull Color color,
                    short fillType,
                    @Nullable SketchGradient gradient) {
    this.isEnabled = isEnabled;
    this.color = color;
    this.fillType = fillType;
    this.gradient = gradient;
  }

  public boolean isEnabled() {
    return isEnabled;
  }

  public Color getColor() {
    return color;
  }

  public short getFillType() {
    return fillType;
  }

  public SketchGradient getGradient() {
    return gradient;
  }
}
