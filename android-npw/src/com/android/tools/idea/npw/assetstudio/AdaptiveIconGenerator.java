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
import static java.lang.Math.max;

import com.android.ide.common.util.AssetUtil;
import com.android.resources.Density;
import com.android.tools.idea.concurrency.FutureUtils;
import com.android.tools.idea.npw.assetstudio.assets.ImageAsset;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObjectProperty;
import com.android.tools.idea.observable.core.ObjectValueProperty;
import com.android.tools.idea.observable.core.OptionalProperty;
import com.android.tools.idea.observable.core.OptionalValueProperty;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.core.StringValueProperty;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.project.Project;
import com.intellij.util.ExceptionUtil;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Generator of adaptive icons.
 */
@SuppressWarnings("UseJBColor") // Android icons don't need JBColor.
public abstract class AdaptiveIconGenerator extends IconGenerator {
  public static final Color DEFAULT_FOREGROUND_COLOR = Color.BLACK;
  public static final Color DEFAULT_BACKGROUND_COLOR = new Color(0x3DDC84);

  private final BoolProperty myShowSafeZone = new BoolValueProperty(true);
  private final ObjectProperty<Color> myBackgroundColor = new ObjectValueProperty<>(DEFAULT_BACKGROUND_COLOR);
  private final OptionalProperty<ImageAsset> myBackgroundImageAsset = new OptionalValueProperty<>();
  private final StringProperty myForegroundLayerName = new StringValueProperty();
  private final StringProperty myBackgroundLayerName = new StringValueProperty();
  private final BoolProperty myGenerateLegacyIcon = new BoolValueProperty(true);

  /**
   * Initializes the icon generator. Every icon generator has to be disposed by calling {@link #dispose()}.
   *
   * @param project the Android project
   * @param minSdkVersion the minimal supported Android SDK version
   * @param context the content used to render vector drawables
   */
  public AdaptiveIconGenerator(@NotNull Project project, int minSdkVersion, @NotNull GraphicGeneratorContext context) {
    super(project, minSdkVersion, context);
  }

  /**
   * A color for rendering the background shape.
   */
  @NotNull
  public ObjectProperty<Color> backgroundColor() {
    return myBackgroundColor;
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

  /**
   * If {@code true}, generate the "Legacy" icon (API 25 and earlier)
   */
  @NotNull
  public BoolProperty generateLegacyIcon() {
    return myGenerateLegacyIcon;
  }

  @NotNull
  protected String getAdaptiveIconXml(@NotNull AdaptiveIconOptions options) {
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

  /**
   * Returns the scaling factor to apply to the {@code source} rectangle so that its width or
   * height is equal to the width or height of {@code destination} rectangle, while remaining
   * contained within {@code destination}.
   */
  public static double getRectangleInsideScale(@NotNull Rectangle source, @NotNull Rectangle destination) {
    double scaleWidth = destination.getWidth() / source.getWidth();
    double scaleHeight = destination.getHeight() / source.getHeight();
    return Math.min(scaleWidth, scaleHeight);
  }

  /** Scale an image given a scale factor. */
  @NotNull
  protected static BufferedImage scaledImage(@NotNull BufferedImage image, double scale) {
    int width = roundToInt(image.getWidth() * scale);
    int height = roundToInt(image.getHeight() * scale);
    return AssetUtil.scaledImage(image, width, height);
  }

  /**
   * For performance reason, we use a lower quality (but faster) image scaling algorithm when
   * generating preview images.
   */
  @NotNull
  protected static BufferedImage scaledPreviewImage(@NotNull BufferedImage image, double scale) {
    int width = roundToInt(image.getWidth() * scale);
    int height = roundToInt(image.getHeight() * scale);
    return scaledPreviewImage(image, width, height);
  }

  /**
   * For performance reason, we use a lower quality (but faster) image scaling algorithm when
   * generating preview images.
   */
  @NotNull
  private static BufferedImage scaledPreviewImage(@NotNull BufferedImage source, int width, int height) {
    // Common case optimization: scaling to the same (width, height) is a no-op.
    if (source.getWidth() == width && source.getHeight() == height) {
      return source;
    }

    BufferedImage scaledBufImage = AssetUtil.newArgbBufferedImage(width, height);
    Graphics2D g = scaledBufImage.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.drawImage(source, 0, 0, width, height, null);
    g.dispose();
    return scaledBufImage;
  }

  @NotNull
  protected static AnnotatedImage mergeLayers(@NotNull Layers layers, @Nullable Color fillColor) {
    BufferedImage backgroundImage = layers.background.getImage();
    BufferedImage foregroundImage = layers.foreground.getImage();
    int width = max(backgroundImage.getWidth(), foregroundImage.getWidth());
    int height = max(backgroundImage.getHeight(), foregroundImage.getHeight());

    BufferedImage outImage = AssetUtil.newArgbBufferedImage(width, height);
    Graphics2D gOut = (Graphics2D) outImage.getGraphics();
    if (fillColor != null) {
      gOut.setPaint(fillColor);
      gOut.fillRect(0, 0, width, height);
    }
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
  private Layers generateIconLayers(@NotNull GraphicGeneratorContext context, @NotNull AdaptiveIconOptions options) {
    AnnotatedImage backgroundImage = generateIconBackgroundLayer(context, options);
    AnnotatedImage foregroundImage = generateIconForegroundLayer(context, options);

    return new Layers(backgroundImage, foregroundImage);
  }

  @NotNull
  protected AnnotatedImage generateMergedLayers(
      @NotNull GraphicGeneratorContext context, @NotNull AdaptiveIconOptions options, @Nullable Color fillColor) {
    Layers layers = generateIconLayers(context, options);
    return mergeLayers(layers, fillColor);
  }

  @NotNull
  public BoolProperty showSafeZone() {
    return myShowSafeZone;
  }

  @NotNull
  protected abstract Rectangle getFullBleedRectangle(@NotNull AdaptiveIconOptions options);

  @NotNull
  protected abstract Rectangle getViewportRectangle(@NotNull AdaptiveIconOptions options);

  @NotNull
  protected abstract Rectangle getLegacyRectangle(@NotNull AdaptiveIconOptions options);

  @NotNull
  protected AnnotatedImage generateIconBackgroundLayer(@NotNull GraphicGeneratorContext context, @NotNull AdaptiveIconOptions options) {
    if (options.usePlaceholders) {
      return PLACEHOLDER_IMAGE;
    }

    BufferedImage image;
    String errorMessage = null;
    Rectangle imageRect = getFullBleedRectangle(options);
    TransformedImageAsset imageAsset = options.backgroundImage;
    if (imageAsset == null) {
      image = generateFlatColorRectangle(new Color(options.backgroundColor), imageRect);
    }
    else {
      try {
        image = generateIconLayer(context, imageAsset, imageRect, false, 0, !options.generateOutputIcons);
      }
      catch (RuntimeException e) {
        errorMessage = composeErrorMessage(e, "background", imageAsset);
        image = imageAsset.createErrorImage(imageRect.getSize());
      }
    }

    return new AnnotatedImage(image, errorMessage);
  }

  @NotNull
  protected AnnotatedImage generateIconForegroundLayer(@NotNull GraphicGeneratorContext context, @NotNull AdaptiveIconOptions options) {
    if (options.usePlaceholders) {
      return PLACEHOLDER_IMAGE;
    }

    BufferedImage image;
    String errorMessage = null;
    Rectangle imageRect = getFullBleedRectangle(options);
    TransformedImageAsset imageAsset = options.foregroundImage;
    if (imageAsset == null) {
      image = AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
    }
    else {
      try {
        image = generateIconLayer(context, imageAsset, imageRect, options.useForegroundColor, options.foregroundColor,
                                  !options.generateOutputIcons);
      }
      catch (RuntimeException e) {
        errorMessage = composeErrorMessage(e, "foreground", imageAsset);
        image = imageAsset.createErrorImage(imageRect.getSize());
      }
    }

    return new AnnotatedImage(image, errorMessage);
  }

  @NotNull
  private static String composeErrorMessage(@NotNull Exception e, @NotNull String role, @NotNull TransformedImageAsset imageAsset) {
    String errorMessage = imageAsset.isDrawable() ?
                          String.format("Unable to generate image, possibly invalid %s drawable", role) :
                          String.format("Failed to transform %s image", role);
    String exceptionMessage = e.getMessage();
    return exceptionMessage == null ? errorMessage : errorMessage + ": " + exceptionMessage;
  }

  protected BufferedImage cropImageToViewport(@NotNull AdaptiveIconOptions options, @NotNull BufferedImage image) {
    return cropImage(image, getViewportRectangle(options));
  }

  private static BufferedImage cropImage(@NotNull BufferedImage image, @NotNull Rectangle targetRect) {
    int width = Math.min(targetRect.width, image.getWidth());
    int height = Math.min(targetRect.height, image.getHeight());
    BufferedImage subImage = image.getSubimage((image.getWidth() - width) / 2, (image.getHeight() - height) / 2, width, height);

    BufferedImage viewportImage = AssetUtil.newArgbBufferedImage(width, height);

    Graphics2D gViewport = (Graphics2D)viewportImage.getGraphics();
    gViewport.drawImage(subImage, 0, 0, null);
    gViewport.dispose();

    return viewportImage;
  }

  @NotNull
  private static BufferedImage generateFlatColorRectangle(@NotNull Color color, @NotNull Rectangle imageRect) {
    BufferedImage result = AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
    Graphics2D gTemp = (Graphics2D)result.getGraphics();
    gTemp.setPaint(color);
    gTemp.fillRect(0, 0, imageRect.width, imageRect.height);
    gTemp.dispose();
    return result;
  }

  @NotNull
  protected static BufferedImage applyMask(@NotNull BufferedImage image, @Nullable BufferedImage mask) {
    if (mask == null) {
      return image;
    }

    Rectangle imageRect = new Rectangle(0, 0, image.getWidth(), image.getHeight());
    BufferedImage tempImage = AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);

    Graphics2D gTemp = (Graphics2D)tempImage.getGraphics();
    AssetUtil.drawCentered(gTemp, mask, imageRect);
    gTemp.setComposite(AlphaComposite.SrcIn);
    AssetUtil.drawCentered(gTemp, image, imageRect);
    gTemp.dispose();

    return tempImage;
  }

  @NotNull
  private BufferedImage generateIconLayer(@NotNull GraphicGeneratorContext context, @NotNull TransformedImageAsset sourceImage,
                                          @NotNull Rectangle imageRect, boolean useFillColor, int fillColor, boolean forPreview) {
    String scaledDrawable = sourceImage.getTransformedDrawable();
    if (scaledDrawable != null) {
      return generateIconLayer(context, scaledDrawable, imageRect);
    }

    BufferedImage trimmedImage = sourceImage.getTrimmedImage();
    if (trimmedImage != null) {
      return generateIconLayer(context, trimmedImage, imageRect, sourceImage.getScaleFactor(), useFillColor, fillColor, forPreview);
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
  private BufferedImage generateIconLayer(@NotNull GraphicGeneratorContext context, @NotNull BufferedImage sourceImage,
                                          @NotNull Rectangle imageRect, double scaleFactor, boolean useFillColor, int fillColor,
                                          boolean forPreview) {
    if (forPreview && max(sourceImage.getWidth(), sourceImage.getHeight()) > getMaxIconRectangle().getWidth() * 1.5) {
      // The source image is pretty large. Scale it down in preview mode to make generation of subsequent images faster.
      sourceImage = generateIconLayer(context, sourceImage, getMaxIconRectangle(), 1, false, 0);
    }

    return generateIconLayer(context, sourceImage, imageRect, scaleFactor, useFillColor, fillColor);
  }

  protected abstract Rectangle getMaxIconRectangle();

  @NotNull
  private static BufferedImage generateIconLayer(@NotNull GraphicGeneratorContext context, @NotNull BufferedImage sourceImage,
                                                 @NotNull Rectangle imageRect, double scaleFactor, boolean useFillColor, int fillColor) {
    Callable<Future<BufferedImage>> generator = () -> FutureUtils.executeOnPooledThread(() -> {
      // Scale the image.
      BufferedImage iconImage = AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
      Graphics2D gIcon = (Graphics2D)iconImage.getGraphics();
      Rectangle rect = scaleRectangleAroundCenter(imageRect, scaleFactor);
      AssetUtil.drawCenterInside(gIcon, sourceImage, rect);
      gIcon.dispose();

      if (!useFillColor) {
        return iconImage;
      }
      // Fill with fillColor.
      BufferedImage effectImage = AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
      Graphics2D gEffect = (Graphics2D)effectImage.getGraphics();
      //noinspection UseJBColor
      AssetUtil.Effect[] effects = new AssetUtil.Effect[] { new AssetUtil.FillEffect(new Color(fillColor), 1) };
      AssetUtil.drawEffects(gEffect, iconImage, 0, 0, effects);
      gEffect.dispose();
      return effectImage;
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

    CacheKey cacheKey = new CacheKey(sourceImage, imageRect, scaleFactor, useFillColor, fillColor);
    Future<BufferedImage> imageFuture = context.getFromCacheOrCreate(cacheKey, generator);
    return Futures.getUnchecked(imageFuture);
  }

  /** Options specific to generating launcher icons. */
  public static class AdaptiveIconOptions extends IconOptions implements Cloneable {
    /** The foreground layer name, used to generate resource paths. */
    public String foregroundLayerName;

    /** The background layer name, used to generate resource paths. */
    public String backgroundLayerName;

    /**
     * Whether to use the foreground color. If we are using images as the source asset for our
     * icons, you shouldn't apply the foreground color, which would paint over it and obscure
     * the image.
     */
    public boolean useForegroundColor = true;

    /** Foreground color, as an RRGGBB packed integer */
    public int foregroundColor = 0;

    /** If foreground is a drawable, the contents of the drawable file and scaling parameters. */
    @Nullable public TransformedImageAsset foregroundImage;

    /**
     * Background color, as an RRGGBB packed integer. The background color is used only if
     * {@link #backgroundImage} is null.
     */
    public int backgroundColor = 0;

    /** If background is a drawable, the contents of the drawable file and scaling parameters. */
    @Nullable public TransformedImageAsset backgroundImage;

    /** Whether to generate the "Legacy" icon (API <= 25). */
    public boolean generateLegacyIcon = true;

    /** The density of the preview images. */
    public Density previewDensity;
    /** Whether to draw the safe zone circle. */
    public boolean showSafeZone;

    public AdaptiveIconOptions(boolean forPreview) {
      super(forPreview);
      iconFolderKind = IconFolderKind.MIPMAP;
    }

    @NotNull
    @Override
    public AdaptiveIconOptions clone() {
      return (AdaptiveIconOptions)super.clone();
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
