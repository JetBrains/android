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
package com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages;

import com.android.tools.idea.resourceExplorer.sketchImporter.converter.builders.Path2DBuilder;
import com.android.tools.idea.resourceExplorer.sketchImporter.converter.models.PathModel;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.interfaces.SketchLayer;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;

import static com.android.tools.idea.resourceExplorer.sketchImporter.parser.deserializers.SketchLayerDeserializer.RECTANGLE_CLASS_TYPE;

/**
 * Refers to objects that have the "_class" field set to be one of the following:
 * <ul>
 * <li>"shapePath"</li>
 * <li>"rectangle"</li>
 * <li>"oval"</li>
 * <li>"star"</li>
 * <li>"polygon"</li>
 * </ul>
 * <p>
 * {@link com.android.tools.idea.resourceExplorer.sketchImporter.parser.deserializers.SketchLayerDeserializer}
 */
public class SketchShapePath extends SketchLayer {
  private final boolean isClosed;
  private final SketchCurvePoint[] points;

  public SketchShapePath(@NotNull String classType,
                         @NotNull String objectId,
                         int booleanOperation,
                         @NotNull SketchExportOptions exportOptions,
                         @NotNull Rectangle.Double frame,
                         boolean isFlippedHorizontal,
                         boolean isFlippedVertical,
                         boolean isVisible,
                         @NotNull String name,
                         int rotation,
                         boolean shouldBreakMaskChain,
                         boolean isClosed,
                         @NotNull SketchCurvePoint[] points) {
    super(classType, objectId, booleanOperation, exportOptions, frame, isFlippedHorizontal, isFlippedVertical, isVisible, name, rotation,
          shouldBreakMaskChain);

    this.isClosed = isClosed;
    this.points = points;
  }

  public boolean isClosed() {
    return isClosed;
  }

  @NotNull
  public SketchCurvePoint[] getPoints() {
    return points;
  }

  @NotNull
  public PathModel createPathModel() {
    return new PathModel(getPath2D(), null, null, isFlippedHorizontal(), isFlippedVertical(), isClosed(), getRotation(),
                         getBooleanOperation(), getFramePosition(), false, false, false);
  }

  @NotNull
  public Point2D.Double getFramePosition() {
    return new Point2D.Double(getFrame().getX(), getFrame().getY());
  }

  @NotNull
  public Path2D.Double getPath2D() {
    if (RECTANGLE_CLASS_TYPE.equals(getClassType())) {
      if (hasRoundCorners()) {
        return getRoundRectanglePath();
      }
      else {
        return getRectanglePath();
      }
    }
    else {
      return getGenericPath();
    }
  }

  //TODO: Add method for oval paths.

  @NotNull
  private Path2D.Double getGenericPath() {
    Path2DBuilder path2DBuilder = new Path2DBuilder();
    SketchCurvePoint[] points = getPoints();
    SketchPoint2D startCoords = points[0].getPoint().makeAbsolutePosition(getFrame());

    path2DBuilder.startPath(startCoords);

    SketchCurvePoint previousCurvePoint;
    SketchCurvePoint currentCurvePoint = points[0];

    for (int i = 1; i < points.length; i++) {
      previousCurvePoint = points[i - 1];
      currentCurvePoint = points[i];

      SketchPoint2D previousPoint = previousCurvePoint.getPoint().makeAbsolutePosition(getFrame());
      SketchPoint2D currentPoint = currentCurvePoint.getPoint().makeAbsolutePosition(getFrame());

      SketchPoint2D previousPointCurveFrom = previousPoint;
      SketchPoint2D currentPointCurveTo = currentPoint;

      if (previousCurvePoint.hasCurveFrom()) {
        previousPointCurveFrom = previousCurvePoint.getCurveFrom().makeAbsolutePosition(getFrame());
      }
      if (currentCurvePoint.hasCurveTo()) {
        currentPointCurveTo = currentCurvePoint.getCurveTo().makeAbsolutePosition(getFrame());
      }

      if (!previousCurvePoint.hasCurveFrom() && !currentCurvePoint.hasCurveTo()) {
        path2DBuilder.createLine(currentPoint);
      }
      else {
        path2DBuilder.createBezierCurve(previousPointCurveFrom, currentPointCurveTo, currentPoint);
      }
    }

    if (isClosed()) {
      path2DBuilder.createClosedShape(this, currentCurvePoint);
    }

    return path2DBuilder.build();
  }

  @NotNull
  private Path2D.Double getRectanglePath() {
    Path2DBuilder path2DBuilder = new Path2DBuilder();
    ArrayList<SketchPoint2D> frameCorners = getRectangleCorners();

    path2DBuilder.startPath(frameCorners.get(0));
    path2DBuilder.createLine(frameCorners.get(1));
    path2DBuilder.createLine(frameCorners.get(2));
    path2DBuilder.createLine(frameCorners.get(3));
    path2DBuilder.closePath();

    return path2DBuilder.build();
  }

  @NotNull
  private Path2D.Double getRoundRectanglePath() {
    Path2DBuilder path2DBuilder = new Path2DBuilder();
    SketchCurvePoint[] points = getPoints();

    SketchPoint2D startPoint = new SketchPoint2D(0, 0);
    SketchPoint2D endPoint = new SketchPoint2D(0, 0);
    SketchPoint2D previousPoint = new SketchPoint2D(0, 0);

    for (int i = 0; i < points.length; i++) {
      switch (i) {
        case 0:
          startPoint.setLocation(0, points[i].getCornerRadius());
          endPoint.setLocation(points[i].getCornerRadius(), 0);
          path2DBuilder.startPath(startPoint);
          break;
        case 1:
          startPoint.setLocation(getFrame().getWidth() - points[i].getCornerRadius(), 0);
          endPoint.setLocation(getFrame().getWidth(), points[i].getCornerRadius());
          break;
        case 2:
          startPoint.setLocation(getFrame().getWidth(), getFrame().getHeight() - points[i].getCornerRadius());
          endPoint.setLocation(getFrame().getWidth() - points[i].getCornerRadius(), getFrame().getHeight());
          break;
        case 3:
          startPoint.setLocation(points[i].getCornerRadius(), getFrame().getHeight());
          endPoint.setLocation(0, getFrame().getHeight() - points[i].getCornerRadius());
          break;
      }

      if (points[i].getCornerRadius() != 0) {
        if (!previousPoint.equals(startPoint) && i != 0) {
          path2DBuilder.createLine(startPoint);
        }
        path2DBuilder.createQuadCurve(points[i].getPoint().makeAbsolutePosition(getFrame()),
                                      endPoint);
      }
      else {
        path2DBuilder.createLine(startPoint.makeAbsolutePosition(getFrame()));
      }

      previousPoint.setLocation(endPoint);
    }

    path2DBuilder.closePath();

    return path2DBuilder.build();
  }

  public boolean hasRoundCorners() {
    SketchCurvePoint[] points = getPoints();

    for (SketchCurvePoint point : points) {
      if (point.getCornerRadius() != 0) {
        return true;
      }
    }

    return false;
  }

  @NotNull
  private ArrayList<SketchPoint2D> getRectangleCorners() {
    ArrayList<SketchPoint2D> corners = new ArrayList<>(4);

    SketchPoint2D upLeftCorner = (new SketchPoint2D(0, 0)).makeAbsolutePosition(getFrame());
    SketchPoint2D upRightCorner = (new SketchPoint2D(1, 0)).makeAbsolutePosition(getFrame());
    SketchPoint2D downRightCorner = (new SketchPoint2D(1, 1)).makeAbsolutePosition(getFrame());
    SketchPoint2D downLeftCorner = (new SketchPoint2D(0, 1)).makeAbsolutePosition(getFrame());

    corners.add(upLeftCorner);
    corners.add(upRightCorner);
    corners.add(downRightCorner);
    corners.add(downLeftCorner);

    return corners;
  }
}
