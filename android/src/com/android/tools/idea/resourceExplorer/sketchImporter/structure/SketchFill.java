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
  private final SketchGraphicContextSettings contextSettings;
  /**
   * Flat Color: 0
   * Gradient: 1
   * Pattern: 4 {NOT SUPPORTED}
   * Noise: 5 {NOT SUPPORTED}
   */
  private final short fillType;
  private final SketchGradient gradient;
  private final SketchMSJSONFileReference image;
  /**
   * Original: 0
   * Black: 1
   * White: 2
   * Color: 3
   */
  private final int noiseIndex;
  /**
   * Seemingly useless, here just in case. The "Intensity" slider in Sketch modifies
   * the opacity from the contextSettings and not this parameter.
   */
  private final int noiseIntensity;
  /**
   * Tile: 0
   * Fill: 1
   * Stretch: 2
   * Fit: 3
   */
  private final short patternFillType;
  /**
   * Represented as a percentage when patternFillType = 0
   */
  private final int patternTileScale;

  public SketchFill(boolean isEnabled,
                    @NotNull Color color,
                    @Nullable SketchGraphicContextSettings contextSettings,
                    short fillType,
                    @Nullable SketchGradient gradient,
                    @Nullable SketchMSJSONFileReference image,
                    int noiseIndex,
                    int noiseIntensity,
                    short patternFillType,
                    int patternTileScale) {
    this.isEnabled = isEnabled;
    this.color = color;
    this.contextSettings = contextSettings;
    this.fillType = fillType;
    this.gradient = gradient;
    this.image = image;
    this.noiseIndex = noiseIndex;
    this.noiseIntensity = noiseIntensity;
    this.patternFillType = patternFillType;
    this.patternTileScale = patternTileScale;
  }

  public boolean isEnabled() {
    return isEnabled;
  }

  public Color getColor() {
    return color;
  }

  public SketchGraphicContextSettings getContextSettings() {
    return contextSettings;
  }

  public short getFillType() {
    return fillType;
  }

  public SketchGradient getGradient() {
    return gradient;
  }

  public SketchMSJSONFileReference getImage() {
    return image;
  }

  public int getNoiseIndex() {
    return noiseIndex;
  }

  public int getNoiseIntensity() {
    return noiseIntensity;
  }

  public short getPatternFillType() {
    return patternFillType;
  }

  public int getPatternTileScale() {
    return patternTileScale;
  }
}
