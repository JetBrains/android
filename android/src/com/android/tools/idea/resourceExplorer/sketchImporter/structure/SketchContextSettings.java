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

public class SketchContextSettings {
  /**
   * Normal: 0
   * Darken: 1
   * Multiply: 2
   * Color Burn: 3
   * Lighten: 4
   * Screen: 5
   * Color Dodge: 6
   * Overlay: 7
   * Soft Light: 8
   * Hard Light: 9
   * Difference: 10
   * Exclusion: 11
   * Hue: 12
   * Saturation: 13
   * Color: 14
   * Luminosity: 15
   */
  private final short blendMode;
  /**
   * Opacity as a percentage
   */
  private final double opacity;

  public SketchContextSettings(short blendMode, double opacity) {
    this.blendMode = blendMode;
    this.opacity = opacity;
  }

  public short getBlendMode() {
    return blendMode;
  }

  public double getOpacity() {
    return opacity;
  }
}
