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
import static com.android.tools.idea.npw.assetstudio.AssetStudioUtils.scaleRectangleAroundCenter;
import static com.android.tools.idea.npw.assetstudio.VectorDrawableTransformer.transform;
import static java.lang.Math.max;

import com.android.ide.common.util.AssetUtil;
import com.android.ide.common.util.PathString;
import com.android.resources.Density;
import com.android.tools.idea.concurrency.FutureUtils;
import com.android.tools.idea.npw.assetstudio.assets.ImageAsset;
import com.android.tools.idea.npw.assetstudio.assets.TextAsset;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObjectProperty;
import com.android.tools.idea.observable.core.ObjectValueProperty;
import com.android.tools.idea.observable.core.OptionalProperty;
import com.android.tools.idea.observable.core.OptionalValueProperty;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.core.StringValueProperty;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ExceptionUtil;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Generator of Android TV banner icons.
 */
@SuppressWarnings("UseJBColor") // Android icons don't need JBColor.
public class TvBannerGenerator extends IconGenerator {
  public static final Color DEFAULT_BACKGROUND_COLOR = new Color(0xFFFFFF);
  private static final Rectangle IMAGE_SIZE_ADAPTIVE_DP = new Rectangle(0, 0, 320, 180);
  private static final Dimension SIZE_ADAPTIVE_DP = IMAGE_SIZE_ADAPTIVE_DP.getSize();
  private static final double ADAPTIVE_ICON_SCALE_FACTOR = 72. / 108.;
  private static final Density LEGACY_DENSITY = Density.XHIGH;
  /** Ratio between image and text width when both are present. */
  private static final double IMAGE_TEXT_RATIO = 0.4;
  /** Default text scale to have some room to grow. */
  private static final double DEFAULT_TEXT_SCALE = 0.82;
  /** Margin between image and the edge of the icon relative to the icon width. */
  private static final double IMAGE_MARGIN = 0.03;
  private static final double PREVIEW_SCALE = 0.31;

  private final ObjectProperty<Color> myBackgroundColor = new ObjectValueProperty<>(DEFAULT_BACKGROUND_COLOR);
  private final BoolProperty myGenerateLegacyIcon = new BoolValueProperty(true);
  private final OptionalProperty<TextAsset> myTextAsset = new OptionalValueProperty<>();
  private final OptionalProperty<ImageAsset> myBackgroundImageAsset = new OptionalValueProperty<>();
  private final StringProperty myForegroundLayerName = new StringValueProperty();
  private final StringProperty myBackgroundLayerName = new StringValueProperty();

  /**
   * Initializes the icon generator. Every icon generator has to be disposed of by calling {@link #dispose()}.
   *
   * @param project the Android project
   * @param minSdkVersion the minimal supported Android SDK version
   */
  public TvBannerGenerator(@NotNull Project project, int minSdkVersion, @Nullable DrawableRenderer renderer) {
    super(project, minSdkVersion, new GraphicGeneratorContext(40, renderer));
  }

  /**
   * A color for rendering the background shape.
   */
  @NotNull
  public ObjectProperty<Color> backgroundColor() {
    return myBackgroundColor;
  }

  /**
   * If {@code true}, generate the "Legacy" icon (API 24 and earlier)
   */
  @NotNull
  public BoolProperty generateLegacyIcon() {
    return myGenerateLegacyIcon;
  }

  @NotNull
  public OptionalProperty<TextAsset> textAsset() {
    return myTextAsset;
  }

  @NotNull
  public OptionalProperty<ImageAsset> backgroundImageAsset() {
    return myBackgroundImageAsset;
  }

  @NotNull
  public StringProperty foregroundLayerName() {
    return myForegroundLayerName;
  }

  @NotNull
  public StringProperty backgroundLayerName() {
    return myBackgroundLayerName;
  }

  @Override
  @NotNull
  public TvBannerOptions createOptions(boolean forPreview) {
    TvBannerOptions options = new TvBannerOptions(forPreview);
    // Set foreground image.
    ImageAsset foregroundAsset = (ImageAsset)sourceAsset().getValueOrNull();
    if (foregroundAsset != null && foregroundAsset.imagePath().getValueOrNull() != null) {
      double scaleFactor = foregroundAsset.scalingPercent().get() / 100.;
      options.foregroundImage =
          new TransformedImageAsset(foregroundAsset, SIZE_ADAPTIVE_DP, scaleFactor, null, getGraphicGeneratorContext());
    }

    TextAsset textAsset = myTextAsset.getValueOrNull();
    if (textAsset != null && !StringUtil.trimTrailing(textAsset.text().get()).isEmpty()) {
      double scaleFactor = textAsset.scalingPercent().get() / 100.;
      options.foregroundText = new TransformedImageAsset(textAsset, SIZE_ADAPTIVE_DP, scaleFactor, null, getGraphicGeneratorContext());
      Color color = textAsset.color().getValueOrNull();
      options.foregroundTextColor = color == null ? 0 : color.getRGB();
    }

    shiftImageAndText(options);

    // Set background image.
    ImageAsset backgroundAsset = myBackgroundImageAsset.getValueOrNull();
    if (backgroundAsset != null) {
      double scaleFactor = backgroundAsset.scalingPercent().get() / 100.;
      options.backgroundImage =
          new TransformedImageAsset(backgroundAsset, SIZE_ADAPTIVE_DP, scaleFactor, null, getGraphicGeneratorContext());
    }

    options.backgroundColor = myBackgroundColor.get().getRGB();
    options.foregroundLayerName = myForegroundLayerName.get();
    options.backgroundLayerName = myBackgroundLayerName.get();
    options.generateLegacyIcon = myGenerateLegacyIcon.get();
    return options;
  }

  @Override
  @NotNull
  protected List<Callable<GeneratedIcon>> createIconGenerationTasks(
      @NotNull GraphicGeneratorContext context, @NotNull IconOptions options, @NotNull String name) {
    TvBannerOptions tvBannerOptions = (TvBannerOptions)options;

    List<Callable<GeneratedIcon>> tasks = new ArrayList<>();

    // Generate tasks for icons (background, foreground, legacy) in all densities.
    createOutputIconsTasks(context, name, tvBannerOptions, tasks);

    // Generate tasks for drawable xml resource.
    createXmlDrawableResourcesTasks(name, tvBannerOptions, tasks);

    // Generate tasks for preview images.
    createPreviewImagesTasks(context, tvBannerOptions, tasks);
    return tasks;
  }

  private void createOutputIconsTasks(@NotNull GraphicGeneratorContext context, @NotNull String name, @NotNull TvBannerOptions options,
                                      @NotNull List<Callable<GeneratedIcon>> tasks) {
    if (!options.generateOutputIcons) {
      return;
    }

    TvBannerOptions localOptions =  options.clone();
    localOptions.density = LEGACY_DENSITY;

    createOutputIconsForSingleDensityTasks(context, name, localOptions, tasks);
  }

  private void createOutputIconsForSingleDensityTasks(@NotNull GraphicGeneratorContext context, @NotNull String name,
                                                      @NotNull TvBannerOptions options, @NotNull List<Callable<GeneratedIcon>> tasks) {
    // Generate foreground mipmap only if the foreground is a raster image.
    if (options.foregroundImage != null && options.foregroundImage.isRasterImage()) {
      tasks.add(() -> {
        TvBannerOptions foregroundOptions = options.clone();
        foregroundOptions.generatePreviewIcons = false;
        foregroundOptions.generateOutputIcons = true;
        AnnotatedImage foregroundImage = generateIconForegroundLayer(foregroundOptions, false);
        return new GeneratedImageIcon(foregroundOptions.foregroundLayerName,
                                      new PathString(getIconPath(foregroundOptions, options.foregroundLayerName)),
                                      IconCategory.ADAPTIVE_FOREGROUND_LAYER,
                                      options.density,
                                      foregroundImage);
      });
    }

    // Generate background mipmap only if the background is a raster image.
    if (options.backgroundImage != null && options.backgroundImage.isRasterImage()) {
      tasks.add(() -> {
        TvBannerOptions backgroundOptions = options.clone();
        backgroundOptions.generatePreviewIcons = false;
        backgroundOptions.generateOutputIcons = true;
        AnnotatedImage backgroundImage = generateIconBackgroundLayer(context, backgroundOptions, false);
        return new GeneratedImageIcon(backgroundOptions.backgroundLayerName,
                                      new PathString(getIconPath(backgroundOptions, options.backgroundLayerName)),
                                      IconCategory.ADAPTIVE_BACKGROUND_LAYER,
                                      options.density,
                                      backgroundImage);
      });
    }

    if (options.generateLegacyIcon) {
      tasks.add(() -> {
        TvBannerOptions legacyOptions = options.clone();
        legacyOptions.previewShape = PreviewShape.LEGACY;
        legacyOptions.generatePreviewIcons = false;
        legacyOptions.generateOutputIcons = true;
        AnnotatedImage legacy = generateLegacyImage(context, legacyOptions);
        return new GeneratedImageIcon(name,
                                      new PathString(getIconPath(legacyOptions, name)),
                                      IconCategory.LEGACY,
                                      options.density,
                                      legacy);
      });
    }
  }

  private void createXmlDrawableResourcesTasks(@NotNull String name, @NotNull TvBannerOptions options,
                                               @NotNull List<Callable<GeneratedIcon>> tasks) {
    if (!options.generateOutputIcons) {
      return;
    }

    {
      TvBannerOptions iconOptions = options.clone();
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

    TransformedImageAsset image = options.foregroundImage;
    TransformedImageAsset text = options.foregroundText;
    if (image == null ? text != null : image.isDrawable()) {
      // Generate foreground drawable.
      tasks.add(() -> {
        TvBannerOptions iconOptions = options.clone();
        iconOptions.density = Density.ANYDPI;
        iconOptions.iconFolderKind = IconFolderKind.DRAWABLE_NO_DPI;

        String imageDrawable = null;
        String textDrawable = null;
        if (image != null) {
          imageDrawable = image.getTransformedDrawable();
          if (imageDrawable == null) {
            getLog().error("Transformed foreground drawable is null" + (image.isDrawable() ? " but the image is drawable" : ""),
                           new Throwable());
            imageDrawable = "<vector/>"; // Use an empty image. It will be recomputed again soon.
          }
        }

        if (text != null) {
          textDrawable = text.getTransformedDrawable();
          if (textDrawable == null) {
            getLog().error("Transformed foreground text is null", new Throwable());
            textDrawable = "<vector/>"; // Use an empty image. It will be recomputed again soon.
          }
        }

        String xmlDrawableText;
        if (imageDrawable == null) {
          xmlDrawableText = textDrawable;
        }
        else if (textDrawable == null) {
          xmlDrawableText = imageDrawable;
        }
        else {
          xmlDrawableText = VectorDrawableTransformer.merge(imageDrawable, textDrawable);
        }

        xmlDrawableText = applyAdaptiveIconScaleFactor(xmlDrawableText);
        iconOptions.apiVersion = calculateMinRequiredApiLevel(xmlDrawableText, myMinSdkVersion);
        return new GeneratedXmlResource(name,
                                        new PathString(getIconPath(iconOptions, iconOptions.foregroundLayerName)),
                                        IconCategory.ADAPTIVE_FOREGROUND_LAYER,
                                        xmlDrawableText);
      });
    }

    if (options.backgroundImage != null && options.backgroundImage.isDrawable()) {
      // Generate background drawable.
      TransformedImageAsset backgroundImage = options.backgroundImage;
      tasks.add(() -> {
        TvBannerOptions iconOptions = options.clone();
        iconOptions.density = Density.ANYDPI;
        iconOptions.iconFolderKind = IconFolderKind.DRAWABLE_NO_DPI;

        if (!backgroundImage.isDrawable()) {
          getLog().error("Background image is not drawable!", new Throwable());
        }
        String xmlDrawableText = backgroundImage.getTransformedDrawable();
        if (xmlDrawableText == null) {
          getLog().error("Transformed background drawable is null" + (backgroundImage.isDrawable() ? " but the image is drawable" : ""),
                         new Throwable());
          xmlDrawableText = "<vector/>"; // Use an empty image. It will be recomputed again soon.
        }

        xmlDrawableText = applyAdaptiveIconScaleFactor(xmlDrawableText);
        iconOptions.apiVersion = calculateMinRequiredApiLevel(xmlDrawableText, myMinSdkVersion);
        return new GeneratedXmlResource(name,
                                        new PathString(getIconPath(iconOptions, iconOptions.backgroundLayerName)),
                                        IconCategory.ADAPTIVE_BACKGROUND_LAYER,
                                        xmlDrawableText);
      });
    } else if (options.backgroundImage == null) {
      // Generate background color value.
      tasks.add(() -> {
        TvBannerOptions iconOptions = options.clone();
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

  @NotNull
  private static String applyAdaptiveIconScaleFactor(@NotNull String xmlDrawableText) {
    return transform(xmlDrawableText, SIZE_ADAPTIVE_DP, Gravity.CENTER, ADAPTIVE_ICON_SCALE_FACTOR, null, null, null, 1);
  }

  /**
   * Shifts foreground image to the left and text to the right so that they appear next to each other.
   */
  private static void shiftImageAndText(@NotNull TvBannerOptions options) {
    TransformedImageAsset image = options.foregroundImage;
    TransformedImageAsset text = options.foregroundText;
    if (text != null ) {
      if (image != null) {
        image.setShift(new Point2D.Double(IMAGE_TEXT_RATIO / 2 - 0.5 + IMAGE_MARGIN, 0));
        double scaleFactor = text.getScaleFactor();
        double shift = IMAGE_TEXT_RATIO / 2 + (1 - IMAGE_TEXT_RATIO) / 2 * (scaleFactor * DEFAULT_TEXT_SCALE - 1);
        text.setShift(new Point2D.Double(shift, 0));
        text.setGravity(Gravity.WEST);
        text.applyAdditionalScaleFactor((1 - IMAGE_TEXT_RATIO) * DEFAULT_TEXT_SCALE);
      }
      else {
        text.applyAdditionalScaleFactor(DEFAULT_TEXT_SCALE);
      }
    }
  }

  private static BufferedImage mergeImages(@NotNull BufferedImage image1, @NotNull BufferedImage image2) {
    BufferedImage outImage = AssetUtil.newArgbBufferedImage(image1.getWidth(), image1.getHeight());
    Graphics2D gOut = (Graphics2D) outImage.getGraphics();
    gOut.drawImage(image1, 0, 0, null);
    gOut.drawImage(image2, 0, 0, null);
    gOut.dispose();
    return outImage;
  }

  @NotNull
  private String getAdaptiveIconXml(@NotNull TvBannerOptions options) {
    String backgroundType = options.backgroundImage == null ? "color" : options.backgroundImage.isDrawable() ? "drawable" : "mipmap";
    String foregroundType = options.foregroundImage != null && options.foregroundImage.isDrawable() ? "drawable" : "mipmap";
    String format = ""
        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>%1$s"
        + "<adaptive-icon xmlns:android=\"http://schemas.android.com/apk/res/android\">%1$s"
        + "    <background android:drawable=\"@%2$s/%3$s\"/>%1$s"
        + "    <foreground android:drawable=\"@%4$s/%5$s\"/>%1$s"
        + "</adaptive-icon>";
    return String.format(format, myLineSeparator, backgroundType, options.backgroundLayerName, foregroundType, options.foregroundLayerName);
  }

  private static void createPreviewImagesTasks(@NotNull GraphicGeneratorContext context, @NotNull TvBannerOptions options,
                                               @NotNull List<Callable<GeneratedIcon>> tasks) {
    if (!options.generatePreviewIcons) {
      return;
    }

    List<PreviewShape> previewShapes = new ArrayList<>();
    previewShapes.add(PreviewShape.ADAPTIVE);
    if (options.generateLegacyIcon) {
      previewShapes.add(PreviewShape.LEGACY);
    }

    for (PreviewShape previewShape : previewShapes) {
      tasks.add(() -> {
        TvBannerOptions localOptions = options.clone();
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
    TvBannerOptions tvBannerOptions = (TvBannerOptions) options;
    TvBannerOptions localOptions = tvBannerOptions.clone();

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
              TvBannerOptions iconOptions = localOptions.clone();
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

    return generatePreviewImage(context, (TvBannerOptions)options);
  }

  @NotNull
  private static AnnotatedImage generatePreviewImage(@NotNull GraphicGeneratorContext context, @NotNull TvBannerOptions options) {
    switch (options.previewShape) {
      case ADAPTIVE:
      case LEGACY:
        Layers layers = generateIconLayers(context, options, true);
        AnnotatedImage mergedImage = mergeLayers(layers);
        BufferedImage image = mergedImage.getImage();
        return new AnnotatedImage(image, mergedImage.getErrorMessage());

      case NONE:
      default:
        throw new IllegalArgumentException();
    }
  }

  @NotNull
  private static AnnotatedImage generateLegacyImage(@NotNull GraphicGeneratorContext context,
                                                    @NotNull TvBannerOptions options) {
    Layers layers = generateIconLayers(context, options, false);
    AnnotatedImage mergedImage = mergeLayers(layers);
    BufferedImage image = mergedImage.getImage();
    return new AnnotatedImage(image, mergedImage.getErrorMessage());
  }

  @NotNull
  private static AnnotatedImage mergeLayers(@NotNull Layers layers) {
    BufferedImage backgroundImage = layers.background.getImage();
    BufferedImage foregroundImage = layers.foreground.getImage();
    int width = max(backgroundImage.getWidth(), foregroundImage.getWidth());
    int height = max(backgroundImage.getHeight(), foregroundImage.getHeight());

    BufferedImage outImage = AssetUtil.newArgbBufferedImage(width, height);
    Graphics2D gOut = (Graphics2D) outImage.getGraphics();
    gOut.drawImage(backgroundImage, 0, 0, null);
    gOut.drawImage(foregroundImage, 0, 0, null);
    gOut.dispose();

    String errorMessage = layers.foreground.getErrorMessage();
    if (errorMessage == null) {
      errorMessage = layers.background.getErrorMessage();
    }
    return new AnnotatedImage(outImage, errorMessage);
  }

  @NotNull
  private static Layers generateIconLayers(@NotNull GraphicGeneratorContext context, @NotNull TvBannerOptions options, boolean forPreview) {
    AnnotatedImage backgroundImage = generateIconBackgroundLayer(context, options, forPreview);
    AnnotatedImage foregroundImage = generateIconForegroundLayer(options, forPreview);

    return new Layers(backgroundImage, foregroundImage);
  }

  @NotNull
  private static AnnotatedImage generateIconBackgroundLayer(@NotNull GraphicGeneratorContext context,
                                                            @NotNull TvBannerOptions options,
                                                            boolean forPreview) {
    if (options.usePlaceholders) {
      return PLACEHOLDER_IMAGE;
    }

    BufferedImage image;
    String errorMessage = null;
    Rectangle imageRect = IMAGE_SIZE_ADAPTIVE_DP;
    if (forPreview) {
      Density density = options.previewShape == PreviewShape.LEGACY ? LEGACY_DENSITY : Density.XXXHIGH;
      // For preview, scale image down so that it does not appear too big.
      double scale = getMdpiScaleFactor(density) * PREVIEW_SCALE;
      imageRect = scaleRectangleAroundCenter(imageRect, scale);
    }
    TransformedImageAsset imageAsset = options.backgroundImage;
    if (imageAsset == null) {
      image = generateFlatColorRectangle(new Color(options.backgroundColor), imageRect);
    }
    else {
      try {
        image = generateIconBackgroundLayer(context, imageAsset, imageRect);
      }
      catch (RuntimeException e) {
        errorMessage = composeErrorMessage(e, "background", imageAsset);
        image = imageAsset.createErrorImage(imageRect.getSize());
      }
    }

    return new AnnotatedImage(image, errorMessage);
  }

  @NotNull
  private static String composeErrorMessage(@NotNull Exception e, @NotNull String role, @Nullable TransformedImageAsset imageAsset) {
    String errorMessage = imageAsset != null && imageAsset.isDrawable() ?
               String.format("Unable to generate image, possibly invalid %s drawable", role) :
               String.format("Failed to transform %s image", role);
    String exceptionMessage = e.getMessage();
    return exceptionMessage == null ? errorMessage : errorMessage + ": " + exceptionMessage;
  }

  @NotNull
  private static AnnotatedImage generateIconForegroundLayer(@NotNull TvBannerOptions options, boolean forPreview) {
    if (options.usePlaceholders) {
      return PLACEHOLDER_IMAGE;
    }

    BufferedImage image;
    String errorMessage = null;
    Rectangle imageRect = IMAGE_SIZE_ADAPTIVE_DP;
    if (forPreview) {
      Density density = options.previewShape == PreviewShape.LEGACY ? LEGACY_DENSITY : Density.XXXHIGH;
      // For preview, scale image down so that it does not appear too big.
      double scale = getMdpiScaleFactor(density) * PREVIEW_SCALE;
      imageRect = scaleRectangleAroundCenter(imageRect, scale);
    }

    TransformedImageAsset foregroundImage = options.foregroundImage;
    TransformedImageAsset foregroundText = options.foregroundText;
    try {
      image = generateIconForegroundLayer(imageRect, foregroundImage, foregroundText);
    }
    catch (RuntimeException e) {
      errorMessage = composeErrorMessage(e, "foreground", foregroundImage);
      if (foregroundImage == null) {
        image = AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
      }
      else {
        image = foregroundImage.createErrorImage(imageRect.getSize());
      }
    }

    return new AnnotatedImage(image, errorMessage);
  }

  @NotNull
  private static BufferedImage generateFlatColorRectangle(@NotNull Color color, @NotNull Rectangle imageRect) {
    BufferedImage result = AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
    Graphics2D gTemp = (Graphics2D) result.getGraphics();
    gTemp.setPaint(color);
    gTemp.fillRect(0, 0, imageRect.width, imageRect.height);
    gTemp.dispose();
    return result;
  }

  @NotNull
  private static BufferedImage generateIconForegroundLayer(@NotNull Rectangle imageRect,
                                                           @Nullable TransformedImageAsset image,
                                                           @Nullable TransformedImageAsset text) {
    BufferedImage foregroundImage = image == null ? null : image.getTransformedImage(imageRect.getSize());
    if (text == null) {
      if (foregroundImage != null) {
        return foregroundImage;
      }
    }
    else {
      BufferedImage textImage = text.getTransformedImage(imageRect.getSize());
      if (foregroundImage != null) {
        return mergeImages(foregroundImage, textImage);
      }
      else {
        return textImage;
      }
    }

    return AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
  }

  @NotNull
  private static BufferedImage generateIconBackgroundLayer(@NotNull GraphicGeneratorContext context,
                                                           @NotNull TransformedImageAsset sourceImage,
                                                           @NotNull Rectangle imageRect) {
    String scaledDrawable = sourceImage.getTransformedDrawable();
    if (scaledDrawable != null) {
      return generateIconLayer(context, scaledDrawable, imageRect);
    }

    BufferedImage trimmedImage = sourceImage.getTrimmedImage();
    if (trimmedImage != null) {
      return generateIconBackgroundLayer(context, trimmedImage, imageRect, sourceImage.getScaleFactor());
    }

    return AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
  }

  @NotNull
  private static BufferedImage generateIconLayer(@NotNull GraphicGeneratorContext context, @NotNull String xmlDrawable,
                                                 @NotNull Rectangle imageRect) {
    Future<BufferedImage> imageFuture = context.renderDrawable(xmlDrawable, imageRect.getSize());
    try {
      BufferedImage image = imageFuture.get();
      if (image != null) {
        return image;
      }
    }
    catch (ExecutionException e) {
      ExceptionUtil.rethrow(e.getCause());
    }
    catch (InterruptedException ignore) {
    }

    return AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
  }

  @NotNull
  private static BufferedImage generateIconBackgroundLayer(@NotNull GraphicGeneratorContext context,
                                                           @NotNull BufferedImage sourceImage,
                                                           @NotNull Rectangle imageRect,
                                                           double scaleFactor) {
    Callable<Future<BufferedImage>> generator = () -> FutureUtils.executeOnPooledThread(() -> {
      // Scale the image.
      BufferedImage iconImage = AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
      Graphics2D gIcon = (Graphics2D)iconImage.getGraphics();
      Rectangle rect = scaleRectangleAroundCenter(imageRect, scaleFactor);
      AssetUtil.drawCenterInside(gIcon, sourceImage, rect);
      gIcon.dispose();

      return iconImage;
    });

    class CacheKey {
      @NotNull private final Object mySource;
      @NotNull private final Rectangle myImageRect;
      private final int myScaleFactorTimes1000;
      private final boolean myUseFillColor;
      private final int myFillColor;

      CacheKey(@NotNull Object source, @NotNull Rectangle imageRect, double scaleFactor, boolean useFillColor, int fillColor) {
        mySource = source;
        myImageRect = imageRect;
        myScaleFactorTimes1000 = roundToInt(scaleFactor * 1000);
        myUseFillColor = useFillColor;
        myFillColor = fillColor;
      }

      @Override
      public int hashCode() {
        return Objects.hash(mySource, myImageRect, myScaleFactorTimes1000, myUseFillColor, myFillColor);
      }

      @Override
      public boolean equals(Object obj) {
        if (!(obj instanceof CacheKey)) {
          return false;
        }
        CacheKey other = (CacheKey) obj;
        return Objects.equals(mySource, other.mySource)
               && Objects.equals(myImageRect, other.myImageRect)
               && myScaleFactorTimes1000 == other.myScaleFactorTimes1000
               && myUseFillColor == other.myUseFillColor
               && myFillColor == other.myFillColor;
      }
    }

    CacheKey cacheKey = new CacheKey(sourceImage, imageRect, scaleFactor, false, 0);
    Future<BufferedImage> imageFuture = context.getFromCacheOrCreate(cacheKey, generator);
    return Futures.getUnchecked(imageFuture);
  }

  @Override
  protected boolean includeDensity(@NotNull Density density) {
    // TV banner is available only in xhdpi.
    return density == Density.XHIGH;
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(TvBannerGenerator.class);
  }

  /** Options specific to generating TV banner icons. */
  public static class TvBannerOptions extends IconOptions implements Cloneable {
    /** The foreground layer name, used to generate resource paths. */
    public String foregroundLayerName;

    /** The background layer name, used to generate resource paths. */
    public String backgroundLayerName;

    /** The contents of the drawable file and scaling parameters. */
    @Nullable public TransformedImageAsset foregroundImage;

    /** Foreground text and scaling parameters. */
    @Nullable public TransformedImageAsset foregroundText;

    /** Color of the text, as an RRGGBB packed integer. */
    public int foregroundTextColor = 0;

    /**
     * Background color, as an RRGGBB packed integer. The background color is used only if
     * {@link #backgroundImage} is null.
     */
    public int backgroundColor = 0;

    /** If background is a drawable, the contents of the drawable file and scaling parameters. */
    @Nullable public TransformedImageAsset backgroundImage;

    /** Whether to generate the "Legacy" icon (API < 25). */
    public boolean generateLegacyIcon = true;

    /** If set, generate a preview image. */
    public PreviewShape previewShape = PreviewShape.NONE;

    public TvBannerOptions(boolean forPreview) {
      super(forPreview);
      iconFolderKind = IconFolderKind.MIPMAP;
    }

    @Override
    @NotNull
    public TvBannerOptions clone() {
      return (TvBannerOptions)super.clone();
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

  private static class Layers {
    @NotNull public AnnotatedImage background;
    @NotNull public AnnotatedImage foreground;

    Layers(@NotNull AnnotatedImage background, @NotNull AnnotatedImage foreground) {
      this.background = background;
      this.foreground = foreground;
    }
  }
}
