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

import java.awt.Color;
import org.jetbrains.annotations.NotNull;

/**
 * Mimics the JSON element with attribute <code>"_class"</code> set to be one of the following:
 * <ul>
 * <li><code>"innerShadow"</code></li>
 * <li><code>"shadow"</code></li>
 * </ul>
 */
public class SketchShadow {
  private final boolean isEnabled;
  private final int blurRadius;
  private final Color color;
  private final SketchGraphicsContextSettings contextSettings;
  private final int offsetX;
  private final int offsetY;
  private final short spread;

  public SketchShadow(boolean enabled,
                      int blurRadius,
                      @NotNull Color color,
                      @NotNull SketchGraphicsContextSettings settings,
                      int offsetX,
                      int offsetY,
                      short spread) {
    isEnabled = enabled;
    this.blurRadius = blurRadius;
    this.color = color;
    contextSettings = settings;
    this.offsetX = offsetX;
    this.offsetY = offsetY;
    this.spread = spread;
  }

  public boolean isEnabled() {
    return isEnabled;
  }

  public int getBlurRadius() {
    return blurRadius;
  }

  @NotNull
  public Color getColor() {
    return color;
  }

  @NotNull
  public SketchGraphicsContextSettings getContextSettings() {
    return contextSettings;
  }

  public int getOffsetX() {
    return offsetX;
  }

  public int getOffsetY() {
    return offsetY;
  }

  public short getSpread() {
    return spread;
  }
}
