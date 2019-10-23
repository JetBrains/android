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
 * Mimics the JSON element with attribute <code>"_class": "blur"</code> contained within a sketch file.
 */
public class SketchBlur {
  private final boolean isEnabled;
  private final SketchPoint2D center;
  private final int motionAngle;
  private final int radius;
  private final short type;

  public SketchBlur(boolean isEnabled, @NotNull SketchPoint2D center, int motionAngle, int radius, short type) {
    this.isEnabled = isEnabled;
    this.center = center;
    this.motionAngle = motionAngle;
    this.radius = radius;
    this.type = type;
  }

  public boolean isEnabled() {
    return isEnabled;
  }

  @NotNull
  public SketchPoint2D getCenter() {
    return center;
  }

  public int getMotionAngle() {
    return motionAngle;
  }

  public int getRadius() {
    return radius;
  }

  public short getType() {
    return type;
  }
}
