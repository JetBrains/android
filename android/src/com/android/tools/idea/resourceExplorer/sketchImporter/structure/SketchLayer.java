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

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public abstract class SketchLayer {
  @SerializedName("_class")
  private final String classType;
  @SerializedName("do_objectID")
  private final String objectId;
  private final int booleanOperation;
  private final Rectangle.Double frame;
  private final boolean isFlippedHorizontal;
  private final boolean isFlippedVertical;
  private final boolean isVisible;
  private final String name;
  /**
   * rotation in degrees (counter-clockwise [0, 359]
   * [0, 359] is sometimes equivalent to [0, 180] U [-179, -1] for some reason
   */
  private final int rotation;
  private final boolean shouldBreakMaskChain;  // TODO what does this do?

  public SketchLayer(@NotNull String classType,
                     @NotNull String objectId,
                     int booleanOperation,
                     @NotNull Rectangle.Double frame,
                     boolean isFlippedHorizontal,
                     boolean isFlippedVertical,
                     boolean isVisible,
                     @NotNull String name,
                     int rotation,
                     boolean shouldBreakMaskChain) {
    this.classType = classType;
    this.objectId = objectId;
    this.booleanOperation = booleanOperation;
    this.frame = frame;
    this.isFlippedHorizontal = isFlippedHorizontal;
    this.isFlippedVertical = isFlippedVertical;
    this.isVisible = isVisible;
    this.name = name;
    this.rotation = rotation;
    this.shouldBreakMaskChain = shouldBreakMaskChain;
  }

  public String getClassType() {
    return classType;
  }

  public String getObjectId() {
    return objectId;
  }

  public int getBooleanOperation() {
    return booleanOperation;
  }

  public Rectangle.Double getFrame() {
    return frame;
  }

  public boolean isFlippedHorizontal() {
    return isFlippedHorizontal;
  }

  public boolean isFlippedVertical() {
    return isFlippedVertical;
  }

  public boolean isVisible() {
    return isVisible;
  }

  public String getName() {
    return name;
  }

  public int getRotation() {
    return rotation;
  }

  public boolean shouldBreakMaskChain() {
    return shouldBreakMaskChain;
  }
}

