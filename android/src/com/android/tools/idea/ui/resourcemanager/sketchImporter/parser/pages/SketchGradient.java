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

import org.jetbrains.annotations.NotNull;

/**
 * Mimics the JSON element with attribute <code>"_class": "gradient"</code> contained within a sketch file.
 */
public class SketchGradient {
  private final int elipseLength;
  /**
   * Linear: 0
   * Radial: 1
   * Angular: 2
   */
  private final int gradientType;
  private final SketchGradientStop[] stops;
  private final SketchPoint2D from;
  private final SketchPoint2D to;

  public SketchGradient(int elipseLength,
                        @NotNull SketchPoint2D from,
                        int gradientType,
                        @NotNull SketchGradientStop[] stops,
                        @NotNull SketchPoint2D to) {
    this.elipseLength = elipseLength;
    this.from = from;
    this.gradientType = gradientType;
    this.stops = stops;
    this.to = to;
  }

  public int getElipseLength() {
    return elipseLength;
  }

  @NotNull
  public SketchPoint2D getFrom() {
    return from;
  }

  public int getGradientType() {
    return gradientType;
  }

  @NotNull
  public SketchGradientStop[] getStops() {
    return stops;
  }

  @NotNull
  public SketchPoint2D getTo() {
    return to;
  }
}
