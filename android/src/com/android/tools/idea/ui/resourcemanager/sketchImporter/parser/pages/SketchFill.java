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
import org.jetbrains.annotations.Nullable;

/**
 * Mimics the JSON element with attribute <code>"_class": "fill"</code> contained within a sketch file.
 */
public class SketchFill {
  private final boolean isEnabled;
  private final SketchGraphicsContextSettings contextSettings;
  /**
   * <ul>
   * <li>Flat Color: 0</li>
   * <li>Gradient: 1</li>
   * <li>Pattern: 4 {NOT SUPPORTED}</li>
   * <li>Noise: 5 {NOT SUPPORTED}</li>
   * </ul>
   */
  private final short fillType;
  private final SketchGradient gradient;
  private final SketchFileReference image;
  /**
   * <ul>
   * <li>Original: 0</li>
   * <li>Black: 1</li>
   * <li>White: 2</li>
   * <li>Color: 3</li>
   * </ul>
   */
  private final int noiseIndex;
  /**
   * Seemingly useless, here just in case. The "Intensity" slider in Sketch modifies
   * the opacity from the contextSettings and not this parameter.
   */
  private final int noiseIntensity;
  /**
   * <ul>
   * <li>Tile: 0</li>
   * <li>Fill: 1</li>
   * <li>Stretch: 2</li>
   * <li>Fit: 3</li>
   * </ul>
   */
  private final short patternFillType;
  /**
   * Represented as a percentage when patternFillType = 0
   */
  private final int patternTileScale;
  private final Color color;

  public SketchFill(boolean isEnabled,
                    @NotNull Color color,
                    @Nullable SketchGraphicsContextSettings contextSettings,
                    short fillType,
                    @Nullable SketchGradient gradient,
                    @Nullable SketchFileReference image,
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

  @NotNull
  public Color getColor() {
    return color;
  }

  @Nullable
  public SketchGraphicsContextSettings getContextSettings() {
    return contextSettings;
  }

  public short getFillType() {
    return fillType;
  }

  @Nullable
  public SketchGradient getGradient() {
    return gradient;
  }

  @Nullable
  public SketchFileReference getImage() {
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
