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
package com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.builders;

import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchCurvePoint;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchPoint2D;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchShapePath;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchCurvePoint;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchPoint2D;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchShapePath;
import java.awt.geom.Path2D;
import org.jetbrains.annotations.NotNull;

public class Path2DBuilder {

  @NotNull private Path2D.Double path;

  public Path2DBuilder() {
    path = new Path2D.Double();
  }

  public void startPath(@NotNull SketchPoint2D startPoint) {
    path.moveTo(startPoint.getX(), startPoint.getY());
  }

  public void createLine(@NotNull SketchPoint2D currentPoint) {
    path.lineTo(currentPoint.getX(), currentPoint.getY());
  }

  public void createBezierCurve(@NotNull SketchPoint2D curveFrom, @NotNull SketchPoint2D curveTo, @NotNull SketchPoint2D destination) {
    path.curveTo(curveFrom.getX(), curveFrom.getY(), curveTo.getX(), curveTo.getY(), destination.getX(), destination.getY());
  }

  public void createQuadCurve(@NotNull SketchPoint2D controlPoint, @NotNull SketchPoint2D endPoint) {
    path.quadTo(controlPoint.getX(), controlPoint.getY(), endPoint.getX(), endPoint.getY());
  }

  public void createClosedShape(@NotNull SketchShapePath shapePath,
                                @NotNull SketchCurvePoint currentCurvePoint) {
    SketchPoint2D currentPointCurveFrom;
    if (currentCurvePoint.hasCurveFrom()) {
      currentPointCurveFrom = currentCurvePoint.getCurveFrom().makeAbsolutePosition(shapePath.getFrame());
    }
    else {
      currentPointCurveFrom = currentCurvePoint.getPoint().makeAbsolutePosition(shapePath.getFrame());
    }
    SketchCurvePoint firstCurvePoint = shapePath.getPoints()[0];
    SketchPoint2D firstPoint = firstCurvePoint.getPoint().makeAbsolutePosition(shapePath.getFrame());
    SketchPoint2D firstPointCurveTo;
    if (firstCurvePoint.hasCurveTo()) {
      firstPointCurveTo = firstCurvePoint.getCurveTo().makeAbsolutePosition(shapePath.getFrame());
    }
    else {
      firstPointCurveTo = firstPoint;
    }

    if (currentCurvePoint.equals(currentPointCurveFrom) && firstPoint.equals(firstPointCurveTo)) {
      closePath();
    }
    else {
      createBezierCurve(currentPointCurveFrom, firstPointCurveTo, firstPoint);
    }
  }

  public void closePath() {
    path.closePath();
  }

  @NotNull
  public Path2D.Double build() {
    return path;
  }
}
