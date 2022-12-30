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

import static com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.deserializers.SketchLayerDeserializer.RECTANGLE_CLASS_TYPE;

import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.SketchLibrary;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.AreaModel;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.AssetModel;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.BorderModel;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.ColorAssetModel;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.DrawableAssetModel;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.FillModel;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.GradientModel;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.GradientStopModel;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.InheritedProperties;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.PathModel;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.ResizingConstraint;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.ShapeModel;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.StudioResourcesModel;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.StyleModel;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.SymbolModel;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.document.SketchDocument;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.document.SketchForeignSymbol;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.document.SketchSharedStyle;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.interfaces.SketchLayer;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.interfaces.SketchLayerable;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchArtboard;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchBorder;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchCurvePoint;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchExportFormat;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchFill;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchGradient;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchGradientStop;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchGraphicsContextSettings;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchPage;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchPoint2D;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchShapeGroup;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchShapePath;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchStyle;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchSymbolInstance;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchSymbolMaster;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.ui.SketchFile;
import com.google.common.collect.ImmutableList;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Class that holds static methods for the conversion between the {@code SketchModel} and the {@code ShapeModel}
 */
public final class SketchToStudioConverter {
  private static final String DEFAULT_DOCUMENT_COLOR_NAME = "document_color";

  @NotNull
  public static StudioResourcesModel getResources(@NotNull SketchPage sketchPage, @NotNull SketchLibrary library) {
    ImmutableList.Builder<DrawableAssetModel> listBuilder = new ImmutableList.Builder<>();

    for (SketchArtboard artboard : SketchFile.getArtboards(sketchPage)) {
      listBuilder.add(createDrawableAsset(artboard, library));
    }

    for (SketchSymbolMaster symbolMaster : SketchFile.getAllSymbolMasters(sketchPage)) {
      listBuilder.add(createDrawableAsset(symbolMaster, library, AssetModel.Origin.SYMBOL));
    }

    // TODO get colors from solid fill shapes & drawables from slices
    return new StudioResourcesModel(listBuilder.build(), ImmutableList.of());
  }

  @NotNull
  public static StudioResourcesModel getResources(@NotNull SketchDocument sketchDocument, @NotNull SketchLibrary library) {
    ImmutableList.Builder<DrawableAssetModel> drawableListBuilder = new ImmutableList.Builder<>();
    ImmutableList.Builder<ColorAssetModel> colorListBuilder = new ImmutableList.Builder<>();

    colorListBuilder.addAll(getDocumentColors(sketchDocument));
    colorListBuilder.addAll(getExternalColors(sketchDocument));
    colorListBuilder.addAll(getSharedColors(sketchDocument));

    drawableListBuilder.addAll(getExternalDrawables(sketchDocument, library));
    drawableListBuilder.addAll(getSharedDrawables(sketchDocument, library));

    return new StudioResourcesModel(drawableListBuilder.build(), colorListBuilder.build());
  }

  /**
   * Generates the {@link DrawableAssetModel} object corresponding to all the shapes inside the artboard, by calling
   * the appropriate method according to the type of each layer in the artboard.
   * <ul>
   * <li>{@code createShapeModelsFromSymbol}</li>
   * <li>{@code createShapeModelFromShapeGroup}</li>
   * <li>{@code createShapeModelsFromLayerable}</li>
   * </ul>
   */
  @NotNull
  public static DrawableAssetModel createDrawableAsset(@NotNull SketchArtboard artboard, @NotNull SketchLibrary library) {
    ImmutableList.Builder<ShapeModel> shapes = new ImmutableList.Builder<>();
    SketchLayer[] layers = artboard.getLayers();

    for (SketchLayer layer : layers) {
      if (layer instanceof SketchSymbolInstance && library.hasSymbols()) {
        shapes.addAll(createShapeModelsFromSymbol((SketchSymbolInstance)layer, new InheritedProperties(), library));
      }
      else if (layer instanceof SketchShapeGroup) {
        shapes.addAll(createShapeModelFromShapeGroup((SketchShapeGroup)layer, new InheritedProperties(), false));
      }
      else if (layer instanceof SketchLayerable) {
        shapes.addAll(
          createShapeModelsFromLayerable((SketchLayerable)layer, new InheritedProperties(), library));
      }
    }

    ImmutableList<ShapeModel> shapeModels = shapes.build();
    String name = getDefaultName(artboard);
    // By default, an item is exportable if it has <b>at least one exportFormat</b> (regardless of
    // the specifics of the format -> users can mark an item as exportable in Sketch with one click).
    boolean exportable = artboard.getExportOptions().getExportFormats().length != 0;
    Rectangle.Double dimension = artboard.getFrame();

    return new DrawableAssetModel(shapeModels, exportable, name, dimension, dimension, AssetModel.Origin.ARTBOARD);
  }

  /**
   * SymbolMaster to Drawable conversion
   */
  @NotNull
  private static DrawableAssetModel createDrawableAsset(@NotNull SketchSymbolMaster symbolMaster,
                                                        @NotNull SketchLibrary library,
                                                        @NotNull AssetModel.Origin origin) {
    boolean exportable = symbolMaster.getExportOptions().getExportFormats().length != 0;
    String name = getDefaultName(symbolMaster);
    Rectangle.Double dimension = symbolMaster.getFrame();

    return new DrawableAssetModel(createShapeModelsFromLayerable(symbolMaster, new InheritedProperties(), library),
                                  exportable, name, dimension, dimension, origin);
  }

  // TODO Slice to Drawable conversion

  // TODO ShapeFill to Color conversion

  @NotNull
  private static String getDefaultName(@NotNull SketchLayer layer) {
    String name = layer.getName();

    if (layer.getExportOptions().getExportFormats().length != 0) {
      SketchExportFormat format = layer.getExportOptions().getExportFormats()[0];

      if (format.getNamingScheme() == SketchExportFormat.NAMING_SCHEME_PREFIX) {
        return format.getName() + name;
      }
      else if (format.getNamingScheme() == SketchExportFormat.NAMING_SCHEME_SUFFIX) {
        return name + format.getName();
      }
    }

    return name;
  }

  // region SketchPage items to Intermediate Models

  /**
   * Generates the list of {@link ShapeModel} objects corresponding to all the shapes that make up the symbol.
   * It gets the {@link SymbolModel} from the symbols library, updates the {@link InheritedProperties} with the
   * {@link SketchSymbolInstance} properties, applies them on the list of {@code ShapeModels} and returns it.
   */
  @NotNull
  private static ImmutableList<ShapeModel> createShapeModelsFromSymbol(@NotNull SketchSymbolInstance symbolInstance,
                                                                       @NotNull InheritedProperties inheritedProperties,
                                                                       @NotNull SketchLibrary library) {
    SymbolModel symbolModel = getSymbolModel(symbolInstance.getSymbolId(), library);
    if (symbolModel == null) {
      return ImmutableList.of();
    }

    symbolModel.setSymbolInstance(symbolInstance);
    inheritedProperties = inheritFromSymbol(symbolInstance, inheritedProperties);
    symbolModel.scaleShapes();
    symbolModel.applyProperties(inheritedProperties);

    return symbolModel.getShapeModels();
  }

  /**
   * Fetches the {@link SketchSymbolMaster} with the given {@code symbolId} and creates the list of
   * {@link ShapeModel}s inside.
   * Returns a new {@link SymbolModel} instance with the created list and the {@code SketchSymbolMaster}
   */
  @Nullable
  private static SymbolModel getSymbolModel(@NotNull String symbolId, @NotNull SketchLibrary library) {
    SketchSymbolMaster symbolMaster = library.getSymbol(symbolId);
    if (symbolMaster == null) {
      return null;
    }
    ImmutableList<ShapeModel> shapeModels = createShapeModelsFromLayerable(symbolMaster, new InheritedProperties(), library);
    return new SymbolModel(shapeModels, symbolMaster);
  }

  /**
   * Generates the {@link ShapeModel} of the shape(s) in the {@link SketchShapeGroup} object.
   * <p>
   * It takes the first shape in the {@code SketchShapeGroup}, updates the {@link InheritedProperties} and creates a
   * {@code ShapeModel} from them. If it's the only shape, it is returned. Otherwise, the method iterates through
   * the remaining {@link SketchShapePath}s and applies appropriate operations on the first {@code ShapeModel}.
   * It applies the transformations on the final {@code ShapeModel} and then returns it.
   */
  @NotNull
  private static ImmutableList<ShapeModel> createShapeModelFromShapeGroup(@NotNull SketchShapeGroup shapeGroup,
                                                                          @NotNull InheritedProperties inheritedProperties,
                                                                          boolean isLastShapeGroup) {
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

    SketchLayer[] layers = shapeGroup.getLayers();
    SketchShapePath baseSketchShapePath = (SketchShapePath)layers[0];

    Path2D.Double baseShapePath = getPath2D(baseSketchShapePath);
    StyleModel styleModel = createStyleModel(shapeGroup.getStyle());
    if (styleModel != null) {
      styleModel.makeGradientRelative(baseShapePath);
    }
    InheritedProperties newInheritedProperties = inheritFromShapeGroup(shapeGroup, inheritedProperties);

    PathModel finalShape = new PathModel(baseShapePath,
                                         styleModel,
                                         baseSketchShapePath.isFlippedHorizontal(),
                                         baseSketchShapePath.isFlippedVertical(),
                                         baseSketchShapePath.isClosed(),
                                         baseSketchShapePath.getRotation(),
                                         shapeGroup.getBooleanOperation(),
                                         baseSketchShapePath.getFramePosition(),
                                         shapeGroup.hasClippingMask(),
                                         shapeGroup.shouldBreakMaskChain(),
                                         isLastShapeGroup,
                                         inheritedProperties.getInheritedResizingConstraint());

    // If the shapegroup has just one layer, there will be no shape operation.
    // Therefore, no conversion to area needed.
    // Therefore, the path does not necessarily have to be closed.
    if (layers.length == 1) {
      finalShape.applyTransformations(newInheritedProperties);
      return ImmutableList.of(finalShape);
    }

    // If the shapegroup has multiple layers, there definitely are some shape operations to be performed.
    // Therefore, the path needs to be closed and converted into an Area before applying anything.
    AreaModel finalArea = finalShape.convertToArea();
    finalArea.applyTransformations(null);
    for (int i = 1; i < layers.length; i++) {
      SketchShapePath path = (SketchShapePath)layers[i];
      if (!path.isVisible()) {
        continue;
      }
      PathModel pathModel = createPathModel(path);
      AreaModel areaModel = pathModel.convertToArea();
      areaModel.applyTransformations(null);
      finalArea.applyOperation(areaModel);
    }

    // The shapeGroup itself and its components altogether can be rotated or flipped.
    finalArea.applyTransformations(newInheritedProperties);
    return ImmutableList.of(finalArea);
  }

  /**
   * Generates a {@link PathModel} object from the given {@link SketchShapePath}
   */
  @NotNull
  private static PathModel createPathModel(@NotNull SketchShapePath shapePath) {
    return new PathModel(getPath2D(shapePath), null, shapePath.isFlippedHorizontal(), shapePath.isFlippedVertical(),
                         shapePath.isClosed(), shapePath.getRotation(),
                         shapePath.getBooleanOperation(), shapePath.getFramePosition(), false, false, false, new ResizingConstraint());
  }

  /**
   * Generates the list of {@link ShapeModel}s in the {@link SketchLayerable} object.
   * <p>
   * It updates the {@link InheritedProperties} and iterates through the layer inside the {@link SketchLayerable}
   * object and calls the appropriate method according to the type of each layer in the layerable.
   * <ul>
   * <li>{@code createShapeModelsFromSymbol}</li>
   * <li>{@code createShapeModelFromShapeGroup}</li>
   * <li>{@code createShapeModelsFromLayerable}</li>
   * </ul>
   */
  @NotNull
  public static ImmutableList<ShapeModel> createShapeModelsFromLayerable(@NotNull SketchLayerable layerable,
                                                                         @NotNull InheritedProperties inheritedProperties,
                                                                         @NotNull SketchLibrary library) {

    ImmutableList.Builder<ShapeModel> builder = new ImmutableList.Builder<>();

    inheritedProperties = inheritFromLayerable(layerable, inheritedProperties);
    boolean isLastGroupElement = false;

    SketchLayer[] groupLayers = layerable.getLayers();
    for (int i = 0; i < groupLayers.length; i++) {
      if (i == groupLayers.length - 1) {
        isLastGroupElement = true;
      }
      SketchLayer layer = groupLayers[i];
      if (library.hasSymbols() && layer instanceof SketchSymbolInstance) {
        builder.addAll(createShapeModelsFromSymbol((SketchSymbolInstance)layer, inheritedProperties, library));
      }
      else if (layer instanceof SketchShapeGroup) {
        SketchShapeGroup shapeGroup = (SketchShapeGroup)layer;
        builder.addAll(createShapeModelFromShapeGroup(shapeGroup, inheritedProperties, isLastGroupElement));
      }
      else if (layer instanceof SketchLayerable) {
        builder.addAll(createShapeModelsFromLayerable((SketchLayerable)layer, inheritedProperties, library));
      }
    }

    return builder.build();
  }

  /**
   * Takes the {@link InheritedProperties} and updates them with the {@link SketchSymbolInstance}s own properties
   * and returns a new instance of {@code InheritedProperties}.
   */
  @NotNull
  private static InheritedProperties inheritFromSymbol(@NotNull SketchSymbolInstance symbolInstance,
                                                       @NotNull InheritedProperties inheritedProperties) {
    Rectangle2D.Double frame = symbolInstance.getFrame();
    Point2D.Double translation = new Point2D.Double(frame.getX(), frame.getY());

    SketchGraphicsContextSettings contextSettings = symbolInstance.getStyle().getContextSettings();
    double opacity = contextSettings != null ? contextSettings.getOpacity() : InheritedProperties.DEFAULT_OPACITY;

    ResizingConstraint constraint = symbolInstance.getResizingConstraint();

    return new InheritedProperties(inheritedProperties,
                                   translation,
                                   symbolInstance.isFlippedHorizontal(),
                                   symbolInstance.isFlippedVertical(),
                                   symbolInstance.getRotation(),
                                   opacity,
                                   constraint);
  }

  /**
   * Takes the {@link InheritedProperties} and updates them with the {@link SketchShapeGroup}s own properties
   * and returns a new instance of {@code InheritedProperties}.
   */
  @NotNull
  private static InheritedProperties inheritFromShapeGroup(@NotNull SketchShapeGroup shapeGroup,
                                                           @NotNull InheritedProperties inheritedProperties) {
    Rectangle2D.Double frame = shapeGroup.getFrame();
    Point2D.Double translation = new Point2D.Double(frame.getX(), frame.getY());

    ResizingConstraint constraint = shapeGroup.getResizingConstraint();

    return new InheritedProperties(inheritedProperties,
                                   translation,
                                   shapeGroup.isFlippedHorizontal(),
                                   shapeGroup.isFlippedVertical(),
                                   shapeGroup.getRotation(),
                                   InheritedProperties.DEFAULT_OPACITY,
                                   constraint);
  }

  /**
   * Takes the {@link InheritedProperties} and updates them with the {@link SketchLayerable}s own properties
   * and returns a new instance of {@code InheritedProperties}.
   */
  @NotNull
  private static InheritedProperties inheritFromLayerable(@NotNull SketchLayerable layerable,
                                                          @NotNull InheritedProperties inheritedProperties) {
    Rectangle2D.Double frame = layerable.getFrame();
    Point2D.Double translation = !(layerable instanceof SketchSymbolMaster)
                                 ? new Point2D.Double(frame.getX(), frame.getY())
                                 : new Point2D.Double();

    SketchGraphicsContextSettings graphicContextSettings = layerable.getStyle().getContextSettings();
    double opacity = graphicContextSettings != null ? graphicContextSettings.getOpacity() : InheritedProperties.DEFAULT_OPACITY;

    return new InheritedProperties(inheritedProperties,
                                   translation,
                                   layerable.isFlippedHorizontal(),
                                   layerable.isFlippedVertical(),
                                   layerable.getRotation(),
                                   opacity,
                                   layerable.getResizingConstraint());
  }

  /**
   * Generates a {@link StyleModel} from the given {@link SketchStyle} object
   */
  @Nullable
  private static StyleModel createStyleModel(@Nullable SketchStyle sketchStyle) {
    if (sketchStyle == null) {
      return null;
    }
    else {
      SketchGraphicsContextSettings styleGraphicsContextSettings = sketchStyle.getContextSettings();
      double styleOpacity = styleGraphicsContextSettings != null ? styleGraphicsContextSettings.getOpacity() : InheritedProperties.DEFAULT_OPACITY;

      SketchBorder[] sketchBorders = sketchStyle.getBorders();
      SketchBorder sketchBorder = sketchBorders != null && sketchBorders.length != 0 ? sketchBorders[0] : null;
      BorderModel borderModel = sketchBorder != null && sketchBorder.isEnabled()
                                ? new BorderModel(sketchBorder.getThickness(), sketchBorder.getColor())
                                : null;

      SketchFill[] sketchFills = sketchStyle.getFills();
      SketchFill sketchFill = sketchFills != null && sketchFills.length != 0 ? sketchFills[0] : null;
      FillModel fillModel = null;
      if (sketchFill != null && sketchFill.isEnabled()) {
        SketchGradient sketchGradient = sketchFill.getGradient();
        GradientModel gradientModel = createGradientModel(sketchGradient);

        SketchGraphicsContextSettings fillGraphicsContextSettings = sketchFill.getContextSettings();
        double fillOpacity = fillGraphicsContextSettings != null ? fillGraphicsContextSettings.getOpacity() : InheritedProperties.DEFAULT_OPACITY;

        fillModel = new FillModel(sketchFill.getColor(), gradientModel, fillOpacity);
      }

      return new StyleModel(fillModel, borderModel, styleOpacity);
    }
  }

  /**
   * Generates a {@link GradientModel} from the given {@link SketchGradient} object
   */
  @Nullable
  private static GradientModel createGradientModel(@Nullable SketchGradient sketchGradient) {
    if (sketchGradient == null) {
      return null;
    }
    else {
      SketchGradientStop[] gradientStops = sketchGradient.getStops();
      GradientStopModel[] gradientStopModels = new GradientStopModel[gradientStops.length];
      for (int i = 0; i < gradientStops.length; i++) {
        SketchGradientStop gradientStop = gradientStops[i];
        gradientStopModels[i] = new GradientStopModel(gradientStop.getPosition(), gradientStop.getColor());
      }

      return new GradientModel(sketchGradient.getGradientType(), sketchGradient.getFrom(), sketchGradient.getTo(), gradientStopModels);
    }
  }

  /**
   * Generates the {@link Path2D.Double} from the {@link SketchShapePath} object by calling the
   * appropriate method according to the type of each layer in the layerable.
   * <ul>
   * <li>{@code getRoundRectanglePath}</li>
   * <li>{@code getGenericPath}</li>
   * </ul>
   */
  @NotNull
  private static Path2D.Double getPath2D(@NotNull SketchShapePath shapePath) {
    if (RECTANGLE_CLASS_TYPE.equals(shapePath.getClassType())) {
      if (hasRoundCorners(shapePath)) {
        return getRoundRectanglePath(shapePath);
      }
      else {
        return getGenericPath(shapePath);
      }
    }
    else {
      return getGenericPath(shapePath);
    }
  }

  /**
   * Generates the {@link Path2D.Double} from a generic {@link SketchShapePath} object using the
   * {@link Path2DBuilder} class and calling its appropriate methods while iterating through
   * the {@code SkethShapePath}'s {@link SketchCurvePoint}s
   */
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

  /**
   * Generates the {@link Path2D.Double} from a {@link SketchShapePath} object that represents
   * a round rectangle in Sketch.
   * It uses the {@link Path2DBuilder} class and calls its appropriate methods while iterating through
   * the {@code SkethShapePath}'s {@link SketchCurvePoint}s. For every {@code SketchCurvePoint} that
   * has a corner radius different than 0, the method uses the builder to create a quad curve
   * instead of a regular rectangle corner.
   */
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

  /**
   * Checks if the given {@link SketchShapePath} has {@link SketchCurvePoint}s with
   * corner radius. Used for generating paths for round rectangles.
   */
  private static boolean hasRoundCorners(@NotNull SketchShapePath shapePath) {
    SketchCurvePoint[] points = shapePath.getPoints();

    for (SketchCurvePoint point : points) {
      if (point.getCornerRadius() != 0) {
        return true;
      }
    }

    return false;
  }
  // endregion

  // region SketchDocument items to Intermediate Models

  @NotNull
  private static ImmutableList<ColorAssetModel> getDocumentColors(@NotNull SketchDocument sketchDocument) {
    Color[] documentColors = sketchDocument.getAssets().getColors();
    int len = documentColors.length;
    if (len == 1) {
      return ImmutableList.of(new ColorAssetModel(true, DEFAULT_DOCUMENT_COLOR_NAME, documentColors[0], AssetModel.Origin.DOCUMENT));
    }
    else {
      ImmutableList.Builder<ColorAssetModel> colorListBuilder = new ImmutableList.Builder<>();
      for (int i = 0; i < len; i++) {
        colorListBuilder
          .add(new ColorAssetModel(true, DEFAULT_DOCUMENT_COLOR_NAME + '_' + (i + 1), documentColors[i], AssetModel.Origin.DOCUMENT));
      }
      return colorListBuilder.build();
    }
  }

  @NotNull
  private static ImmutableList<ColorAssetModel> getExternalColors(@NotNull SketchDocument sketchDocument) {
    ImmutableList.Builder<ColorAssetModel> colorListBuilder = new ImmutableList.Builder<>();
    // TODO
    return colorListBuilder.build();
  }

  @NotNull
  private static ImmutableList<ColorAssetModel> getSharedColors(@NotNull SketchDocument sketchDocument) {
    ImmutableList.Builder<ColorAssetModel> colorListBuilder = new ImmutableList.Builder<>();
    SketchSharedStyle[] sharedStyles = sketchDocument.getLayerStyles();
    for (SketchSharedStyle style : sharedStyles) {
      SketchFill[] fills = style.getValue().getFills();
      Color color = fills != null && fills.length != 0 ? fills[0].getColor() : null;
      if (color != null) {
        colorListBuilder.add(new ColorAssetModel(true, style.getName(), color, AssetModel.Origin.SHARED)); // TODO sanitize name
      }
    }
    return colorListBuilder.build();
  }

  @NotNull
  private static ImmutableList<DrawableAssetModel> getExternalDrawables(@NotNull SketchDocument sketchDocument,
                                                                        @NotNull SketchLibrary library) {
    if (sketchDocument.getForeignSymbols() == null) {
      return ImmutableList.of();
    }

    ImmutableList.Builder<DrawableAssetModel> drawableListBuilder = new ImmutableList.Builder<>();
    for (SketchForeignSymbol foreignSymbol : sketchDocument.getForeignSymbols()) {
      DrawableAssetModel drawableAsset = createDrawableAsset(foreignSymbol.getSymbolMaster(), library, AssetModel.Origin.EXTERNAL);
      drawableListBuilder.add(drawableAsset);
    }

    return drawableListBuilder.build();
  }

  @NotNull
  private static ImmutableList<DrawableAssetModel> getSharedDrawables(@NotNull SketchDocument sketchDocument,
                                                                      @NotNull SketchLibrary library) {
    ImmutableList.Builder<DrawableAssetModel> drawableListBuilder = new ImmutableList.Builder<>();
    // TODO
    return drawableListBuilder.build();
  }

  // endregion
}