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

import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.*;

import static java.awt.geom.PathIterator.*;

public class PathUtils {

  @NotNull
  public static String toStringPath(@NotNull Shape shape) {
    PathStringBuilder pathStringBuilder = new PathStringBuilder();
    PathIterator pathIterator = shape.getPathIterator(null);

    while (!pathIterator.isDone()) {
      double[] coordinates = new double[6];
      int type = pathIterator.currentSegment(coordinates);

      switch (type) {
        case SEG_MOVETO:
          pathStringBuilder.startPath(coordinates[0], coordinates[1]);
          break;
        case SEG_LINETO:
          pathStringBuilder.createLine(coordinates[0], coordinates[1]);
          break;
        case SEG_CUBICTO:
          pathStringBuilder.createBezierCurve(coordinates);
          break;
        case SEG_QUADTO:
          pathStringBuilder.createQuadCurve(coordinates[0], coordinates[1], coordinates[2], coordinates[3]);
          break;
        case SEG_CLOSE:
          pathStringBuilder.endPath();
          break;
      }

      pathIterator.next();
    }
    return pathStringBuilder.build();
  }

  @NotNull
  public static Area rotateArea(@NotNull Area area, @NotNull int degrees) {
    AffineTransform shapeRotation = computeRotationAffineTransform(area, degrees);
    area.transform(shapeRotation);
    return area;
  }

  @NotNull
  public static Path2D.Double rotatePath(@NotNull Path2D.Double path, @NotNull int degrees) {
    AffineTransform shapeRotation = computeRotationAffineTransform(path, degrees);
    path.transform(shapeRotation);
    return path;
  }

  @NotNull
  private static AffineTransform computeRotationAffineTransform(@NotNull Shape path, @NotNull int degrees) {
    AffineTransform shapeRotation = new AffineTransform();
    shapeRotation.setToIdentity();

    double anchorPointX = path.getBounds2D().getCenterX();
    double anchorPointY = path.getBounds2D().getCenterY();
    shapeRotation.rotate(Math.toRadians(-degrees), anchorPointX, anchorPointY);

    return shapeRotation;
  }

  @NotNull
  public static Area flipArea(@NotNull Area area, boolean isFlippedHorizontally, boolean isFlippedVertically) {
    AffineTransform shapeMirroring = new AffineTransform();
    shapeMirroring.setToIdentity();

    Rectangle2D bounds = area.getBounds2D();
    if (isFlippedHorizontally) {
      shapeMirroring.scale(-1, 1);
      shapeMirroring.translate(-(bounds.getWidth() + 2 * bounds.getX()), 0);
    }
    if (isFlippedVertically) {
      shapeMirroring.scale(1, -1);
      shapeMirroring.translate(0, -(bounds.getHeight() + 2 * bounds.getY()));
    }

    area.transform(shapeMirroring);

    return area;
  }

  @NotNull
  public static Path2D.Double flipPath(@NotNull Path2D.Double path, boolean isFlippedHorizontally, boolean isFlippedVertically) {
    AffineTransform shapeMirroring = new AffineTransform();
    shapeMirroring.setToIdentity();

    Rectangle2D bounds = path.getBounds2D();
    if (isFlippedHorizontally) {
      shapeMirroring.scale(-1, 1);
      shapeMirroring.translate(-(bounds.getWidth() + 2 * bounds.getX()), 0);
    }

    if (isFlippedVertically) {
      shapeMirroring.scale(1, -1);
      shapeMirroring.translate(0, -(bounds.getHeight() + 2 * bounds.getY()));
    }

    path.transform(shapeMirroring);

    return path;
  }
}
