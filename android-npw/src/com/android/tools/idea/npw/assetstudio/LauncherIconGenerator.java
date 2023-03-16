/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.npw.assetstudio;

import static com.android.tools.idea.npw.assetstudio.AssetStudioUtils.scaleRectangle;

import com.android.ide.common.util.AssetUtil;
import com.android.ide.common.util.PathString;
import com.android.resources.Density;
import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import com.android.tools.idea.npw.assetstudio.assets.ImageAsset;
import com.android.tools.idea.npw.assetstudio.assets.TextAsset;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObjectProperty;
import com.android.tools.idea.observable.core.ObjectValueProperty;
import com.android.tools.idea.rendering.DrawableRenderer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Generator of Android launcher icons.
 *
 * Defaults from https://romannurik.github.io/AndroidAssetStudio/icons-launcher.html
 */
@SuppressWarnings("UseJBColor") // Android icons don't need JBColor.
public class LauncherIconGenerator extends AdaptiveIconGenerator {
  private static final Rectangle IMAGE_SIZE_FULL_BLEED_DP = new Rectangle(0, 0, 108, 108);
  public static final Dimension SIZE_FULL_BLEED_DP = IMAGE_SIZE_FULL_BLEED_DP.getSize();
  private static final Rectangle IMAGE_SIZE_SAFE_ZONE_DP = new Rectangle(0, 0, 66, 66);
  private static final Rectangle IMAGE_SIZE_VIEWPORT_DP = new Rectangle(0, 0, 72, 72);
  private static final Rectangle IMAGE_SIZE_LEGACY_DP = new Rectangle(0, 0, 48, 48);
  private static final Rectangle IMAGE_SIZE_VIEWPORT_PLAY_STORE_PX = new Rectangle(0, 0, 512, 512);
  private static final Rectangle IMAGE_SIZE_FULL_BLEED_PLAY_STORE_PX = new Rectangle(0, 0, 768, 768);
  private static final Rectangle IMAGE_SIZE_VIEWPORT_PREVIEW_PLAY_STORE_PX = new Rectangle(0, 0, 528, 529);
  private static final Rectangle IMAGE_SIZE_TARGET_PLAY_STORE_PX = new Rectangle(8, 4, 528, 529);

  private final BoolProperty myGenerateRoundIcon = new BoolValueProperty(true);
  private final BoolProperty myGeneratePlayStoreIcon = new BoolValueProperty(true);
  private final BoolProperty myGenerateWebpIcons = new BoolValueProperty(true);
  private final ObjectProperty<Shape> myLegacyIconShape = new ObjectValueProperty<>(Shape.SQUARE);
  private final BoolProperty myShowGrid = new BoolValueProperty();
  private final ObjectValueProperty<Density> myPreviewDensity = new ObjectValueProperty<>(Density.XHIGH);

  /**
   * Initializes the icon generator. Every icon generator has to be disposed by calling {@link #dispose()}.
   *
   * @param project the Android project
   * @param minSdkVersion the minimal supported Android SDK version
   */
  public LauncherIconGenerator(@NotNull Project project, int minSdkVersion, @Nullable DrawableRenderer renderer) {
    super(project, minSdkVersion, new GraphicGeneratorContext(40, renderer));
  }

  /**
   * If {@code true}, generate the "Round" icon (API 25).
   */
  @NotNull
  public BoolProperty generateRoundIcon() {
    return myGenerateRoundIcon;
  }

  /**
   * If {@code true}, generate the Play Store icon.
   */
  @NotNull
  public BoolProperty generatePlayStoreIcon() {
    return myGeneratePlayStoreIcon;
  }

  /**
   * If {@code true}, generate icons in the WebP format.
   */
  @NotNull
  public BoolProperty generateWebpIcons() {
    return myGenerateWebpIcons;
  }

  /**
   * A shape which will be used as the "Legacy" icon's backdrop.
   */
  @NotNull
  public ObjectProperty<Shape> legacyIconShape() {
    return myLegacyIconShape;
  }

  @NotNull
  public BoolProperty showGrid() {
    return myShowGrid;
  }

  @NotNull
  public ObjectValueProperty<Density> previewDensity() {
    return myPreviewDensity;
  }

  @Override
  @NotNull
  public LauncherIconOptions createOptions(boolean forPreview) {
    LauncherIconOptions options = new LauncherIconOptions(forPreview);
    // Set foreground image.
    BaseAsset foregroundAsset = sourceAsset().getValueOrNull();
    if (foregroundAsset != null) {
      options.useForegroundColor = foregroundAsset.isColorable();
      Color color = foregroundAsset.isColorable() ? foregroundAsset.color().getValueOrNull() : null;
      if (color != null) {
        options.foregroundColor = color.getRGB();
      }
      double scaleFactor = foregroundAsset.scalingPercent().get() / 100.;
      if (foregroundAsset instanceof ImageAsset && ((ImageAsset)foregroundAsset).isClipart()) {
        scaleFactor *= 0.58;  // Scale correction for clip art to more or less fit into the safe zone.
      }
      else if (foregroundAsset instanceof TextAsset) {
        scaleFactor *= 0.46;  // Scale correction for text to more or less fit into the safe zone.
      }
      else if (foregroundAsset.trimmed().get()) {
        // Scale correction for images to fit into the safe zone.
        // Finding the smallest circle containing the image is not trivial (see https://en.wikipedia.org/wiki/Smallest-circle_problem).
        // For simplicity we treat the safe zone as a square.
        scaleFactor *= IMAGE_SIZE_SAFE_ZONE_DP.getWidth() / SIZE_FULL_BLEED_DP.getWidth();
      }
      options.foregroundImage =
        new TransformedImageAsset(foregroundAsset, SIZE_FULL_BLEED_DP, scaleFactor,
                                  color, getGraphicGeneratorContext(), myLineSeparator);
    }
    // Set background image.
    ImageAsset backgroundAsset = backgroundImageAsset().getValueOrNull();
    if (backgroundAsset != null) {
      double scaleFactor = backgroundAsset.scalingPercent().get() / 100.;
      options.backgroundImage =
        new TransformedImageAsset(backgroundAsset, SIZE_FULL_BLEED_DP, scaleFactor,
                                  null, getGraphicGeneratorContext(), myLineSeparator);
    }

    options.backgroundColor = backgroundColor().get().getRGB();
    options.showGrid = myShowGrid.get();
    options.showSafeZone = showSafeZone().get();
    options.previewDensity = myPreviewDensity.get();
    options.foregroundLayerName = foregroundLayerName().get();
    options.backgroundLayerName = backgroundLayerName().get();
    options.generateLegacyIcon = generateLegacyIcon().get();
    options.legacyIconShape = myLegacyIconShape.get();
    options.generateRoundIcon = myGenerateRoundIcon.get();
    options.generatePlayStoreIcon = myGeneratePlayStoreIcon.get();
    options.generateWebpIcons = myGenerateWebpIcons.get();
    return options;
  }

  @Override
  @NotNull
  protected List<Callable<GeneratedIcon>> createIconGenerationTasks(
      @NotNull GraphicGeneratorContext context, @NotNull IconOptions options, @NotNull String name) {
    LauncherIconOptions launcherIconOptions = (LauncherIconOptions)options;

    List<Callable<GeneratedIcon>> tasks = new ArrayList<>();

    // Generate tasks for icons (background, foreground, legacy) in all densities.
    createOutputIconsTasks(context, name, launcherIconOptions, tasks);

    // Generate tasks for drawable xml resource.
    createXmlDrawableResourcesTasks(name, launcherIconOptions, tasks);

    // Generate tasks for preview images.
    createPreviewImagesTasks(context, launcherIconOptions, tasks);
    return tasks;
  }

  private void createOutputIconsTasks(@NotNull GraphicGeneratorContext context, @NotNull String name, @NotNull LauncherIconOptions options,
                                      @NotNull List<Callable<GeneratedIcon>> tasks) {
    if (!options.generateOutputIcons) {
      return;
    }

    for (Density density : DENSITIES) {
      LauncherIconOptions localOptions = options.clone();
      localOptions.density = density;
      localOptions.showGrid = false;
      localOptions.showSafeZone = false;

      createOutputIconsForSingleDensityTasks(context, name, localOptions, density, tasks);
    }

    if (options.generatePlayStoreIcon) {
      tasks.add(() -> {
        LauncherIconOptions localOptions = options.clone();
        localOptions.showGrid = false;
        localOptions.showSafeZone = false;
        localOptions.generatePlayStoreIcon = true;
        localOptions.generateOutputIcons = true;
        localOptions.generatePreviewIcons = false;
        localOptions.legacyIconShape = Shape.NONE;
        AnnotatedImage image = generateLegacyImage(context, localOptions);
        return new GeneratedImageIcon(name,
                                      new PathString(getIconPath(localOptions, name)),
                                      IconCategory.PLAY_STORE,
                                      Density.NODPI,
                                      image);
      });
    }
  }

  private void createOutputIconsForSingleDensityTasks(@NotNull GraphicGeneratorContext context, @NotNull String name,
                                                      @NotNull LauncherIconOptions options, @NotNull Density density,
                                                      @NotNull List<Callable<GeneratedIcon>> tasks) {
    // Generate foreground mipmap only if the foreground is a raster image.
    if (options.foregroundImage != null && options.foregroundImage.isRasterImage()) {
      tasks.add(() -> {
        LauncherIconOptions foregroundOptions = options.clone();
        foregroundOptions.generatePlayStoreIcon = false;
        foregroundOptions.generatePreviewIcons = false;
        foregroundOptions.generateOutputIcons = true;
        AnnotatedImage foregroundImage = generateIconForegroundLayer(context, foregroundOptions);
        return new GeneratedImageIcon(foregroundOptions.foregroundLayerName,
                                      new PathString(getIconPath(foregroundOptions, options.foregroundLayerName)),
                                      IconCategory.ADAPTIVE_FOREGROUND_LAYER,
                                      density,
                                      foregroundImage);
      });
    }

    // Generate background mipmap only if the background is a raster image.
    if (options.backgroundImage != null && options.backgroundImage.isRasterImage()) {
      tasks.add(() -> {
        LauncherIconOptions backgroundOptions = options.clone();
        backgroundOptions.generatePlayStoreIcon = false;
        backgroundOptions.generatePreviewIcons = false;
        backgroundOptions.generateOutputIcons = true;
        AnnotatedImage backgroundImage = generateIconBackgroundLayer(context, backgroundOptions);
        return new GeneratedImageIcon(backgroundOptions.backgroundLayerName,
                                      new PathString(getIconPath(backgroundOptions, options.backgroundLayerName)),
                                      IconCategory.ADAPTIVE_BACKGROUND_LAYER,
                                      density,
                                      backgroundImage);
      });
    }

    if (options.generateLegacyIcon) {
      tasks.add(() -> {
        LauncherIconOptions legacyOptions = options.clone();
        legacyOptions.previewShape = PreviewShape.LEGACY;
        legacyOptions.generatePlayStoreIcon = false;
        legacyOptions.generatePreviewIcons = false;
        legacyOptions.generateOutputIcons = true;
        AnnotatedImage legacy = generateLegacyImage(context, legacyOptions);
        return new GeneratedImageIcon(name,
                                      new PathString(getIconPath(legacyOptions, name)),
                                      IconCategory.LEGACY,
                                      density,
                                      legacy);
      });
    }

    if (options.generateRoundIcon) {
      tasks.add(() -> {
        LauncherIconOptions legacyOptions = options.clone();
        legacyOptions.previewShape = PreviewShape.LEGACY_ROUND;
        legacyOptions.generatePlayStoreIcon = false;
        legacyOptions.generatePreviewIcons = false;
        legacyOptions.generateOutputIcons = true;
        legacyOptions.legacyIconShape = Shape.CIRCLE;
        AnnotatedImage legacyRound = generateLegacyImage(context, legacyOptions);
        return new GeneratedImageIcon(name + "_round",
                                      new PathString(getIconPath(legacyOptions, name + "_round")),
                                      IconCategory.ROUND_API_25,
                                      density,
                                      legacyRound);
      });
    }
  }

  private void createXmlDrawableResourcesTasks(@NotNull String name, @NotNull LauncherIconOptions options,
                                               @NotNull List<Callable<GeneratedIcon>> tasks) {
    if (!options.generateOutputIcons) {
      return;
    }
    {
      LauncherIconOptions iconOptions = options.clone();
      iconOptions.density = Density.ANYDPI;
      iconOptions.generatePlayStoreIcon = false;
      iconOptions.iconFolderKind = IconFolderKind.MIPMAP;
      iconOptions.apiVersion = 26; // Adaptive icons were introduced in API 26.

      tasks.add(() -> {
        String xmlAdaptiveIcon = getAdaptiveIconXml(iconOptions);
        return new GeneratedXmlResource(name,
                                        new PathString(getIconPath(iconOptions, name)),
                                        IconCategory.XML_RESOURCE,
                                        xmlAdaptiveIcon);
      });

      if (iconOptions.generateRoundIcon) {
        tasks.add(() -> {
          String xmlAdaptiveIcon = getAdaptiveIconXml(iconOptions);
          return new GeneratedXmlResource(name + "_round",
                                          new PathString(getIconPath(iconOptions, name + "_round")),
                                          IconCategory.XML_RESOURCE,
                                          xmlAdaptiveIcon);
        });
      }
    }

    if (options.foregroundImage != null && options.foregroundImage.isDrawable()) {
      // Generate foreground drawable.
      TransformedImageAsset image = options.foregroundImage;
      tasks.add(() -> {
        LauncherIconOptions iconOptions = options.clone();
        iconOptions.generatePlayStoreIcon = false;
        iconOptions.density = Density.ANYDPI;
        iconOptions.iconFolderKind = IconFolderKind.DRAWABLE_NO_DPI;

        if (!image.isDrawable()) {
          getLog().error("Background image is not drawable!", new Throwable());
        }
        String xmlDrawableText = image.getTransformedDrawable();
        if (xmlDrawableText == null) {
          getLog().error("Transformed foreground drawable is null" + (image.isDrawable() ? " but the image is drawable" : ""),
                         new Throwable());
          xmlDrawableText = "<vector/>"; // Use an empty image. It will be recomputed again soon.
        }
        iconOptions.apiVersion = calculateMinRequiredApiLevel(xmlDrawableText, myMinSdkVersion);
        return new GeneratedXmlResource(name,
                                        new PathString(getIconPath(iconOptions, iconOptions.foregroundLayerName)),
                                        IconCategory.ADAPTIVE_FOREGROUND_LAYER,
                                        xmlDrawableText);
      });
    }

    if (options.backgroundImage != null && options.backgroundImage.isDrawable()) {
      // Generate background drawable.
      TransformedImageAsset image = options.backgroundImage;
      tasks.add(() -> {
        LauncherIconOptions iconOptions = options.clone();
        iconOptions.generatePlayStoreIcon = false;
        iconOptions.density = Density.ANYDPI;
        iconOptions.iconFolderKind = IconFolderKind.DRAWABLE_NO_DPI;

        if (!image.isDrawable()) {
          getLog().error("Background image is not drawable!", new Throwable());
        }
        String xmlDrawableText = image.getTransformedDrawable();
        if (xmlDrawableText == null) {
          getLog().error("Transformed background drawable is null" + (image.isDrawable() ? " but the image is drawable" : ""),
                         new Throwable());
          xmlDrawableText = "<vector/>"; // Use an empty image. It will be recomputed again soon.
        }
        iconOptions.apiVersion = calculateMinRequiredApiLevel(xmlDrawableText, myMinSdkVersion);
        return new GeneratedXmlResource(name,
                                        new PathString(getIconPath(iconOptions, iconOptions.backgroundLayerName)),
                                        IconCategory.ADAPTIVE_BACKGROUND_LAYER,
                                        xmlDrawableText);
      });
    } else if (options.backgroundImage == null) {
      // Generate background color value.
      tasks.add(() -> {
        LauncherIconOptions iconOptions = options.clone();
        iconOptions.generatePlayStoreIcon = false;
        iconOptions.density = Density.ANYDPI;
        iconOptions.iconFolderKind = IconFolderKind.VALUES;

        String format = ""
            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>%1$s"
            + "<resources>%1$s"
            + "    <color name=\"%2$s\">#%3$06X</color>%1$s"
            + "</resources>";
        String xmlColor = String.format(format, myLineSeparator, iconOptions.backgroundLayerName, iconOptions.backgroundColor & 0xFFFFFF);
        return new GeneratedXmlResource(name,
                                        new PathString(getIconPath(iconOptions, iconOptions.backgroundLayerName)),
                                        IconCategory.XML_RESOURCE,
                                        xmlColor);
      });
    }
  }

  private void createPreviewImagesTasks(@NotNull GraphicGeneratorContext context, @NotNull LauncherIconOptions options,
                                        @NotNull List<Callable<GeneratedIcon>> tasks) {
    if (!options.generatePreviewIcons) {
      return;
    }

    List<PreviewShape> previewShapes = new ArrayList<>();
    previewShapes.add(PreviewShape.FULL_BLEED);
    previewShapes.add(PreviewShape.SQUIRCLE);
    previewShapes.add(PreviewShape.CIRCLE);
    previewShapes.add(PreviewShape.SQUARE);
    previewShapes.add(PreviewShape.ROUNDED_SQUARE);
    if (options.generateLegacyIcon) {
      previewShapes.add(PreviewShape.LEGACY);
    }
    if (options.generateRoundIcon) {
      previewShapes.add(PreviewShape.LEGACY_ROUND);
    }
    if (options.generatePlayStoreIcon) {
      previewShapes.add(PreviewShape.PLAY_STORE);
    }

    for (PreviewShape previewShape : previewShapes) {
      tasks.add(() -> {
        LauncherIconOptions localOptions = options.clone();
        localOptions.density = options.previewDensity;
        localOptions.previewShape = previewShape;
        localOptions.generateLegacyIcon = (previewShape == PreviewShape.LEGACY);
        localOptions.generateRoundIcon = (previewShape == PreviewShape.LEGACY_ROUND);
        localOptions.generatePlayStoreIcon = (previewShape == PreviewShape.PLAY_STORE);

        AnnotatedImage image;
        try {
          image = generatePreviewImage(context, localOptions);
        }
        catch (Throwable e) {
          getLog().error(e); // Unexpected error, log it.
          image = createPlaceholderErrorImage(e, localOptions);
        }

        return new GeneratedImageIcon(previewShape.id,
                                      null, // No path for preview icons.
                                      IconCategory.PREVIEW,
                                      localOptions.density,
                                      image);
      });
    }
  }

  @Override
  public void generateRasterImage(@Nullable String category, @NotNull Map<String, Map<String, AnnotatedImage>> categoryMap,
                                  @NotNull GraphicGeneratorContext context, @NotNull IconOptions options, @NotNull String name) {
    LauncherIconOptions launcherIconOptions = (LauncherIconOptions) options;
    LauncherIconOptions localOptions = launcherIconOptions.clone();
    localOptions.generatePlayStoreIcon = false;

    Collection<GeneratedIcon> icons = generateIcons(context, options, name);
    icons.stream()
        .filter(icon -> icon instanceof GeneratedImageIcon)
        .map(icon -> (GeneratedImageIcon) icon)
        .filter(icon -> icon.getOutputPath() != null)
        .forEach(
            icon -> {
              assert icon.getOutputPath() != null;

              Map<String, AnnotatedImage> imageMap = categoryMap.computeIfAbsent(icon.getCategory().toString(), k -> new LinkedHashMap<>());

              // Store image in a map, where the key is the relative path to the image.
              LauncherIconOptions iconOptions = localOptions.clone();
              iconOptions.density = icon.getDensity();
              iconOptions.iconFolderKind = IconFolderKind.MIPMAP;
              iconOptions.generatePlayStoreIcon = (icon.getCategory() == IconCategory.PLAY_STORE);
              imageMap.put(icon.getOutputPath().toString(), new AnnotatedImage(icon.getImage(), icon.getErrorMessage()));
            });
  }

  @Override
  @NotNull
  public AnnotatedImage generateRasterImage(@NotNull GraphicGeneratorContext context, @NotNull IconOptions options) {
    if (options.usePlaceholders) {
      return PLACEHOLDER_IMAGE;
    }

    return generatePreviewImage(context, (LauncherIconOptions)options);
  }

  @NotNull
  private AnnotatedImage generatePreviewImage(@NotNull GraphicGeneratorContext context, @NotNull LauncherIconOptions options) {
    switch (options.previewShape) {
      case CIRCLE:
      case SQUIRCLE:
      case ROUNDED_SQUARE:
      case SQUARE:
        return generateViewportPreviewImage(context, options);

      case LEGACY:
        options.generatePreviewIcons = true;
        options.generatePlayStoreIcon = false;
        return generateLegacyImage(context, options);

      case LEGACY_ROUND:
        options.generatePreviewIcons = true;
        options.generatePlayStoreIcon = false;
        options.legacyIconShape = Shape.CIRCLE;
        return generateLegacyImage(context, options);

      case FULL_BLEED: {
        AnnotatedImage annotatedImage = generateFullBleedPreviewImage(context, options);
        // For preview, scale image down so that it does not display relatively
        // too big compared to the other preview icons.
        return new AnnotatedImage(scaledPreviewImage(annotatedImage.getImage(), 0.8f), annotatedImage.getErrorMessage());
      }

      case PLAY_STORE: {
        options.generatePreviewIcons = true;
        options.generatePlayStoreIcon = true;
        options.legacyIconShape = Shape.SQUARE;
        AnnotatedImage annotatedImage = generateLegacyImage(context, options);
        BufferedImage image = AssetUtil.trimmedImage(annotatedImage.getImage());
        // For preview, scale image down so that it does not display relatively
        // too big compared to the other preview icons.
        double scale = getMdpiScaleFactor(options.previewDensity);
        return new AnnotatedImage(scaledPreviewImage(image, 0.22 * scale), annotatedImage.getErrorMessage());
      }

      case NONE:
      default:
        throw new IllegalArgumentException();
    }
  }

  @NotNull
  private AnnotatedImage generateFullBleedPreviewImage(@NotNull GraphicGeneratorContext context, @NotNull LauncherIconOptions options) {
    AnnotatedImage mergedImage = generateMergedLayers(context, options, Color.BLACK);
    drawGrid(options, mergedImage.getImage());
    return mergedImage;
  }

  /**
   * Generates an raster image for either a "Legacy", "Round" or Play Store icon. The created
   * image consists of both background and foreground layer images merge together, then a shape
   * (e.g. circle, square) mask is applied, and finally the image is scaled to the appropriate
   * size (48x48 legacy or 512x512px Play Store).
   */
  @NotNull
  private AnnotatedImage generateLegacyImage(@NotNull GraphicGeneratorContext context, @NotNull LauncherIconOptions options) {
    // The viewport rectangle (72x72dp) scaled according to density.
    Rectangle viewportRect = getViewportRectangle(options);

    // The "Legacy" icon rectangle (48x48dp) scaled according to density.
    Rectangle legacyRect = getLegacyRectangle(options);

    // The Play Store density does not exist in the "Density" enum. Various icon APIs use
    // Density.NODPI as a placeholder for Play Store.
    Density legacyOrPlayStoreDensity = options.generatePlayStoreIcon ? Density.NODPI : options.density;

    // The sub-rectangle of the 48x48dp "Legacy" icon that corresponds to the "Legacy" icon
    // shape, scaled according to the density.
    Rectangle legacyShapeRect = options.generatePlayStoreIcon ?
                                IMAGE_SIZE_TARGET_PLAY_STORE_PX :
                                LauncherLegacyIconGenerator.getTargetRect(options.legacyIconShape, legacyOrPlayStoreDensity);

    // Generate full bleed and viewport images.
    AnnotatedImage mergedImage = generateMergedLayers(context, options, null);
    BufferedImage fullBleed = mergedImage.getImage();

    // Scale the "Full Bleed" icon so that it is contained in the "Legacy" shape rectangle.
    //
    // Note that even though we scale the "Full Bleed" image, we use the ratio of the
    // Viewport rectangle (72x72dp) to Legacy shape (sub-rectangle of 48x48dp) as the
    // scaling factor, because the Viewport rectangle is the visible part of Adaptive icons,
    // whereas the "Full Bleed" icon is never entirely visible.
    double viewportScale = getRectangleInsideScale(viewportRect, legacyShapeRect);
    BufferedImage scaledFullBleed =
        options.generatePreviewIcons ? scaledPreviewImage(fullBleed, viewportScale) : scaledImage(fullBleed, viewportScale);

    // Load shadow and mask corresponding to legacy shape.
    BufferedImage shapeImageBack = null;
    BufferedImage shapeImageFore = null;
    BufferedImage shapeImageMask = null;
    if (options.legacyIconShape != Shape.NONE) {
      shapeImageBack = loadBackImage(context, options.legacyIconShape, legacyOrPlayStoreDensity);
      shapeImageFore = loadStyleImage(context, options.legacyIconShape, legacyOrPlayStoreDensity, Style.SIMPLE);
      shapeImageMask = loadMaskImage(context, options.legacyIconShape, legacyOrPlayStoreDensity);
    }

    // Generate legacy image by merging shadow, mask and (scaled) adaptive icon
    BufferedImage legacyImage = AssetUtil.newArgbBufferedImage(legacyRect.width, legacyRect.height);
    Graphics2D gLegacy = (Graphics2D)legacyImage.getGraphics();

    // Start with backdrop image (semi-transparent shadow).
    if (shapeImageBack != null) {
      AssetUtil.drawCentered(gLegacy, shapeImageBack, legacyRect);
    }

    // Apply the mask to the scaled adaptive icon.
    if (shapeImageMask != null) {
      scaledFullBleed = applyMask(scaledFullBleed, shapeImageMask);
    }

    // Draw the scaled adaptive icon on top of shadow effect.
    AssetUtil.drawCentered(gLegacy, scaledFullBleed, legacyRect);

    // Finish with the foreground effect (shadow outline).
    if (shapeImageFore != null) {
      gLegacy.drawImage(shapeImageFore, 0, 0, null);
    }
    gLegacy.dispose();
    return new AnnotatedImage(legacyImage, mergedImage.getErrorMessage());
  }

  /** Generates a preview image with a {@link PreviewShape} mask applied (e.g. Square, Squircle). */
  @NotNull
  private AnnotatedImage generateViewportPreviewImage(@NotNull GraphicGeneratorContext context, @NotNull LauncherIconOptions options) {
    AnnotatedImage mergedImage = generateMergedLayers(context, options, null);
    BufferedImage image = mergedImage.getImage();
    BufferedImage mask = generateMaskLayer(context, options, options.previewShape);
    image = cropImageToViewport(options, image);
    image = applyMask(image, mask);
    drawGrid(options, image);

    return new AnnotatedImage(image, mergedImage.getErrorMessage());
  }

  @Nullable
  private BufferedImage generateMaskLayer(@NotNull GraphicGeneratorContext context, @NotNull LauncherIconOptions options,
                                          @NotNull PreviewShape shape) {
    String maskName;
    switch (shape) {
      case CIRCLE:
        maskName = "circle";
        break;
      case SQUARE:
        maskName = "square";
        break;
      case ROUNDED_SQUARE:
        maskName = "rounded_corner";
        break;
      case SQUIRCLE:
        //noinspection SpellCheckingInspection
        maskName = "squircle";
        break;
      default:
        maskName = null;
    }
    if (maskName == null) {
      return null;
    }

    if (options.generatePlayStoreIcon) {
      String resourceName = String.format("/images/adaptive_icons_masks/adaptive_%s-%s.png", maskName, Density.XXXHIGH.getResourceValue());

      BufferedImage mask = context.loadImageResource(resourceName);
      if (mask == null) {
        return null;
      }
      Rectangle maskRect = new Rectangle(0, 0, mask.getWidth(), mask.getHeight());
      double scale = getRectangleInsideScale(maskRect, getViewportRectangle(options));
      return options.generatePreviewIcons ? scaledPreviewImage(mask, scale) : scaledImage(mask, scale);
    }

    String resourceName = String.format("/images/adaptive_icons_masks/adaptive_%s-%s.png", maskName, options.density.getResourceValue());
    return context.loadImageResource(resourceName);
  }

  @Override
  @NotNull
  protected Rectangle getFullBleedRectangle(@NotNull AdaptiveIconOptions options) {
    if (((LauncherIconOptions)options).generatePlayStoreIcon) {
      return IMAGE_SIZE_FULL_BLEED_PLAY_STORE_PX;
    }
    return scaleRectangle(IMAGE_SIZE_FULL_BLEED_DP, getMdpiScaleFactor(options.density));
  }

  @Override
  @NotNull
  protected Rectangle getViewportRectangle(@NotNull AdaptiveIconOptions options) {
    if (((LauncherIconOptions)options).generatePlayStoreIcon) {
      return IMAGE_SIZE_VIEWPORT_PLAY_STORE_PX;
    }
    return scaleRectangle(IMAGE_SIZE_VIEWPORT_DP, getMdpiScaleFactor(options.density));
  }

  @Override
  @NotNull
  protected Rectangle getLegacyRectangle(@NotNull AdaptiveIconOptions options) {
    if (((LauncherIconOptions)options).generatePlayStoreIcon) {
      return options.generatePreviewIcons ? IMAGE_SIZE_VIEWPORT_PREVIEW_PLAY_STORE_PX : IMAGE_SIZE_VIEWPORT_PLAY_STORE_PX;
    }
    return scaleRectangle(IMAGE_SIZE_LEGACY_DP, getMdpiScaleFactor(options.density));
  }

  @Override
  protected Rectangle getMaxIconRectangle() {
    return IMAGE_SIZE_FULL_BLEED_PLAY_STORE_PX;
  }

  private static void drawGrid(@NotNull LauncherIconOptions launcherIconOptions, @NotNull BufferedImage image) {
    Graphics2D gOut = (Graphics2D) image.getGraphics();
    drawGrid(launcherIconOptions, gOut);
    gOut.dispose();
  }

  private static void drawGrid(@NotNull LauncherIconOptions launcherIconOptions, @NotNull Graphics2D gOut) {
    if (launcherIconOptions.generatePlayStoreIcon) {
      return;
    }

    if (launcherIconOptions.previewShape == PreviewShape.FULL_BLEED) {
      if (launcherIconOptions.showGrid || launcherIconOptions.showSafeZone) {
        drawFullBleedIconGrid(launcherIconOptions, gOut);
      }
      return;
    }

    if (launcherIconOptions.previewShape == PreviewShape.LEGACY || launcherIconOptions.previewShape == PreviewShape.LEGACY_ROUND) {
      if (launcherIconOptions.showGrid) {
        drawLegacyIconGrid(launcherIconOptions, gOut);
      }
      return;
    }

    if (launcherIconOptions.showGrid || launcherIconOptions.showSafeZone) {
      drawAdaptiveIconGrid(launcherIconOptions, gOut);
    }
  }

  private static void drawAdaptiveIconGrid(@NotNull LauncherIconOptions options, @NotNull Graphics2D out) {
    double scaleFactor = getMdpiScaleFactor(options.density);

    // 72x72
    int size = IMAGE_SIZE_VIEWPORT_DP.width;
    int center = size / 2;

    Color c = new Color(0f, 0f, 0f, 0.20f);
    out.setColor(c);
    out.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    PrimitiveShapesHelper g = new PrimitiveShapesHelper(out, scaleFactor);
    if (options.showGrid) {
      g.drawRect(0, 0, size, size);

      // "+" and "x" cross
      g.drawLine(0, 0, size, size);
      g.drawLine(size, 0, 0, size);
      g.drawLine(0, center, size, center);
      g.drawLine(center, 0, center, size);

      // 3 keyline rectangles (36x52, 44x44, 52x36)
      int arcSize = 4;
      int rect1 = 36;
      int rect2 = 44;
      int rect3 = 52;
      g.drawRoundRect((size - rect1) / 2, (size - rect3) / 2, rect1, rect3, arcSize, arcSize);
      g.drawRoundRect((size - rect2) / 2, (size - rect2) / 2, rect2, rect2, arcSize, arcSize);
      g.drawRoundRect((size - rect3) / 2, (size - rect1) / 2, rect3, rect1, arcSize, arcSize);

      // 2 keyline circles: 36dp and 52dp
      g.drawCenteredCircle(center, center, 18);
      g.drawCenteredCircle(center, center, 26);
    }

    if (options.showSafeZone) {
      // Safe zone: 66dp
      g.drawCenteredCircle(center, center, 33);
    }
  }

  private static void drawFullBleedIconGrid(@NotNull LauncherIconOptions options, @NotNull Graphics2D out) {
    double scaleFactor = getMdpiScaleFactor(options.density);

    // 108x108
    int size = IMAGE_SIZE_FULL_BLEED_DP.width;
    int center = size / 2;

    //noinspection UseJBColor
    Color c = new Color(0f, 0f, 0f, 0.20f);
    out.setColor(c);
    out.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    PrimitiveShapesHelper g = new PrimitiveShapesHelper(out, scaleFactor);
    if (options.showGrid) {
      g.drawRect(0, 0, size, size);

      // Viewport
      g.drawRect(18, 18, IMAGE_SIZE_VIEWPORT_DP.width, IMAGE_SIZE_VIEWPORT_DP.height);

      // "+" and "x" cross
      g.drawLine(0, 0, size, size);
      g.drawLine(size, 0, 0, size);
      g.drawLine(0, center, size, center);
      g.drawLine(center, 0, center, size);
    }

    if (options.showSafeZone) {
      // Safe zone: 66dp
      g.drawCenteredCircle(center, center, IMAGE_SIZE_SAFE_ZONE_DP.width / 2);
    }
  }

  private static void drawLegacyIconGrid(@NotNull LauncherIconOptions options, @NotNull Graphics2D out) {
    double scaleFactor = getMdpiScaleFactor(options.density);

    // 48x48
    int size = IMAGE_SIZE_LEGACY_DP.width;
    int center = size / 2;

    //noinspection UseJBColor
    Color c = new Color(0f, 0f, 0f, 0.20f);
    out.setColor(c);
    out.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    PrimitiveShapesHelper g = new PrimitiveShapesHelper(out, scaleFactor);
    g.drawRect(0, 0, size, size);

    // "+" and "x" cross
    g.drawLine(0, 0, size, size);
    g.drawLine(size, 0, 0, size);
    g.drawLine(0, center, size, center);
    g.drawLine(center, 0, center, size);

    // 2 keyline rectangles (32x44, 38x38, 44x32)
    int arcSize = 3;
    int rect1 = 32;
    //int rect2 = 38;
    int rect3 = 44;
    g.drawRoundRect((size - rect1) / 2, (size - rect3) / 2, rect1, rect3, arcSize, arcSize);
    //g.drawRoundRect((size - rect2) / 2, (size - rect2) / 2, rect2, rect2, arcSize, arcSize);
    g.drawRoundRect((size - rect3) / 2, (size - rect1) / 2, rect3, rect1, arcSize, arcSize);

    // 2 keyline circles: 20dp and 44dp
    g.drawCenteredCircle(center, center, 10);
    g.drawCenteredCircle(center, center, 22);
  }

  @Override
  protected boolean includeDensity(@NotNull Density density) {
    // Launcher icons should include xxxhdpi as well.
    return super.includeDensity(density) || density == Density.XXXHIGH;
  }

  @Override
  @NotNull
  protected String getIconPath(@NotNull IconOptions options, @NotNull String iconName) {
    if (((LauncherIconOptions) options).generatePlayStoreIcon) {
      return iconName + "-playstore.png"; // Store at the root of the project.
    }

    return super.getIconPath(options, iconName);
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(LauncherIconGenerator.class);
  }

  /** Options specific to generating launcher icons. */
  public static class LauncherIconOptions extends AdaptiveIconOptions implements Cloneable {
    /** Whether to generate the "Round" icon (API 25). */
    public boolean generateRoundIcon = true;

    /**
     * Whether a Play Store graphic should be generated (will ignore normal density setting).
     * The {@link #generateRasterImage(GraphicGeneratorContext, IconOptions)} method uses this to decide
     * whether to generate a normal density icon or a high res Play Store image.
     * The {@link IconGenerator#generateRasterImage(String, Map, GraphicGeneratorContext, IconOptions, String)}
     * method uses this flag to determine whether it should include a Play Store graphic in its iteration.
     */
    public boolean generatePlayStoreIcon;

    /** If set, generate a preview image. */
    public PreviewShape previewShape = PreviewShape.NONE;

    /** The shape to use for the "Legacy" icon. */
    public Shape legacyIconShape = Shape.SQUARE;

    /** Whether to draw the keyline shapes. */
    public boolean showGrid;

    public LauncherIconOptions(boolean forPreview) {
      super(forPreview);
      iconFolderKind = IconFolderKind.MIPMAP;
    }

    @Override
    @NotNull
    public LauncherIconOptions clone() {
      return (LauncherIconOptions)super.clone();
    }
  }

  public enum PreviewShape {
    CIRCLE("circle", "Circle"),
    SQUIRCLE("squircle", "Squircle"),
    ROUNDED_SQUARE("rounded-square", "Rounded Square"),
    SQUARE("square", "Square"),
    FULL_BLEED("full-bleed-layers", "Full Bleed Layers"),
    LEGACY("legacy", "Legacy Icon"),
    LEGACY_ROUND("legacy-round", "Round Icon"),
    PLAY_STORE("play-store", "Google Play Store"),
    NONE("none", "none");

    /** Id, used when shape is converted to a string */
    public final String id;
    /** Display name, when shape is displayed to the end-user */
    public final String displayName;

    PreviewShape(@NotNull String id, @NotNull String displayName) {
      this.id = id;
      this.displayName = displayName;
    }
  }
}
