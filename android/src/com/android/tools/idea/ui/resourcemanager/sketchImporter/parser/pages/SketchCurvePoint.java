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

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;

/**
 * Mimics the JSON element with attribute <code>"_class": "curvePoint"</code> contained within a sketch file.
 */
public class SketchCurvePoint {
  @SerializedName("do_objectID")
  private final int objectId;
  private final int cornerRadius;
  private final SketchPoint2D curveFrom;
  private final short curveMode;
  private final SketchPoint2D curveTo;
  private final boolean hasCurveFrom;
  private final boolean hasCurveTo;
  private final SketchPoint2D point;

  public SketchCurvePoint(int objectId,
                          int cornerRadius,
                          @NotNull SketchPoint2D curveFrom,
                          short curveMode,
                          @NotNull SketchPoint2D curveTo,
                          boolean hasCurveFrom,
                          boolean hasCurveTo,
                          @NotNull SketchPoint2D point) {
    this.objectId = objectId;
    this.cornerRadius = cornerRadius;
    this.curveFrom = curveFrom;
    this.curveMode = curveMode;
    this.curveTo = curveTo;
    this.hasCurveFrom = hasCurveFrom;
    this.hasCurveTo = hasCurveTo;
    this.point = point;
  }

  public int getObjectId() {
    return objectId;
  }

  public int getCornerRadius() {
    return cornerRadius;
  }

  @NotNull
  public SketchPoint2D getCurveFrom() {
    return curveFrom;
  }

  public short getCurveMode() {
    return curveMode;
  }

  @NotNull
  public SketchPoint2D getCurveTo() {
    return curveTo;
  }

  public boolean hasCurveFrom() {
    return hasCurveFrom;
  }

  public boolean hasCurveTo() {
    return hasCurveTo;
  }

  @NotNull
  public SketchPoint2D getPoint() {
    return point;
  }
}