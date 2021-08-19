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
package com.android.tools.idea.npw.assetstudio;

import static com.android.tools.idea.npw.assetstudio.AssetStudioUtils.roundToInt;
import static com.android.tools.idea.npw.assetstudio.AssetStudioUtils.scaleRectangle;

import com.android.ide.common.util.PathString;
import com.android.resources.Density;
import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import com.android.tools.idea.npw.assetstudio.assets.ImageAsset;
import com.android.tools.idea.npw.assetstudio.assets.TextAsset;
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
 * Generator of Android TV channel icons.
 */
@SuppressWarnings("UseJBColor") // Android icons don't need JBColor.
public class TvChannelIconGenerator extends AdaptiveIconGenerator {
  private static final Rectangle IMAGE_SIZE_FULL_BLEED_DP = new Rectangle(0, 0, 120, 120);
  private static final Dimension SIZE_FULL_BLEED_DP = IMAGE_SIZE_FULL_BLEED_DP.getSize();
  private static final Rectangle IMAGE_SIZE_SAFE_ZONE_DP = new Rectangle(0, 0, 73, 73);
  private static final Rectangle IMAGE_SIZE_VIEWPORT_DP = new Rectangle(0, 0, 80, 80);
  private static final Rectangle IMAGE_SIZE_LEGACY_DP = new Rectangle(0, 0, 80, 80);
  private static final Density LEGACY_DENSITY = Density.XHIGH;
  private static final double PREVIEW_SCALE = 0.6;

  /**
   * Initializes the icon generator. Every icon generator has to be disposed by calling {@link #dispose()}.
   *
   * @param project the Android project
   * @param minSdkVersion the minimal supported Android SDK version
   */
  public TvChannelIconGenerator(@NotNull Project project, int minSdkVersion, @Nullable DrawableRenderer renderer) {
    super(project, minSdkVersion, new GraphicGeneratorContext(40, renderer));
  }

  @Override
  @NotNull
  public TvChannelIconOptions createOptions(boolean forPreview) {
    TvChannelIconOptions options = new TvChannelIconOptions(forPreview);
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
    options.showSafeZone = showSafeZone().get();
    options.previewDensity = LEGACY_DENSITY;
    options.foregroundLayerName = foregroundLayerName().get();
    options.backgroundLayerName = backgroundLayerName().get();
    options.generateLegacyIcon = generateLegacyIcon().get();
    return options;
  }

  @Override
  @NotNull
  protected List<Callable<GeneratedIcon>> createIconGenerationTasks(
      @NotNull GraphicGeneratorContext context, @NotNull IconOptions options, @NotNull String name) {
    TvChannelIconOptions launcherIconOptions = (TvChannelIconOptions)options;

    List<Callable<GeneratedIcon>> tasks = new ArrayList<>();

    // Generate tasks for icons (background, foreground, legacy) in all densities.
    createOutputIconsTasks(context, name, launcherIconOptions, tasks);

    // Generate tasks for drawable xml resource.
    createXmlDrawableResourcesTasks(name, launcherIconOptions, tasks);

    // Generate tasks for preview images.
    createPreviewImagesTasks(context, launcherIconOptions, tasks);
    return tasks;
  }

  private void createOutputIconsTasks(@NotNull GraphicGeneratorContext context, @NotNull String name, @NotNull TvChannelIconOptions options,
                                      @NotNull List<Callable<GeneratedIcon>> tasks) {
    if (!options.generateOutputIcons) {
      return;
    }

    TvChannelIconOptions localOptions = options.clone();
    localOptions.density = LEGACY_DENSITY;
    createOutputIconsForSingleDensityTasks(context, name, localOptions, localOptions.density, tasks);
  }

  private void createOutputIconsForSingleDensityTasks(@NotNull GraphicGeneratorContext context, @NotNull String name,
                                                      @NotNull TvChannelIconOptions options, @NotNull Density density,
                                                      @NotNull List<Callable<GeneratedIcon>> tasks) {
    // Generate foreground mipmap only if the foreground is a raster image.
    if (options.foregroundImage != null && options.foregroundImage.isRasterImage()) {
      tasks.add(() -> {
        TvChannelIconOptions foregroundOptions = options.clone();
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
        TvChannelIconOptions backgroundOptions = options.clone();
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
        TvChannelIconOptions legacyOptions = options.clone();
        legacyOptions.previewShape = PreviewShape.LEGACY;
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
  }

  private void createXmlDrawableResourcesTasks(@NotNull String name, @NotNull TvChannelIconOptions options,
                                               @NotNull List<Callable<GeneratedIcon>> tasks) {
    if (!options.generateOutputIcons) {
      return;
    }
    {
      TvChannelIconOptions iconOptions = options.clone();
      iconOptions.density = Density.ANYDPI;
      iconOptions.iconFolderKind = IconFolderKind.MIPMAP;
      iconOptions.apiVersion = 26; // Adaptive icons were introduced in API 26.

      tasks.add(() -> {
        String xmlAdaptiveIcon = getAdaptiveIconXml(iconOptions);
        return new GeneratedXmlResource(name,
                                        new PathString(getIconPath(iconOptions, name)),
                                        IconCategory.XML_RESOURCE,
                                        xmlAdaptiveIcon);
      });
    }

    if (options.foregroundImage != null && options.foregroundImage.isDrawable()) {
      // Generate foreground drawable.
      TransformedImageAsset image = options.foregroundImage;
      tasks.add(() -> {
        TvChannelIconOptions iconOptions = options.clone();
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
        TvChannelIconOptions iconOptions = options.clone();
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
        TvChannelIconOptions iconOptions = options.clone();
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

  private void createPreviewImagesTasks(@NotNull GraphicGeneratorContext context, @NotNull TvChannelIconOptions options,
                                        @NotNull List<Callable<GeneratedIcon>> tasks) {
    if (!options.generatePreviewIcons) {
      return;
    }

    List<PreviewShape> previewShapes = new ArrayList<>(2);
    previewShapes.add(PreviewShape.ADAPTIVE);
    if (options.generateLegacyIcon) {
      previewShapes.add(PreviewShape.LEGACY);
    }

    for (PreviewShape previewShape : previewShapes) {
      tasks.add(() -> {
        TvChannelIconOptions localOptions = options.clone();
        localOptions.density = options.previewDensity;
        localOptions.previewShape = previewShape;
        localOptions.generateLegacyIcon = (previewShape == PreviewShape.LEGACY);

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
    TvChannelIconOptions launcherIconOptions = (TvChannelIconOptions) options;
    TvChannelIconOptions localOptions = launcherIconOptions.clone();

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
              TvChannelIconOptions iconOptions = localOptions.clone();
              iconOptions.density = icon.getDensity();
              iconOptions.iconFolderKind = IconFolderKind.MIPMAP;
              imageMap.put(icon.getOutputPath().toString(), new AnnotatedImage(icon.getImage(), icon.getErrorMessage()));
            });
  }

  @Override
  @NotNull
  public AnnotatedImage generateRasterImage(@NotNull GraphicGeneratorContext context, @NotNull IconOptions options) {
    if (options.usePlaceholders) {
      return PLACEHOLDER_IMAGE;
    }

    return generatePreviewImage(context, (TvChannelIconOptions)options);
  }

  @NotNull
  private AnnotatedImage generatePreviewImage(@NotNull GraphicGeneratorContext context, @NotNull TvChannelIconOptions options) {
    switch (options.previewShape) {
      case ADAPTIVE:
        return generateViewportPreviewImage(context, options);

      case LEGACY:
        return generateLegacyImage(context, options);

      case NONE:
      default:
        throw new IllegalArgumentException();
    }
  }

  /**
   * Generates an raster image for a legacy icon.
   */
  @NotNull
  private AnnotatedImage generateLegacyImage(@NotNull GraphicGeneratorContext context, @NotNull TvChannelIconOptions options) {
    AnnotatedImage mergedImage = generateMergedLayers(context, options, null);
    BufferedImage image = cropImageToViewport(options, mergedImage.getImage());
    if (options.generatePreviewIcons && options.showSafeZone) {
      drawSafeZone(image);
    }
    return new AnnotatedImage(image, mergedImage.getErrorMessage());
  }

  /** Generates a preview image. */
  @NotNull
  private AnnotatedImage generateViewportPreviewImage(@NotNull GraphicGeneratorContext context, @NotNull TvChannelIconOptions options) {
    AnnotatedImage mergedImage = generateMergedLayers(context, options, null);
    BufferedImage image = cropImageToViewport(options, mergedImage.getImage());
    if (options.showSafeZone) {
      drawSafeZone(image);
    }
    return new AnnotatedImage(image, mergedImage.getErrorMessage());
  }

  private static void drawSafeZone(@NotNull BufferedImage image) {
    Graphics2D gOut = (Graphics2D) image.getGraphics();

    Color c = new Color(0f, 0f, 0f, 0.20f);
    gOut.setColor(c);
    gOut.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    PrimitiveShapesHelper g = new PrimitiveShapesHelper(gOut, 1);
    g.drawCenteredCircle(image.getWidth() / 2, image.getHeight() / 2, roundToInt(image.getWidth() * (33. / 72.)));
    gOut.dispose();
  }

  @Override
  @NotNull
  protected Rectangle getFullBleedRectangle(@NotNull AdaptiveIconOptions options) {
    return scaleRectangle(IMAGE_SIZE_FULL_BLEED_DP, computeScaleFactor(options));
  }

  @Override
  @NotNull
  protected Rectangle getViewportRectangle(@NotNull AdaptiveIconOptions options) {
    return scaleRectangle(IMAGE_SIZE_VIEWPORT_DP, computeScaleFactor(options));
  }

  @Override
  @NotNull
  protected Rectangle getLegacyRectangle(@NotNull AdaptiveIconOptions options) {
    return scaleRectangle(IMAGE_SIZE_LEGACY_DP, computeScaleFactor(options));
  }

  private static double computeScaleFactor(@NotNull IconOptions options) {
    double scaleFactor = getMdpiScaleFactor(getDensity(options));
    if (((TvChannelIconOptions)options).generatePreviewIcons) {
      scaleFactor *= PREVIEW_SCALE;
    }
    return scaleFactor;
  }

  @NotNull
  private static Density getDensity(@NotNull IconOptions options) {
    return ((TvChannelIconOptions)options).previewShape == PreviewShape.LEGACY ? options.density : Density.XXXHIGH;
  }

  @Override
  protected Rectangle getMaxIconRectangle() {
    return IMAGE_SIZE_FULL_BLEED_DP;
  }

  @Override
  protected boolean includeDensity(@NotNull Density density) {
    // TV channel icons is available only in xhdpi.
    return density == LEGACY_DENSITY;
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(TvChannelIconGenerator.class);
  }

  /** Options specific to generating TV channel icons. */
  public static class TvChannelIconOptions extends AdaptiveIconOptions implements Cloneable {
    /** If set, generate a preview image. */
    public PreviewShape previewShape = PreviewShape.NONE;

    public TvChannelIconOptions(boolean forPreview) {
      super(forPreview);
      iconFolderKind = IconFolderKind.MIPMAP;
    }

    @Override
    @NotNull
    public TvChannelIconOptions clone() {
      return (TvChannelIconOptions)super.clone();
    }
  }

  public enum PreviewShape {
    NONE("none", "none"),
    ADAPTIVE("adaptive", "Adaptive (anydpi)"),
    LEGACY("legacy", "Legacy (xhdpi)");

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
