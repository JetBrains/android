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
package com.android.tools.idea.resourceExplorer.sketchImporter.converter.model_converters;

import static com.android.tools.idea.resourceExplorer.sketchImporter.converter.model_converters.ShapeToDrawableConverter.createDrawableShape;
import static com.android.tools.idea.resourceExplorer.sketchImporter.parser.deserializers.SketchLayerDeserializer.RECTANGLE_CLASS_TYPE;

import com.android.tools.idea.resourceExplorer.sketchImporter.converter.SymbolsLibrary;
import com.android.tools.idea.resourceExplorer.sketchImporter.converter.builders.Path2DBuilder;
import com.android.tools.idea.resourceExplorer.sketchImporter.converter.models.AreaModel;
import com.android.tools.idea.resourceExplorer.sketchImporter.converter.models.DrawableModel;
import com.android.tools.idea.resourceExplorer.sketchImporter.converter.models.PathModel;
import com.android.tools.idea.resourceExplorer.sketchImporter.converter.models.ShapeModel;
import com.android.tools.idea.resourceExplorer.sketchImporter.converter.models.SymbolModel;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.interfaces.SketchLayer;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.interfaces.SketchLayerable;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchArtboard;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchBorder;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchCurvePoint;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchFill;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchGraphicsContextSettings;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchPage;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchPoint2D;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchShapeGroup;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchShapePath;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchSymbolInstance;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchSymbolMaster;
import com.google.common.collect.ImmutableList;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;

public class SketchToShapeConverter {

  public static final int DEFAULT_OPACITY = 1;

  @NotNull
  public static ImmutableList<DrawableModel> createAllDrawableShapes(@NotNull SketchArtboard artboard,
                                                                     @NotNull SymbolsLibrary symbolsLibrary) {
    ImmutableList.Builder<DrawableModel> drawableShapes = new ImmutableList.Builder<>();

    for (ShapeModel shapeModel : createAllShapeModels(artboard, symbolsLibrary)) {
      drawableShapes.add(createDrawableShape(shapeModel));
    }

    return drawableShapes.build();
  }

  @NotNull
  private static ImmutableList<ShapeModel> createAllShapeModels(@NotNull SketchArtboard artboard, @NotNull SymbolsLibrary symbolsLibrary) {
    ImmutableList.Builder<ShapeModel> shapes = new ImmutableList.Builder<>();
    SketchLayer[] layers = artboard.getLayers();

    for (SketchLayer layer : layers) {
      if (!symbolsLibrary.isEmpty() && layer instanceof SketchSymbolInstance) {
        shapes.addAll(createShapeModelsFromSymbol((SketchSymbolInstance)layer, symbolsLibrary));
      }

      if (layer instanceof SketchShapeGroup) {
        shapes.addAll(createShapeModelsFromShapeGroup((SketchShapeGroup)layer, new Point2D.Double(), false, DEFAULT_OPACITY));
      }
      else if (layer instanceof SketchPage) {
        shapes.addAll(createShapeModelsFromLayerable((SketchLayerable)layer, new Point2D.Double(), DEFAULT_OPACITY, symbolsLibrary));
      }
    }

    return shapes.build();
  }

  @NotNull
  private static ImmutableList<ShapeModel> createShapeModelsFromSymbol(SketchSymbolInstance symbolInstance, SymbolsLibrary symbolsLibrary) {
    SymbolModel symbolModel = symbolsLibrary.getSymbolModel(symbolInstance.getSymbolId());
    symbolModel.setSymbolInstance(symbolInstance);
    symbolModel.scaleShapes();
    symbolModel.translateShapes();
    return symbolModel.getShapeModels();
  }

  /**
   * Method that generates the shape model of the shape in the SketchShapeGroup object.
   * <p>
   * Shape operations can only be performed on Area objects, and to make sure that the conversion
   * between {@link Path2D.Double} to {@link java.awt.geom.Area} is correct, the {@link Path2D.Double} object must be closed
   */
  @NotNull
  private static ImmutableList<ShapeModel> createShapeModelsFromShapeGroup(@NotNull SketchShapeGroup shapeGroup,
                                                                           @NotNull Point2D.Double parentCoords,
                                                                           boolean isLastShapeGroup,
                                                                           double parentOpacity) {
    SketchFill[] fills = shapeGroup.getStyle().getFills();
    SketchBorder[] borders = shapeGroup.getStyle().getBorders();
    SketchFill shapeGroupFill = fills != null ? fills[0] : null;
    SketchBorder shapeGroupBorder = borders != null ? borders[0] : null;

    // If the shape does not have a fill or border, it will not be visible in the VectorDrawable file. However,
    // clipping paths don't need fills and colors to have an effect, but they still need to be included in the
    // DrawableModel list.
    if (shapeGroupBorder == null && shapeGroupFill == null && !shapeGroup.hasClippingMask()) {
      return ImmutableList.of();
    }

    Point2D.Double newParentCoords = new Point2D.Double(parentCoords.getX() + shapeGroup.getFrame().getX(),
                                                        parentCoords.getY() + shapeGroup.getFrame().getY());

    SketchLayer[] layers = shapeGroup.getLayers();
    SketchShapePath baseSketchShapePath = (SketchShapePath)layers[0];

    Path2D.Double baseShapePath = getPath2D(baseSketchShapePath);
    PathModel finalShape = new PathModel(baseShapePath,
                                         shapeGroup.getStyle(),
                                         baseSketchShapePath.isFlippedHorizontal(),
                                         baseSketchShapePath.isFlippedVertical(),
                                         baseSketchShapePath.isClosed(),
                                         baseSketchShapePath.getRotation(),
                                         shapeGroup.getBooleanOperation(),
                                         baseSketchShapePath.getFramePosition(),
                                         shapeGroup.hasClippingMask(),
                                         shapeGroup.shouldBreakMaskChain(),
                                         isLastShapeGroup,
                                         parentOpacity);

    // If the shapegroup has just one layer, there will be no shape operation.
    // Therefore, no conversion to area needed.
    // Therefore, the path does not necessarily have to be closed.
    if (layers.length == 1) {
      return ImmutableList.of(transformShapeGroup(shapeGroup, finalShape, newParentCoords));
    }

    // If the shapegroup has multiple layers, there definitely are some shape operations to be performed.
    // Therefore, the path needs to be closed and converted into an Area before applying anything.
    AreaModel finalArea = finalShape.convertToArea();
    finalArea.applyTransformations();

    for (int i = 1; i < layers.length; i++) {
      SketchShapePath path = (SketchShapePath)layers[i];

      PathModel pathModel = createPathModel(path);
      AreaModel areaModel = pathModel.convertToArea();
      areaModel.applyTransformations();
      finalArea.applyOperation(areaModel);
    }

    // The shapeGroup itself and its components altogether can be rotated or flipped.
    return ImmutableList.of(transformShapeGroup(shapeGroup, finalArea, newParentCoords));
  }

  @NotNull
  private static PathModel createPathModel(@NotNull SketchShapePath shapePath) {
    return new PathModel(getPath2D(shapePath), null, shapePath.isFlippedHorizontal(), shapePath.isFlippedVertical(),
                         shapePath.isClosed(), shapePath.getRotation(),
                         shapePath.getBooleanOperation(), shapePath.getFramePosition(), false, false, false, DEFAULT_OPACITY);
  }

  @NotNull
  public static ImmutableList<ShapeModel> createShapeModelsFromLayerable(@NotNull SketchLayerable layerable,
                                                                         @NotNull Point2D.Double parentCoords,
                                                                         double parentOpacity,
                                                                         @NotNull SymbolsLibrary symbolsLibrary) {
    Point2D.Double newParentCoords;

    // The SketchSymbolMaster in a page has its own frame and position that have nothing
    // to do with where the instance is placed. Therefore, if the layer is a symbol master,
    // we ignore its position
    if (layerable instanceof SketchSymbolMaster) {
      newParentCoords = parentCoords;
    }
    // However, if the layer is not a SymbolMaster, it is a layerable object inside an artboard
    // whose position is relevant and must be added to the new parent coordinates.
    else {
      newParentCoords = new Point2D.Double(parentCoords.getX() + layerable.getFrame().getX(),
                                           parentCoords.getY() + layerable.getFrame().getY());
    }

    SketchGraphicsContextSettings graphicContextSettings = layerable.getStyle().getContextSettings();
    if (graphicContextSettings != null) {
      parentOpacity *= graphicContextSettings.getOpacity();
    }
    ImmutableList.Builder<ShapeModel> builder = new ImmutableList.Builder<>();

    boolean isLastGroupElement = false;
    SketchLayer[] groupLayers = layerable.getLayers();
    for (int i = 0; i < groupLayers.length; i++) {
      if (i == groupLayers.length - 1) {
        isLastGroupElement = true;
      }
      SketchLayer layer = groupLayers[i];
      if (!symbolsLibrary.isEmpty() && layer instanceof SketchSymbolInstance) {
        builder.addAll(createShapeModelsFromSymbol((SketchSymbolInstance)layer, symbolsLibrary));
      }

      if (layer instanceof SketchShapeGroup) {
        builder.addAll(createShapeModelsFromShapeGroup((SketchShapeGroup)layer, newParentCoords, isLastGroupElement, parentOpacity));
      }
      else if (layer instanceof SketchPage) {
        builder.addAll(createShapeModelsFromLayerable((SketchPage)layer, newParentCoords, parentOpacity, symbolsLibrary));
      }
    }

    return builder.build();
  }

  @NotNull
  private static ShapeModel transformShapeGroup(@NotNull SketchShapeGroup shapeGroup,
                                                @NotNull ShapeModel finalShape,
                                                @NotNull Point2D.Double newParentCoords) {
    finalShape.setRotation(shapeGroup.getRotation());
    finalShape.setMirroring(shapeGroup.isFlippedHorizontal(), shapeGroup.isFlippedVertical());
    finalShape.setTranslation(newParentCoords);
    finalShape.applyTransformations();

    return finalShape;
  }

  @NotNull
  private static Path2D.Double getPath2D(@NotNull SketchShapePath shapePath) {
    if (RECTANGLE_CLASS_TYPE.equals(shapePath.getClassType())) {
      if (hasRoundCorners(shapePath)) {
        return getRoundRectanglePath(shapePath);
      }
      else {
        return getRectanglePath(shapePath);
      }
    }
    else {
      return getGenericPath(shapePath);
    }
  }

  //TODO: Add method for oval paths.

  @NotNull
  private static Path2D.Double getGenericPath(@NotNull SketchShapePath shapePath) {
    Path2DBuilder path2DBuilder = new Path2DBuilder();
    SketchCurvePoint[] points = shapePath.getPoints();
    if (points.length == 0) {
      return new Path2D.Double();
    }
    SketchPoint2D startCoords = points[0].getPoint().makeAbsolutePosition(shapePath.getFrame());

    path2DBuilder.startPath(startCoords);

    SketchCurvePoint previousCurvePoint;
    SketchCurvePoint currentCurvePoint = points[0];

    for (int i = 1; i < points.length; i++) {
      previousCurvePoint = points[i - 1];
      currentCurvePoint = points[i];

      SketchPoint2D previousPoint = previousCurvePoint.getPoint().makeAbsolutePosition(shapePath.getFrame());
      SketchPoint2D currentPoint = currentCurvePoint.getPoint().makeAbsolutePosition(shapePath.getFrame());

      SketchPoint2D previousPointCurveFrom = previousPoint;
      SketchPoint2D currentPointCurveTo = currentPoint;

      if (previousCurvePoint.hasCurveFrom()) {
        previousPointCurveFrom = previousCurvePoint.getCurveFrom().makeAbsolutePosition(shapePath.getFrame());
      }
      if (currentCurvePoint.hasCurveTo()) {
        currentPointCurveTo = currentCurvePoint.getCurveTo().makeAbsolutePosition(shapePath.getFrame());
      }

      if (!previousCurvePoint.hasCurveFrom() && !currentCurvePoint.hasCurveTo()) {
        path2DBuilder.createLine(currentPoint);
      }
      else {
        path2DBuilder.createBezierCurve(previousPointCurveFrom, currentPointCurveTo, currentPoint);
      }
    }

    if (shapePath.isClosed()) {
      path2DBuilder.createClosedShape(shapePath, currentCurvePoint);
    }

    return path2DBuilder.build();
  }

  @NotNull
  private static Path2D.Double getRectanglePath(@NotNull SketchShapePath shapePath) {
    Path2DBuilder path2DBuilder = new Path2DBuilder();
    ArrayList<SketchPoint2D> frameCorners = getRectangleCorners(shapePath);

    path2DBuilder.startPath(frameCorners.get(0));
    path2DBuilder.createLine(frameCorners.get(1));
    path2DBuilder.createLine(frameCorners.get(2));
    path2DBuilder.createLine(frameCorners.get(3));
    path2DBuilder.closePath();

    return path2DBuilder.build();
  }

  @NotNull
  private static Path2D.Double getRoundRectanglePath(@NotNull SketchShapePath shapePath) {
    Path2DBuilder path2DBuilder = new Path2DBuilder();
    SketchCurvePoint[] points = shapePath.getPoints();

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
          startPoint.setLocation(shapePath.getFrame().getWidth() - points[i].getCornerRadius(), 0);
          endPoint.setLocation(shapePath.getFrame().getWidth(), points[i].getCornerRadius());
          break;
        case 2:
          startPoint.setLocation(shapePath.getFrame().getWidth(), shapePath.getFrame().getHeight() - points[i].getCornerRadius());
          endPoint.setLocation(shapePath.getFrame().getWidth() - points[i].getCornerRadius(), shapePath.getFrame().getHeight());
          break;
        case 3:
          startPoint.setLocation(points[i].getCornerRadius(), shapePath.getFrame().getHeight());
          endPoint.setLocation(0, shapePath.getFrame().getHeight() - points[i].getCornerRadius());
          break;
      }

      if (points[i].getCornerRadius() != 0) {
        if (!previousPoint.equals(startPoint) && i != 0) {
          path2DBuilder.createLine(startPoint);
        }
        path2DBuilder.createQuadCurve(points[i].getPoint().makeAbsolutePosition(shapePath.getFrame()),
                                      endPoint);
      }
      else {
        path2DBuilder.createLine(startPoint.makeAbsolutePosition(shapePath.getFrame()));
      }

      previousPoint.setLocation(endPoint);
    }

    path2DBuilder.closePath();

    return path2DBuilder.build();
  }

  private static boolean hasRoundCorners(@NotNull SketchShapePath shapePath) {
    SketchCurvePoint[] points = shapePath.getPoints();

    for (SketchCurvePoint point : points) {
      if (point.getCornerRadius() != 0) {
        return true;
      }
    }

    return false;
  }

  @NotNull
  private static ArrayList<SketchPoint2D> getRectangleCorners(@NotNull SketchShapePath shapePath) {
    ArrayList<SketchPoint2D> corners = new ArrayList<>(4);

    SketchPoint2D upLeftCorner = (new SketchPoint2D(0, 0)).makeAbsolutePosition(shapePath.getFrame());
    SketchPoint2D upRightCorner = (new SketchPoint2D(1, 0)).makeAbsolutePosition(shapePath.getFrame());
    SketchPoint2D downRightCorner = (new SketchPoint2D(1, 1)).makeAbsolutePosition(shapePath.getFrame());
    SketchPoint2D downLeftCorner = (new SketchPoint2D(0, 1)).makeAbsolutePosition(shapePath.getFrame());

    corners.add(upLeftCorner);
    corners.add(upRightCorner);
    corners.add(downRightCorner);
    corners.add(downLeftCorner);

    return corners;
  }
}
