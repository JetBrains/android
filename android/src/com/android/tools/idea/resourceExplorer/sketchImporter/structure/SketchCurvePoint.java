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

public class SketchCurvePoint {
  @SerializedName("do_objectID")
  private final int objectId;
  private final int cornerRadius;
  private final String curveFrom;
  private final short curveMode;
  private final String curveTo;
  private final boolean hasCurveFrom;
  private final boolean hasCurveTo;
  private final String point;

  public SketchCurvePoint(int objectId,
                          int cornerRadius,
                          @NotNull String curveFrom,
                          short curveMode,
                          @NotNull String curveTo,
                          boolean from,
                          boolean hasCurveTo,
                          @NotNull String point) {
    this.objectId = objectId;
    this.cornerRadius = cornerRadius;
    this.curveFrom = curveFrom;
    this.curveMode = curveMode;
    this.curveTo = curveTo;
    hasCurveFrom = from;
    this.hasCurveTo = hasCurveTo;
    this.point = point;
  }

  public int getObjectId() {
    return objectId;
  }

  public int getCornerRadius() {
    return cornerRadius;
  }

  public String getCurveFrom() {
    return curveFrom;
  }

  public short getCurveMode() {
    return curveMode;
  }

  public String getCurveTo() {
    return curveTo;
  }

  public boolean isHasCurveFrom() {
    return hasCurveFrom;
  }

  public boolean isHasCurveTo() {
    return hasCurveTo;
  }

  public String getPoint() {
    return point;
  }
}
