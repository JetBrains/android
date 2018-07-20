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

import java.awt.geom.Point2D;

public class SketchCurvePoint {
  @SerializedName("do_objectID")
  private final int objectId;
  private final int cornerRadius;
  private final Point2D.Double curveFrom;
  private final short curveMode;
  private final Point2D.Double curveTo;
  private final boolean hasCurveFrom;
  private final boolean hasCurveTo;
  private final Point2D.Double point;

  public SketchCurvePoint(int objectId,
                          int cornerRadius,
                          @NotNull Point2D.Double curveFrom,
                          short curveMode,
                          @NotNull Point2D.Double curveTo,
                          boolean hasCurveFrom,
                          boolean hasCurveTo,
                          @NotNull Point2D.Double point) {
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

  public Point2D.Double getCurveFrom() {
    return curveFrom;
  }

  public short getCurveMode() {
    return curveMode;
  }

  public Point2D.Double getCurveTo() {
    return curveTo;
  }

  public boolean hasCurveFrom() {
    return hasCurveFrom;
  }

  public boolean hasCurveTo() {
    return hasCurveTo;
  }

  public Point2D.Double getPoint() {
    return point;
  }
}
