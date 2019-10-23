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
 * Mimics the JSON element with attribute <code>"_class": "border"</code> contained within a sketch file.
 */
public class SketchBorder {
  private final boolean isEnabled;
  private final short fillType;
  private final int position;
  private final int thickness;
  private final Color color;

  public SketchBorder(boolean isEnabled, @NotNull Color color, short fillType, int position, int thickness) {
    this.isEnabled = isEnabled;
    this.color = color;
    this.fillType = fillType;
    this.position = position;
    this.thickness = thickness;
  }

  public boolean isEnabled() {
    return isEnabled;
  }

  @NotNull
  public Color getColor() {
    return color;
  }

  public short getFillType() {
    return fillType;
  }

  public int getPosition() {
    return position;
  }

  public int getThickness() {
    return thickness;
  }
}
