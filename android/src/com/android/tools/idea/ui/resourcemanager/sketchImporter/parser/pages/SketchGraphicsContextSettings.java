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

/**
 * Mimics the JSON element with attribute <code>"_class": "graphicsContextSettings"</code> contained within a sketch file.
 */
public class SketchGraphicsContextSettings {
  /**
   * <ul>
   * <li>Normal: 0</li>
   * <li>Darken: 1</li>
   * <li>Multiply: 2</li>
   * <li>Color Burn: 3</li>
   * <li>Lighten: 4</li>
   * <li>Screen: 5</li>
   * <li>Color Dodge: 6</li>
   * <li>Overlay: 7</li>
   * <li>Soft Light: 8</li>
   * <li>Hard Light: 9</li>
   * <li>Difference: 10</li>
   * <li>Exclusion: 11</li>
   * <li>Hue: 12</li>
   * <li>Saturation: 13</li>
   * <li>Color: 14</li>
   * <li>Luminosity: 15</li>
   * </ul>
   */
  private final short blendMode;
  /**
   * Opacity as a percentage
   */
  private final double opacity;

  public SketchGraphicsContextSettings(short blendMode, double opacity) {
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
