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

import com.android.tools.idea.resourceExplorer.sketchImporter.logic.Path2DBuilder;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;

import static com.android.tools.idea.resourceExplorer.sketchImporter.structure.deserializers.SketchLayerDeserializer.OVAL_CLASS_TYPE;
import static com.android.tools.idea.resourceExplorer.sketchImporter.structure.deserializers.SketchLayerDeserializer.RECTANGLE_CLASS_TYPE;

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
 * {@link com.android.tools.idea.resourceExplorer.sketchImporter.structure.deserializers.SketchLayerDeserializer}
 */
public class SketchShapePath extends SketchLayer {
  private final boolean isClosed;
  private final SketchCurvePoint[] points;

  public SketchShapePath(@NotNull String classType,
                         @NotNull String objectId,
                         int booleanOperation,
                         @NotNull Rectangle.Double frame,
                         boolean isFlippedHorizontal,
                         boolean isFlippedVertical,
                         boolean isVisible,
                         @NotNull String name,
                         int rotation,
                         boolean shouldBreakMaskChain,
                         boolean isClosed,
                         @NotNull SketchCurvePoint[] points) {
    super(classType, objectId, booleanOperation, frame, isFlippedHorizontal, isFlippedVertical, isVisible, name, rotation,
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

  public Path2D.Double toPath2D(Rectangle2D.Double parentFrame) {

    if (RECTANGLE_CLASS_TYPE.equals(getClassType())) {
      if (hasRoundCorners()) {

        return toRoundRectangle(parentFrame);
      }
      else {
        return toRectangle(parentFrame);
      }
    }
    else {
      return toGenericPath(parentFrame);
    }
  }

  public Path2D.Double toGenericPath(Rectangle.Double parentFrame) {
    Path2DBuilder path2DBuilder = new Path2DBuilder();

    SketchCurvePoint[] points = getPoints();

    SketchPoint2D startCoords = points[0].getPoint().makeAbsolutePosition(parentFrame, getFrame());

    path2DBuilder.startPath(startCoords);

    SketchCurvePoint previousCurvePoint;
    SketchCurvePoint currentCurvePoint = points[0];

    for (int i = 1; i < points.length; i++) {

      previousCurvePoint = points[i - 1];
      currentCurvePoint = points[i];

      SketchPoint2D previousPoint = previousCurvePoint.getPoint().makeAbsolutePosition(parentFrame, getFrame());
      SketchPoint2D currentPoint = currentCurvePoint.getPoint().makeAbsolutePosition(parentFrame, getFrame());


      SketchPoint2D previousPointCurveFrom = previousPoint;
      SketchPoint2D currentPointCurveTo = currentPoint;

      if (previousCurvePoint.hasCurveFrom()) {
        previousPointCurveFrom = previousCurvePoint.getCurveFrom().makeAbsolutePosition(parentFrame, getFrame());
      }
      if (currentCurvePoint.hasCurveTo()) {
        currentPointCurveTo = currentCurvePoint.getCurveTo().makeAbsolutePosition(parentFrame, getFrame());
      }

      if (!previousCurvePoint.hasCurveTo() && !currentCurvePoint.hasCurveFrom()) {
        path2DBuilder.createLine(currentPoint);
      }
      else {
        path2DBuilder.createBezierCurve(previousPointCurveFrom, currentPointCurveTo, currentPoint);
      }
    }

    if (OVAL_CLASS_TYPE.equals(getClassType())) {
      path2DBuilder.createClosedOval(this, currentCurvePoint, parentFrame);
    }

    if (isClosed()) {
      path2DBuilder.closePath();
    }

    return path2DBuilder.build();
  }

  public Path2D.Double toRectangle(Rectangle.Double parentFrame) {

    Path2DBuilder path2DBuilder = new Path2DBuilder();

    ArrayList<SketchPoint2D> frameCorners = getRectangleCorners(parentFrame);

    path2DBuilder.startPath(frameCorners.get(0));
    path2DBuilder.createLine(frameCorners.get(1));
    path2DBuilder.createLine(frameCorners.get(2));
    path2DBuilder.createLine(frameCorners.get(3));
    path2DBuilder.closePath();

    return path2DBuilder.build();
  }

  public Path2D.Double toRoundRectangle(Rectangle.Double parentFrame) {

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
          path2DBuilder.startPath(startPoint.makeAbsolutePosition(parentFrame));
          break;
        case 1:
          startPoint.setLocation(parentFrame.getWidth() - points[i].getCornerRadius(), 0);
          endPoint.setLocation(parentFrame.getWidth(), points[i].getCornerRadius());
          break;
        case 2:
          startPoint.setLocation(parentFrame.getWidth(), parentFrame.getHeight() - points[i].getCornerRadius());
          endPoint.setLocation(parentFrame.getWidth() - points[i].getCornerRadius(), parentFrame.getHeight());
          break;
        case 3:
          startPoint.setLocation(points[i].getCornerRadius(), parentFrame.getHeight());
          endPoint.setLocation(0, parentFrame.getHeight() - points[i].getCornerRadius());
          break;
      }

      if (points[i].getCornerRadius() != 0) {
        if (!previousPoint.equals(startPoint) && i != 0) {
          path2DBuilder.createLine(startPoint.makeAbsolutePosition(parentFrame));
        }
        path2DBuilder.createQuadCurve(points[i].getPoint().makeAbsolutePosition(parentFrame, getFrame()),
                                      endPoint.makeAbsolutePosition(parentFrame));
      }
      else {
        path2DBuilder.createLine(startPoint.makeAbsolutePosition(parentFrame));
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

  private ArrayList<SketchPoint2D> getRectangleCorners(@NotNull Rectangle.Double shapeGroupFrame) {

    ArrayList<SketchPoint2D> corners = new ArrayList<>();

    SketchPoint2D upLeftCorner = (new SketchPoint2D(0, 0)).makeAbsolutePosition(shapeGroupFrame, getFrame());
    SketchPoint2D upRightCorner = (new SketchPoint2D(1, 0)).makeAbsolutePosition(shapeGroupFrame, getFrame());
    SketchPoint2D downRightCorner = (new SketchPoint2D(1, 1)).makeAbsolutePosition(shapeGroupFrame, getFrame());
    SketchPoint2D downLeftCorner = (new SketchPoint2D(0, 1)).makeAbsolutePosition(shapeGroupFrame, getFrame());

    corners.add(upLeftCorner);
    corners.add(upRightCorner);
    corners.add(downRightCorner);
    corners.add(downLeftCorner);

    return corners;
  }
}
