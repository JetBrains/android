/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.npw.assetstudio;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.GuardedBy;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.ide.common.util.AssetUtil;
import com.android.resources.Density;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

/** A {@link GraphicGenerator} that generates Android adaptive icons. */
public class AdaptiveIconGenerator extends GraphicGenerator {
  private static final Rectangle IMAGE_SIZE_VIEW_PORT_WEB_PX = new Rectangle(0, 0, 512, 512);
  private static final Rectangle IMAGE_SIZE_FULL_BLEED_WEB_PX = new Rectangle(0, 0, 768, 768);
  private static final Rectangle IMAGE_SIZE_FULL_BLEED_DP = new Rectangle(0, 0, 108, 108);
  private static final Rectangle IMAGE_SIZE_VIEWPORT_DP = new Rectangle(0, 0, 72, 72);
  private static final Rectangle IMAGE_SIZE_LEGACY_DP = new Rectangle(0, 0, 48, 48);
  private static final Rectangle IMAGE_SIZE_SAFE_ZONE_DP = new Rectangle(0, 0, 66, 66);

  /**
   * Scaling images with {@link AssetUtil#scaledImage(BufferedImage, int, int)} is time consuming
   * (a few milliseconds per operation on a fast Desktop CPU). Since we end up scaling the same
   * images (foreground and background layers) many times during a call to {@link
   * #generateIcons(GraphicGeneratorContext, Options, String)}, this cache is used to decrease the
   * total number of calls to {@link AssetUtil#scaledImage(BufferedImage, int, int)}
   */
  private static class ImageCache {
    @NonNull private final Object lock = new Object();
    @NonNull private final GraphicGeneratorContext context;

    @GuardedBy("lock")
    @NonNull
    private final Map<Key, BufferedImage> map = new HashMap<>();

    public ImageCache(@NonNull GraphicGeneratorContext context) {
      this.context = context;
    }

    @NonNull
    public GraphicGeneratorContext getContext() {
      return context;
    }

    private static class Key {
      @NonNull private final BufferedImage image;
      @NonNull private final Dimension imageRectSize;
      private final boolean useFillColor;
      private final int fillColor;

      public Key(@NonNull BufferedImage image, @NonNull Dimension imageRectSize, boolean useFillColor, int fillColor) {
        this.image = image;
        this.imageRectSize = imageRectSize;
        this.useFillColor = useFillColor;
        this.fillColor = fillColor;
      }

      @Override
      public int hashCode() {
        return Objects.hash(this.image, this.imageRectSize, this.useFillColor, this.fillColor);
      }

      @Override
      public boolean equals(Object obj) {
        if (!(obj instanceof Key)) {
          return false;
        }
        Key other = (Key) obj;
        return Objects.equals(image, other.image)
            && Objects.equals(imageRectSize, other.imageRectSize)
            && useFillColor == other.useFillColor
            && fillColor == other.fillColor;
      }
    }

    @NonNull
    public BufferedImage getOrCreate(
        @NonNull BufferedImage image,
        @NonNull Dimension imageRectSize,
        boolean useFillColor,
        int fillColor,
        Callable<BufferedImage> generator) {
      Key key = new Key(image, imageRectSize, useFillColor, fillColor);

      synchronized (lock) {
        // Initial lookup attempt
        BufferedImage value = map.get(key);
        if (value != null) {
          return value;
        }

        // Value not present, create it
        try {
          value = generator.call();
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
        assert value != null;

        // Store new value
        if (!map.containsKey(key)) {
          map.put(key, value);
        }
        return value;
      }
    }
  }

  @Override
  @NonNull
  public GeneratedIcons generateIcons(@NonNull GraphicGeneratorContext context, @NonNull Options options, @NonNull String name) {
    AdaptiveIconOptions adaptiveIconOptions = (AdaptiveIconOptions) options;
    ImageCache cache = new ImageCache(context);

    List<Callable<GeneratedIcon>> tasks = new ArrayList<>();

    // Generate tasks for icons (background, foreground, legacy) in all densities
    createOutputIconsTasks(cache, name, adaptiveIconOptions, tasks);

    // Generate tasks for drawable xml resource
    createXmlDrawableResourcesTasks(name, adaptiveIconOptions, tasks);

    // Generate tasks for preview images
    createPreviewImagesTasks(cache, adaptiveIconOptions, tasks);

    // Execute tasks in parallel and wait for results
    WaitableExecutor executor = WaitableExecutor.useGlobalSharedThreadPool();
    tasks.forEach(executor::execute);

    List<GeneratedIcon> results;
    try {
      results = executor.waitForTasksWithQuickFail(true);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    // Add task results to the returned list
    GeneratedIcons icons = new GeneratedIcons();
    results.forEach(icons::add);
    return icons;
  }

  private void createOutputIconsTasks(@NonNull ImageCache imageCache, @NonNull String name, @NonNull AdaptiveIconOptions options,
                                      @NonNull List<Callable<GeneratedIcon>> tasks) {
    if (!options.generateOutputIcons) {
      return;
    }
    for (Density density :
        new Density[] {
          Density.MEDIUM, Density.HIGH, Density.XHIGH, Density.XXHIGH, Density.XXXHIGH
        }) {
      AdaptiveIconOptions localOptions = cloneOptions(options);
      localOptions.density = density;
      localOptions.showGrid = false;
      localOptions.showSafeZone = false;

      createOutputIconsForSingleDensityTasks(imageCache, name, localOptions, density, tasks);
    }

    if (options.generateWebIcon) {
      tasks.add(
          () -> {
            AdaptiveIconOptions localOptions = cloneOptions(options);
            localOptions.showGrid = false;
            localOptions.showSafeZone = false;
            localOptions.generateWebIcon = true;
            localOptions.generateOutputIcons = true;
            localOptions.generatePreviewIcons = false;
            localOptions.legacyIconShape = localOptions.webIconShape;
            BufferedImage image = generateLegacyImage(imageCache, localOptions);
            return new GeneratedImageIcon(
                name,
                Paths.get(getIconPath(localOptions, name)),
                IconCategory.WEB,
                Density.NODPI,
                image);
          });
    }
  }

  private void createOutputIconsForSingleDensityTasks(@NonNull ImageCache imageCache, @NonNull String name,
                                                      @NonNull AdaptiveIconOptions options, @NonNull Density density,
                                                      @NonNull List<Callable<GeneratedIcon>> tasks) {
    tasks.add(
        () -> {
          AdaptiveIconOptions foregroundOptions = cloneOptions(options);
          foregroundOptions.generateWebIcon = false;
          foregroundOptions.generatePreviewIcons = false;
          foregroundOptions.generateOutputIcons = true;
          BufferedImage foregroundImage =
              generateAdaptiveIconForegroundLayer(imageCache, foregroundOptions);
          return new GeneratedImageIcon(
              foregroundOptions.foregroundLayerName,
              Paths.get(
                  getIconPath(
                      foregroundOptions,
                      foregroundOptions.foregroundLayerName)),
              IconCategory.ADAPTIVE_FOREGROUND_LAYER,
              density,
              foregroundImage);
        });

    // Generate background mipmap only if we got a background image
    if (backgroundIsImage(options)) {
      tasks.add(
          () -> {
            AdaptiveIconOptions backgroundOptions = cloneOptions(options);
            backgroundOptions.generateWebIcon = false;
            backgroundOptions.generatePreviewIcons = false;
            backgroundOptions.generateOutputIcons = true;
            BufferedImage backgroundImage =
                generateAdaptiveIconBackgroundLayer(imageCache, backgroundOptions);
            return new GeneratedImageIcon(
                backgroundOptions.backgroundLayerName,
                Paths.get(
                    getIconPath(
                        backgroundOptions,
                        backgroundOptions.backgroundLayerName)),
                IconCategory.ADAPTIVE_BACKGROUND_LAYER,
                density,
                backgroundImage);
          });
    }

    if (options.generateLegacyIcon) {
      tasks.add(
          () -> {
            AdaptiveIconOptions legacyOptions = cloneOptions(options);
            legacyOptions.previewShape = PreviewShape.LEGACY;
            legacyOptions.generateWebIcon = false;
            legacyOptions.generatePreviewIcons = false;
            legacyOptions.generateOutputIcons = true;
            BufferedImage legacy = generateLegacyImage(imageCache, legacyOptions);
            return new GeneratedImageIcon(
                name,
                Paths.get(getIconPath(legacyOptions, name)),
                IconCategory.LEGACY,
                density,
                legacy);
          });
    }

    if (options.generateRoundIcon) {
      tasks.add(
          () -> {
            AdaptiveIconOptions legacyOptions = cloneOptions(options);
            legacyOptions.previewShape = PreviewShape.LEGACY_ROUND;
            legacyOptions.generateWebIcon = false;
            legacyOptions.generatePreviewIcons = false;
            legacyOptions.generateOutputIcons = true;
            legacyOptions.legacyIconShape = Shape.CIRCLE;
            BufferedImage legacyRound = generateLegacyImage(imageCache, legacyOptions);
            return new GeneratedImageIcon(
                name + "_round",
                Paths.get(getIconPath(legacyOptions, name + "_round")),
                IconCategory.ROUND_API_25,
                density,
                legacyRound);
          });
    }
  }

  private void createXmlDrawableResourcesTasks(@NonNull String name, @NonNull AdaptiveIconOptions options,
                                               @NonNull List<Callable<GeneratedIcon>> tasks) {
    if (!options.generateOutputIcons) {
      return;
    }
    AdaptiveIconOptions xmlOptions = cloneOptions(options);
    xmlOptions.density = Density.ANYDPI;
    xmlOptions.generateWebIcon = false;
    xmlOptions.iconFolderKind = IconFolderKind.MIPMAP_V26;

    tasks.add(
        () -> {
          String xmlAdaptiveIcon = getAdaptiveIconXml(xmlOptions);
          return new GeneratedXmlResource(
              name,
              Paths.get(getIconPath(xmlOptions, name)),
              IconCategory.XML_RESOURCE,
              xmlAdaptiveIcon);
        });

    tasks.add(
        () -> {
          String xmlAdaptiveIcon = getAdaptiveIconXml(xmlOptions);
          return new GeneratedXmlResource(
              name + "_round",
              Paths.get(getIconPath(xmlOptions, name + "_round")),
              IconCategory.XML_RESOURCE,
              xmlAdaptiveIcon);
        });

    // Generate color value
    if (xmlOptions.backgroundImage == null) {
      tasks.add(
          () -> {
            AdaptiveIconGenerator.AdaptiveIconOptions iconPathOptions =
                cloneOptions(xmlOptions);
            iconPathOptions.generateWebIcon = false;
            iconPathOptions.density = Density.ANYDPI;
            iconPathOptions.iconFolderKind = IconFolderKind.VALUES;

            String xmlColor =
                String.format(
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<resources>\n"
                        + "    <color name=\"%s\">#%06X</color>\n"
                        + "</resources>",
                    xmlOptions.backgroundLayerName,
                    (xmlOptions.backgroundColor & 0xFF_FF_FF));
            return new GeneratedXmlResource(
                name,
                Paths.get(
                    getIconPath(
                        iconPathOptions, xmlOptions.backgroundLayerName)),
                IconCategory.XML_RESOURCE,
                xmlColor);
          });
    }
  }

  @NonNull
  private static String getAdaptiveIconXml(@NonNull AdaptiveIconOptions options) {
    String xmlAdaptiveIcon;
    if (backgroundIsImage(options)) {
      String xmlFormat =
          "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
              + "<adaptive-icon xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
              + "    <background android:drawable=\"@mipmap/%s\"/>\n"
              + "    <foreground android:drawable=\"@mipmap/%s\"/>\n"
              + "</adaptive-icon>";
      xmlAdaptiveIcon = String.format(xmlFormat, options.backgroundLayerName, options.foregroundLayerName);
    } else {
      String xmlFormat =
          "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
              + "<adaptive-icon xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
              + "    <background android:drawable=\"@color/%s\"/>\n"
              + "    <foreground android:drawable=\"@mipmap/%s\"/>\n"
              + "</adaptive-icon>";
      xmlAdaptiveIcon = String.format(xmlFormat, options.backgroundLayerName, options.foregroundLayerName);
    }
    return xmlAdaptiveIcon;
  }

  private static boolean backgroundIsImage(@NonNull AdaptiveIconOptions adaptiveIconOptions) {
    return adaptiveIconOptions.backgroundImage != null;
  }

  private static void createPreviewImagesTasks(
      @NonNull ImageCache imageCache,
      @NonNull AdaptiveIconOptions options,
      @NonNull List<Callable<GeneratedIcon>> tasks) {
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
    if (options.generateWebIcon) {
      previewShapes.add(PreviewShape.WEB);
    }

    for (PreviewShape previewShape : previewShapes) {
      tasks.add(
          () -> {
            AdaptiveIconOptions localOptions = cloneOptions(options);
            localOptions.density = options.previewDensity;
            localOptions.previewShape = previewShape;
            localOptions.generateLegacyIcon = (previewShape == PreviewShape.LEGACY);
            localOptions.generateRoundIcon =
                (previewShape == PreviewShape.LEGACY_ROUND);
            localOptions.generateWebIcon = (previewShape == PreviewShape.WEB);

            BufferedImage image = generatePreviewImage(imageCache, localOptions);
            return new GeneratedImageIcon(
                previewShape.id,
                null, // no path for preview icons
                IconCategory.PREVIEW,
                localOptions.density,
                image);
          });
    }
  }

  @Override
  public void generate(String category, Map<String, Map<String, BufferedImage>> categoryMap, GraphicGeneratorContext context,
                       Options options, String name) {
    AdaptiveIconOptions adaptiveIconOptions = (AdaptiveIconOptions) options;
    AdaptiveIconOptions localOptions = cloneOptions(adaptiveIconOptions);
    localOptions.generateWebIcon = false;

    GeneratedIcons icons = generateIcons(context, options, name);
    icons.getList()
        .stream()
        .filter(x -> x instanceof GeneratedImageIcon)
        .map(x -> (GeneratedImageIcon) x)
        .filter(x -> x.getOutputPath() != null)
        .forEach(
            x -> {
              assert x.getOutputPath() != null;

              Map<String, BufferedImage> imageMap =
                  categoryMap.computeIfAbsent(x.getCategory().toString(), k -> new LinkedHashMap<>());

              // Store image in map, where the key is the relative path to the image
              AdaptiveIconOptions iconOptions = cloneOptions(localOptions);
              iconOptions.density = x.getDensity();
              iconOptions.iconFolderKind = IconFolderKind.MIPMAP;
              iconOptions.generateWebIcon = (x.getCategory() == IconCategory.WEB);
              imageMap.put(x.getOutputPath().toString(), x.getImage());
            });
  }

  @NonNull
  @Override
  public BufferedImage generate(@NonNull GraphicGeneratorContext context, @NonNull Options options) {
    ImageCache imageCache = new ImageCache(context);
    return generatePreviewImage(imageCache, (AdaptiveIconOptions) options);
  }

  @NonNull
  private static BufferedImage generatePreviewImage(@NonNull ImageCache imageCache, @NonNull AdaptiveIconOptions options) {
    switch (options.previewShape) {
      case CIRCLE:
      case SQUIRCLE:
      case ROUNDED_SQUARE:
      case SQUARE:
        return generateViewportPreviewImage(imageCache, options);

      case LEGACY:
        options.generatePreviewIcons = true;
        options.generateWebIcon = false;
        return generateLegacyImage(imageCache, options);

      case LEGACY_ROUND:
        options.generatePreviewIcons = true;
        options.generateWebIcon = false;
        options.legacyIconShape = Shape.CIRCLE;
        return generateLegacyImage(imageCache, options);

      case FULL_BLEED:
        {
          BufferedImage image = generateFullBleedPreviewImage(imageCache, options);
          // For preview, scale image down so that it does not display relatively
          // too big compared to the other preview icons.
          return scaledPreviewImage(image, 0.8f);
        }

      case WEB:
        {
          options.generatePreviewIcons = true;
          options.generateWebIcon = true;
          options.legacyIconShape = options.webIconShape;
          BufferedImage image = generateLegacyImage(imageCache, options);
          image = AssetUtil.trimmedImage(image);
          // For preview, scale image down so that it does not display relatively
          // too big compared to the other preview icons.
          float scale = getMdpiScaleFactor(options.previewDensity);
          return scaledPreviewImage(image, 0.25f * scale);
        }

      case NONE:
      default:
        throw new IllegalArgumentException();
    }
  }

  @NonNull
  private static AdaptiveIconOptions cloneOptions(@NonNull AdaptiveIconOptions options) {
    AdaptiveIconOptions localOptions = new AdaptiveIconOptions();

    localOptions.minSdk = options.minSdk;
    localOptions.sourceImage = options.sourceImage;
    localOptions.backgroundImage = options.backgroundImage;
    localOptions.density = options.density;
    localOptions.previewDensity = options.previewDensity;
    localOptions.iconFolderKind = options.iconFolderKind;

    localOptions.useForegroundColor = options.useForegroundColor;
    localOptions.foregroundColor = options.foregroundColor;
    localOptions.backgroundColor = options.backgroundColor;
    localOptions.generateLegacyIcon = options.generateLegacyIcon;
    localOptions.generateRoundIcon = options.generateRoundIcon;
    localOptions.generateWebIcon = options.generateWebIcon;
    localOptions.generateOutputIcons = options.generateOutputIcons;
    localOptions.generatePreviewIcons = options.generatePreviewIcons;

    localOptions.previewShape = options.previewShape;
    localOptions.legacyIconShape = options.legacyIconShape;
    localOptions.webIconShape = options.webIconShape;
    localOptions.showGrid = options.showGrid;
    localOptions.showSafeZone = options.showSafeZone;
    localOptions.foregroundLayerName = options.foregroundLayerName;
    localOptions.backgroundLayerName = options.backgroundLayerName;

    return localOptions;
  }

  @SuppressWarnings("UseJBColor")
  @NonNull
  private static BufferedImage generateFullBleedPreviewImage(@NonNull ImageCache imageCache, @NonNull AdaptiveIconOptions options) {
    Layers layers = generateAdaptiveIconLayers(imageCache, options);
    BufferedImage result = mergeLayers(layers, Color.BLACK);
    drawGrid(options, result);
    return result;
  }

  /**
   * Generate a {@link BufferedImage} for either a "Legacy", "Round" or "Web" icon. The created
   * image consists of both background and foregroud layer images merge together, then a shape
   * (e.g. circle, square) mask is applied, and finally the image is scaled to the appropriate
   * size (48x48 legacy or 512x512px web).
   */
  @NonNull
  private static BufferedImage generateLegacyImage(@NonNull ImageCache imageCache, @NonNull AdaptiveIconOptions options) {
    // The "Web" density does not exist in the "Density" enum. Various "Legacy" icon APIs use
    // "null" as a placeholder for "Web".
    Density legacyOrWebDensity = (options.generateWebIcon ? null : options.density);

    // The viewport rectangle (72x72dp) scaled according to density
    Rectangle viewportRect = getViewportRectangle(options);

    // The "Legacy" icon rectangle (48x48dp) scaled according to density
    Rectangle legacyRect = getLegacyRectangle(options);

    // The sub-rectangle of the 48x48dp "Legacy" icon that corresponds to the "Legacy" icon
    // shape, scaled according to the density
    Rectangle legacyShapeRect =LauncherIconGenerator.getTargetRect(options.legacyIconShape, legacyOrWebDensity);

    // Generate full bleed and viewport images
    Layers layers = generateAdaptiveIconLayers(imageCache, options);
    BufferedImage fullBleed = mergeLayers(layers);

    // Scale the "Full Bleed" icon so that it is contained in the "Legacy" shape rectangle.
    //
    // Note that even though we scale the "Full Bleed" image, we use the ratio of the
    // Viewport rectangle (72x72dp) to Legacy shape (sub-rectangle of 48x48dp) as the
    // scaling factor, because the Viewport rectangle is the visible part of Adaptive icons,
    // whereas the "Full Bleed" icon is never entirely visible.
    float viewportScale = getRectangleInsideScale(viewportRect, legacyShapeRect);
    BufferedImage scaledFullBleed =
        options.generatePreviewIcons ? scaledPreviewImage(fullBleed, viewportScale) : scaledImage(fullBleed, viewportScale);

    // Load shadow and mask corresponding to legacy shape
    BufferedImage shapeImageBack = null;
    BufferedImage shapeImageFore = null;
    BufferedImage shapeImageMask = null;
    if (options.legacyIconShape != Shape.NONE) {
      shapeImageBack =LauncherIconGenerator.loadBackImage(imageCache.getContext(), options.legacyIconShape, legacyOrWebDensity);
      shapeImageFore =
          LauncherIconGenerator.loadStyleImage(imageCache.getContext(), options.legacyIconShape, legacyOrWebDensity, Style.SIMPLE);
      shapeImageMask = LauncherIconGenerator.loadMaskImage(imageCache.getContext(), options.legacyIconShape, legacyOrWebDensity);
    }

    // Generate legacy image by merging shadow, mask and (scaled) adaptive icon
    BufferedImage legacyImage =
        AssetUtil.newArgbBufferedImage(legacyRect.width, legacyRect.height);
    Graphics2D gLegacy = (Graphics2D) legacyImage.getGraphics();

    // Start with backdrop image (semi transparent shadow)
    if (shapeImageBack != null) {
      AssetUtil.drawCentered(gLegacy, shapeImageBack, legacyRect);
    }

    // Apply the mask to the scaled adaptive icon
    if (shapeImageMask != null) {
      scaledFullBleed = applyMask(scaledFullBleed, shapeImageMask);
    }

    // Draw the scaled adaptive icon on top of shadow effect
    AssetUtil.drawCentered(gLegacy, scaledFullBleed, legacyRect);

    // Finish with the foreground effect (shadow outline)
    if (shapeImageFore != null) {
      gLegacy.drawImage(shapeImageFore, 0, 0, null);
    }
    gLegacy.dispose();
    return legacyImage;
  }

  /** See {@link AssetUtil#getRectangleInsideScale(Rectangle, Rectangle)}. */
  private static float getRectangleInsideScale(@NonNull Rectangle source, @NonNull Rectangle destination) {
    return AssetUtil.getRectangleInsideScale(source, destination);
  }

  /** Scale an image given a scale factor. */
  @NonNull
  private static BufferedImage scaledImage(@NonNull BufferedImage image, float scale) {
    int width = Math.round(image.getWidth() * scale);
    int height = Math.round(image.getHeight() * scale);
    return scaledImage(image, width, height);
  }

  /** Scale an image given the desired scaled image size. */
  @NonNull
  public static BufferedImage scaledImage(@NonNull BufferedImage image, int width, int height) {
    return AssetUtil.scaledImage(image, width, height);
  }

  /**
   * For performance reason, we use a lower qualitty (but faster) image scaling algorithm when
   * generating preview images.
   */
  @NonNull
  private static BufferedImage scaledPreviewImage(@NonNull BufferedImage image, float scale) {
    int width = Math.round(image.getWidth() * scale);
    int height = Math.round(image.getHeight() * scale);
    return scaledPreviewImage(image, width, height);
  }

  /**
   * For performance reason, we use a lower qualitty (but faster) image scaling algorithm when
   * generating preview images.
   */
  @NonNull
  public static BufferedImage scaledPreviewImage(@NonNull BufferedImage source, int width, int height) {
    // Common case optimization: scaling to the same (width, height) should be a no-op, but
    // the source.getScaledInstance call below is actually CPU intensive.
    if (source.getWidth() == width && source.getHeight() == height) {
      return source;
    }

    BufferedImage scaledBufImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = scaledBufImage.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.drawImage(source, 0, 0, width, height, null);
    g.dispose();
    return scaledBufImage;
  }

  /** Generate a preview image with a Shape mask applied (e.g. Square, Squircle). */
  @NonNull
  private static BufferedImage generateViewportPreviewImage(@NonNull ImageCache imageCache, @NonNull AdaptiveIconOptions options) {
    Layers layers = generateAdaptiveIconLayers(imageCache, options);
    BufferedImage result = mergeLayers(layers);
    BufferedImage mask = generateMaskLayer(imageCache, options, options.previewShape);
    result = cropImageToViewport(options, result);
    result = applyMask(result, mask);
    drawGrid(options, result);

    return result;
  }

  private static BufferedImage cropImageToViewport(@NonNull AdaptiveIconOptions options, @NonNull BufferedImage image) {
    return cropImage(image, getViewportRectangle(options));
  }

  private static BufferedImage cropImage(@NonNull BufferedImage image, @NonNull Rectangle targetRect) {
    Rectangle imageRect = new Rectangle(0, 0, image.getWidth(), image.getHeight());

    BufferedImage subImage =
        image.getSubimage(
            (imageRect.width - targetRect.width) / 2,
            (imageRect.height - targetRect.height) / 2,
            targetRect.width,
            targetRect.height);

    BufferedImage viewportImage = AssetUtil.newArgbBufferedImage(targetRect.width, targetRect.height);

    Graphics2D gViewport = (Graphics2D) viewportImage.getGraphics();
    gViewport.drawImage(subImage, 0, 0, null);
    gViewport.dispose();

    return viewportImage;
  }

  @NonNull
  private static BufferedImage mergeLayers(@NonNull Layers layers) {
    return mergeLayers(layers, null);
  }

  @NonNull
  private static BufferedImage mergeLayers(@NonNull Layers layers, @Nullable Color fillColor) {
    int width = Math.max(layers.background.getWidth(), layers.foreground.getWidth());
    int height = Math.max(layers.background.getHeight(), layers.foreground.getHeight());

    BufferedImage outImage = AssetUtil.newArgbBufferedImage(width, height);
    Graphics2D gOut = (Graphics2D) outImage.getGraphics();
    if (fillColor != null) {
      gOut.setPaint(fillColor);
      gOut.fillRect(0, 0, width, height);
    }
    gOut.drawImage(layers.background, 0, 0, null);
    gOut.drawImage(layers.foreground, 0, 0, null);
    gOut.dispose();

    return outImage;
  }

  private static class Layers {
    public BufferedImage background;
    public BufferedImage foreground;

    public Layers(@NonNull BufferedImage background, @NonNull BufferedImage foreground) {
      this.background = background;
      this.foreground = foreground;
    }
  }

  @NonNull
  private static Layers generateAdaptiveIconLayers(@NonNull ImageCache imageCache, @NonNull AdaptiveIconOptions options) {
    BufferedImage backgroundImage = generateAdaptiveIconBackgroundLayer(imageCache, options);
    BufferedImage foregroundImage = generateAdaptiveIconForegroundLayer(imageCache, options);

    return new Layers(backgroundImage, foregroundImage);
  }

  @Nullable
  private static BufferedImage generateMaskLayer(@NonNull ImageCache imageCache, @NonNull AdaptiveIconOptions options,
                                                 @NonNull PreviewShape shape) {
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

    if (options.generateWebIcon) {
      String resourceName = String.format("/images/adaptive_icons_masks/adaptive_%s-%s.png", maskName, Density.XXXHIGH.getResourceValue());

      BufferedImage mask = imageCache.getContext().loadImageResource(resourceName);
      if (mask == null) {
        return null;
      }
      Rectangle maskRect = new Rectangle(0, 0, mask.getWidth(), mask.getHeight());
      float scale = getRectangleInsideScale(maskRect, getViewportRectangle(options));
      return options.generatePreviewIcons ? scaledPreviewImage(mask, scale) : scaledImage(mask, scale);
    } else {
      String resourceName = String.format("/images/adaptive_icons_masks/adaptive_%s-%s.png", maskName, options.density.getResourceValue());
      return imageCache.getContext().loadImageResource(resourceName);
    }
  }

  @NonNull
  private static Rectangle getFullBleedRectangle(@NonNull AdaptiveIconOptions options) {
    if (options.generateWebIcon) {
      return IMAGE_SIZE_FULL_BLEED_WEB_PX;
    }
    return AssetUtil.scaleRectangle(IMAGE_SIZE_FULL_BLEED_DP, GraphicGenerator.getMdpiScaleFactor(options.density));
  }

  @NonNull
  private static Rectangle getViewportRectangle(@NonNull AdaptiveIconOptions options) {
    if (options.generateWebIcon) {
      return IMAGE_SIZE_VIEW_PORT_WEB_PX;
    }
    return AssetUtil.scaleRectangle(IMAGE_SIZE_VIEWPORT_DP, GraphicGenerator.getMdpiScaleFactor(options.density));
  }

  @NonNull
  private static Rectangle getLegacyRectangle(@NonNull AdaptiveIconOptions options) {
    if (options.generateWebIcon) {
      return IMAGE_SIZE_VIEW_PORT_WEB_PX;
    }
    return AssetUtil.scaleRectangle(
        IMAGE_SIZE_LEGACY_DP, GraphicGenerator.getMdpiScaleFactor(options.density));
  }

  @NonNull
  private static BufferedImage generateAdaptiveIconBackgroundLayer(@NonNull ImageCache imageCache, @NonNull AdaptiveIconOptions options) {
    Rectangle imageRect = getFullBleedRectangle(options);
    if (backgroundIsImage(options)) {
      return generateAdaptiveIconLayer(imageCache, options.backgroundImage, imageRect, false, 0);
    } else {
      return generateAdaptiveIconBackgroundLayerFlatColor(options, imageRect);
    }
  }

  @NonNull
  private static BufferedImage generateAdaptiveIconForegroundLayer(ImageCache imageCache, @NonNull AdaptiveIconOptions options) {
    Rectangle imageRect = getFullBleedRectangle(options);

    return generateAdaptiveIconLayer(imageCache, options.sourceImage, imageRect, options.useForegroundColor, options.foregroundColor);
  }

  @NonNull
  private static BufferedImage generateAdaptiveIconBackgroundLayerFlatColor(
      @NonNull AdaptiveIconOptions options, @NonNull Rectangle imageRect) {
    BufferedImage tempImage = AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
    Graphics2D gTemp = (Graphics2D) tempImage.getGraphics();
    //noinspection UseJBColor
    gTemp.setPaint(new Color(options.backgroundColor));
    gTemp.fillRect(0, 0, imageRect.width, imageRect.height);
    gTemp.dispose();
    return tempImage;
  }

  @NonNull
  private static BufferedImage applyMask(@NonNull BufferedImage image, @Nullable BufferedImage mask) {
    if (mask == null) {
      return image;
    }

    Rectangle imageRect = new Rectangle(0, 0, image.getWidth(), image.getHeight());
    BufferedImage tempImage = AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);

    Graphics2D gTemp = (Graphics2D) tempImage.getGraphics();
    AssetUtil.drawCentered(gTemp, mask, imageRect);
    gTemp.setComposite(AlphaComposite.SrcIn);
    AssetUtil.drawCentered(gTemp, image, imageRect);
    gTemp.dispose();

    return tempImage;
  }

  @NonNull
  private static BufferedImage generateAdaptiveIconLayer(@NonNull ImageCache imageCache, @NonNull BufferedImage sourceImage,
                                                         @NonNull Rectangle imageRect, boolean useFillColor, int fillColor) {
    return imageCache.getOrCreate(
        sourceImage,
        imageRect.getSize(),
        useFillColor,
        fillColor,
        () -> {
          // Draw the source image with effect
          BufferedImage iconImage =
              AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
          Graphics2D gIcon = (Graphics2D) iconImage.getGraphics();
          AssetUtil.drawCenterInside(gIcon, sourceImage, imageRect);
          AssetUtil.Effect[] effects;
          if (useFillColor) {
            //noinspection UseJBColor
            effects =
                new AssetUtil.Effect[] {
                  new AssetUtil.FillEffect(new Color(fillColor), 1.0)
                };
          } else {
            effects = new AssetUtil.Effect[0];
          }

          BufferedImage effectImage =
              AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
          Graphics2D gEffect = (Graphics2D) effectImage.getGraphics();
          AssetUtil.drawEffects(gEffect, iconImage, 0, 0, effects);
          gIcon.dispose();
          gEffect.dispose();

          return effectImage;
        });
  }

  private static void drawGrid(@NonNull AdaptiveIconOptions adaptiveIconOptions, @NonNull BufferedImage image) {
    Graphics2D gOut = (Graphics2D) image.getGraphics();
    drawGrid(adaptiveIconOptions, gOut);
    gOut.dispose();
  }

  private static void drawGrid(@NonNull AdaptiveIconOptions adaptiveIconOptions, @NonNull Graphics2D gOut) {
    if (adaptiveIconOptions.generateWebIcon) {
      return;
    }

    if (adaptiveIconOptions.previewShape == PreviewShape.FULL_BLEED) {
      if (adaptiveIconOptions.showGrid || adaptiveIconOptions.showSafeZone) {
        drawFullBleedIconGrid(adaptiveIconOptions, gOut);
      }
      return;
    }

    if (adaptiveIconOptions.previewShape == PreviewShape.LEGACY
        || adaptiveIconOptions.previewShape == PreviewShape.LEGACY_ROUND) {
      if (adaptiveIconOptions.showGrid) {
        drawLegacyIconGrid(adaptiveIconOptions, gOut);
      }
      return;
    }

    if (adaptiveIconOptions.showGrid || adaptiveIconOptions.showSafeZone) {
      drawAdaptiveIconGrid(adaptiveIconOptions, gOut);
    }
  }

  private static void drawAdaptiveIconGrid(@NonNull AdaptiveIconOptions options, @NonNull Graphics2D out) {
    float scaleFactor = GraphicGenerator.getMdpiScaleFactor(options.density);

    // 72x72
    int size = IMAGE_SIZE_VIEWPORT_DP.width;
    int center = size / 2;

    //noinspection UseJBColor
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

  private static void drawFullBleedIconGrid(@NonNull AdaptiveIconOptions options, @NonNull Graphics2D out) {
    float scaleFactor = GraphicGenerator.getMdpiScaleFactor(options.density);

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

  private static void drawLegacyIconGrid(@NonNull AdaptiveIconOptions options, @NonNull Graphics2D out) {
    float scaleFactor = GraphicGenerator.getMdpiScaleFactor(options.density);

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
  protected boolean includeDensity(@NonNull Density density) {
    // Launcher icons should include xxxhdpi as well
    return super.includeDensity(density) || density == Density.XXXHIGH;
  }

  @NonNull
  @Override
  protected String getIconPath(@NonNull Options options, @NonNull String name) {
    if (((AdaptiveIconOptions) options).generateWebIcon) {
      return name + "-web.png"; // Store at the root of the project
    }

    return super.getIconPath(options, name);
  }

  /** Options specific to generating launcher icons */
  public static class AdaptiveIconOptions extends Options {
    public AdaptiveIconOptions() {
      iconFolderKind = IconFolderKind.MIPMAP;
    }

    /** The foreground layer name, used to generate resource paths */
    public String foregroundLayerName;

    /** The background layer name, used to generate resource paths */
    public String backgroundLayerName;

    /**
     * Whether to use the foreground color. If we are using images as the source asset for our
     * icons, you shouldn't apply the foreground color, which would paint over it and obscure
     * the image.
     */
    public boolean useForegroundColor = true;

    /** Foreground color, as an RRGGBB packed integer */
    public int foregroundColor = 0;

    /**
     * Background color, as an RRGGBB packed integer. The background color is used only if
     * {@link #backgroundImage} is <code>null</code>.
     */
    public int backgroundColor = 0;

    /** Source image to use as a basis for the icon background layer (optional) */
    public BufferedImage backgroundImage;

    /** Whether to actual icons to be written to disk */
    public boolean generateOutputIcons = true;

    /** Whether to actual preview icons */
    public boolean generatePreviewIcons = true;

    /** Whether to generate the "Legacy" icon (API <= 24) */
    public boolean generateLegacyIcon = true;

    /** Whether to generate the "Round" icon (API 25) */
    public boolean generateRoundIcon = true;

    /**
     * Whether a web graphic should be generated (will ignore normal density setting). The
     * {@link #generate(GraphicGeneratorContext, Options)} method will use this to decide
     * whether to generate a normal density icon or a high res web image. The {@link
     * GraphicGenerator#generate(String, Map, GraphicGeneratorContext, Options, String)} method
     * will use this flag to determine whether it should include a web graphic in its iteration.
     */
    public boolean generateWebIcon;

    /** If set, generate a preview image */
    public PreviewShape previewShape = PreviewShape.NONE;

    /** The density of the preview images */
    public Density previewDensity;

    /** The shape to use for the "Legacy" icon */
    public Shape legacyIconShape = Shape.SQUARE;

    /** The shape to use for the "Web" icon */
    public Shape webIconShape = Shape.SQUARE;

    /** Whether to draw the keyline shapes */
    public boolean showGrid;

    /** Whether to draw the safe zone circle */
    public boolean showSafeZone;
  }

  public enum PreviewShape {
    NONE("none", "none"),
    CIRCLE("circle", "Circle"),
    SQUIRCLE("squircle", "Squircle"),
    ROUNDED_SQUARE("rounded-square", "Rounded Square"),
    SQUARE("square", "Square"),
    FULL_BLEED("full-bleed-layers", "Full Bleed Layers"),
    LEGACY("legacy", "Legacy Icon"),
    LEGACY_ROUND("legacy-round", "Round Icon"),
    WEB("web", "Google Play Store");

    /** Id, used when shape is converted to a string */
    public final String id;
    /** Display name, when shape is displayed to the end-user */
    public final String displayName;

    PreviewShape(String id, String displayName) {
      this.id = id;
      this.displayName = displayName;
    }
  }
}
