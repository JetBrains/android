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
package com.android.tools.idea.resourceExplorer.sketchImporter.logic;

import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchCurvePoint;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchPoint2D;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchShapePath;
import com.android.tools.layoutlib.annotations.NotNull;

import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

public class Path2DBuilder {
  @NotNull
  private Path2D.Double path;

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

    //TODO: Can be optimized by also taking into consideration the S command for bezier curves
  }

  public void createQuadCurve(@NotNull SketchPoint2D controlPoint, @NotNull SketchPoint2D endPoint) {
    path.quadTo(controlPoint.getX(), controlPoint.getY(), endPoint.getX(), endPoint.getY());
  }

  public void createClosedShape(@NotNull SketchShapePath shapePath,
                                @NotNull SketchCurvePoint currentPoint,
                                @NotNull Rectangle2D.Double parentFrame) {
    SketchPoint2D currentPointCurveFrom = currentPoint.getCurveFrom().makeAbsolutePosition(parentFrame, shapePath.getFrame());
    SketchPoint2D firstPointCurveTo = shapePath.getPoints()[0].getCurveTo().makeAbsolutePosition(parentFrame, shapePath.getFrame());
    SketchPoint2D firstPoint = shapePath.getPoints()[0].getPoint().makeAbsolutePosition(parentFrame, shapePath.getFrame());

    createBezierCurve(currentPointCurveFrom, firstPointCurveTo, firstPoint);
  }

  public void closePath() {
    path.closePath();
  }

  @NotNull
  public Path2D.Double build() {
    return path;
  }
}
