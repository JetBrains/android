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
 * Mimics the JSON element with attribute <code>"_class": "colorControls"</code> contained within a sketch file.
 */
public class SketchColorControls {
  private final boolean isEnabled;
  private final int brightness;
  private final int contrast;
  private final int hue;
  private final int saturation;

  public SketchColorControls(boolean isEnabled, int brightness, int contrast, int hue, int saturation) {
    this.isEnabled = isEnabled;
    this.brightness = brightness;
    this.contrast = contrast;
    this.hue = hue;
    this.saturation = saturation;
  }

  public boolean isEnabled() {
    return isEnabled;
  }

  public int getBrightness() {
    return brightness;
  }

  public int getContrast() {
    return contrast;
  }

  public int getHue() {
    return hue;
  }

  public int getSaturation() {
    return saturation;
  }
}
