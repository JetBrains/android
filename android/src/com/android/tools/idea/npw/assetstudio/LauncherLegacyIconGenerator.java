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

import static com.android.tools.idea.npw.assetstudio.AssetStudioUtils.scaleRectangle;

import com.android.ide.common.util.AssetUtil;
import com.android.ide.common.util.PathString;
import com.android.resources.Density;
import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObjectProperty;
import com.android.tools.idea.observable.core.ObjectValueProperty;
import com.android.utils.Pair;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.project.Project;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Generator of legacy Android launcher icons.
 *
 * Defaults from https://romannurik.github.io/AndroidAssetStudio/icons-launcher.html
 */
@SuppressWarnings("UseJBColor") // We are generating colors in our icons, no need for JBColor here.
public class LauncherLegacyIconGenerator extends IconGenerator {
  public static final Color DEFAULT_BACKGROUND_COLOR = Color.WHITE;
  public static final Shape DEFAULT_ICON_SHAPE = Shape.SQUARE;

  private static final Rectangle IMAGE_SIZE_MDPI = new Rectangle(0, 0, 48, 48);

  private static final Map<Pair<Shape, Density>, Rectangle> TARGET_RECTS = buildTargetRectangles();

  private final ObjectProperty<Color> myBackgroundColor = new ObjectValueProperty<>(DEFAULT_BACKGROUND_COLOR);
  private final ObjectProperty<Shape> myShape = new ObjectValueProperty<>(DEFAULT_ICON_SHAPE);
  private final BoolProperty myCropped = new BoolValueProperty();
  private final BoolProperty myDogEared = new BoolValueProperty();

  /**
   * Initializes the icon generator. Every icon generator has to be disposed by calling {@link #dispose()}.
   *
   * @param project the Android project
   * @param minSdkVersion the minimal supported Android SDK version
   */
  public LauncherLegacyIconGenerator(@NotNull Project project, int minSdkVersion, @Nullable DrawableRenderer renderer) {
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
   * If {@code true}, any extra part of the source asset that doesn't fit on the final icon will
   * be cropped. Otherwise, the source asset will be shrunk to fit.
   */
  @NotNull
  public BoolProperty cropped() {
    return myCropped;
  }

  /**
   * A shape which will be used as the icon's backdrop.
   */
  @NotNull
  public ObjectProperty<Shape> shape() {
    return myShape;
  }

  /**
   * If true and the backdrop shape supports it, add a fold to the top-right corner of the backdrop shape.
   */
  @NotNull
  public BoolProperty dogEared() {
    return myDogEared;
  }

  @Override
  @NotNull
  public LauncherLegacyOptions createOptions(boolean forPreview) {
    LauncherLegacyOptions options = new LauncherLegacyOptions(forPreview);
    BaseAsset asset = sourceAsset().getValueOrNull();
    if (asset != null) {
      double paddingFactor = asset.paddingPercent().get() / 100.;
      double scaleFactor = 1. / (1 + paddingFactor * 2);
      options.useForegroundColor = asset.isColorable();
      Color color = asset.isColorable() ? asset.color().getValueOrNull() : null;
      if (color != null) {
        options.foregroundColor = color.getRGB();
      }
      options.image = new TransformedImageAsset(asset, IMAGE_SIZE_MDPI.getSize(), scaleFactor, color, getGraphicGeneratorContext());
    }

    options.shape = myShape.get();
    options.crop = myCropped.get();
    options.style = Style.SIMPLE;
    options.backgroundColor = myBackgroundColor.get().getRGB();
    options.isDogEar = myDogEared.get();

    return options;
  }

  /**
   * Modifies the value of the option to take into account the dog-ear effect. This effect only
   * applies to Square, Hrect and Vrect shapes.
   *
   * @param shape shape of the icon before applying dog-ear effect
   * @return shape with dog-ear effect on
   */
  private static Shape applyDog(Shape shape) {
    if (shape == Shape.SQUARE) {
      return Shape.SQUARE_DOG;
    } else if (shape == Shape.HRECT) {
      return Shape.HRECT_DOG;
    } else if (shape == Shape.VRECT) {
      return Shape.VRECT_DOG;
    } else {
      return shape;
    }
  }

  /**
   * Returns the {@link Rectangle} (in pixels) where the foreground image of a legacy icon should
   * be rendered. The {@link Rectangle} value depends on the {@link Shape} of the background, as
   * different shapes have different sizes.
   */
  @NotNull
  public static Rectangle getTargetRect(@Nullable Shape shape, @NotNull Density density) {
    Rectangle targetRect = TARGET_RECTS.get(Pair.of(shape, density));
    if (targetRect == null) {
      // Scale up from MDPI if no density-specific target rectangle is defined.
      targetRect = scaleRectangle(TARGET_RECTS.get(Pair.of(shape, Density.MEDIUM)), getMdpiScaleFactor(density));
    }
    return targetRect;
  }

  @Override
  @NotNull
  public AnnotatedImage generateRasterImage(@NotNull GraphicGeneratorContext context, @NotNull Options options) {
    if (options.usePlaceholders) {
      return PLACEHOLDER_IMAGE;
    }

    LauncherLegacyOptions launcherOptions = (LauncherLegacyOptions)options;
    Rectangle imageRect = scaleRectangle(IMAGE_SIZE_MDPI, getMdpiScaleFactor(launcherOptions.density));

    if (launcherOptions.isDogEar) {
      launcherOptions.shape = applyDog(launcherOptions.shape);
    }

    BufferedImage shapeImageBack = null;
    BufferedImage shapeImageFore = null;
    BufferedImage shapeImageMask = null;
    if (launcherOptions.shape != Shape.NONE && launcherOptions.shape != null && launcherOptions.renderShape) {
      shapeImageBack = loadBackImage(context, launcherOptions.shape, launcherOptions.density);
      shapeImageFore = loadStyleImage(context, launcherOptions.shape, launcherOptions.density, launcherOptions.style);
      shapeImageMask = loadMaskImage(context, launcherOptions.shape, launcherOptions.density);
    }

    Rectangle targetRect = getTargetRect(launcherOptions.shape, launcherOptions.density);

    // Create an intermediate image filled with the background color.
    BufferedImage tempImage = AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
    Graphics2D gTemp = (Graphics2D) tempImage.getGraphics();
    gTemp.setPaint(new Color(launcherOptions.backgroundColor));
    gTemp.fillRect(0, 0, imageRect.width, imageRect.height);

    AnnotatedImage sourceImage = generateRasterImage(targetRect.getSize(), options);

    // Render the foreground icon onto an intermediate image. This lets us override the color of the icon.
    BufferedImage iconImage = AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
    Graphics2D gIcon = (Graphics2D) iconImage.getGraphics();
    if (launcherOptions.crop) {
      AssetUtil.drawCenterCrop(gIcon, sourceImage.getImage(), targetRect);
    } else {
      AssetUtil.drawCenterInside(gIcon, sourceImage.getImage(), targetRect);
    }

    // Apply the foreground color if requested.
    AssetUtil.Effect[] effects;
    if (launcherOptions.useForegroundColor) {
      effects = new AssetUtil.Effect[] { new AssetUtil.FillEffect(new Color(launcherOptions.foregroundColor), 1.0) };
    } else {
      effects = AssetUtil.NO_EFFECTS;
    }
    AssetUtil.drawEffects(gTemp, iconImage, 0, 0, effects);

    if (shapeImageMask != null) {
      // Apply the shape mask to an intermediate image.
      gTemp.setComposite(AlphaComposite.DstIn);
      gTemp.drawImage(shapeImageMask, 0, 0, null);
    }

    // Create the final image..
    BufferedImage outImage = AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
    Graphics2D gOut = (Graphics2D) outImage.getGraphics();
    if (shapeImageBack != null) {
      // Render the background shadow to the output image.
      gOut.drawImage(shapeImageBack, 0, 0, null);
    }

    // Render the previously combined foreground layer and background color.
    gOut.drawImage(tempImage, 0, 0, null);

    if (shapeImageFore != null) {
      // Apply the foreground shape effects, e.g. dogear (folded top right corner).
      gOut.drawImage(shapeImageFore, 0, 0, null);
    }

    gOut.dispose();
    gTemp.dispose();
    gIcon.dispose();

    return new AnnotatedImage(outImage, sourceImage.getErrorMessage());
  }

  @Override
  protected boolean includeDensity(@NotNull Density density) {
    // Launcher icons should include xxxhdpi as well.
    return super.includeDensity(density) || density == Density.XXXHIGH;
  }

  @Override
  @NotNull
  public Collection<GeneratedIcon> generateIcons(@NotNull GraphicGeneratorContext context, @NotNull Options options, @NotNull String name) {
    Map<String, Map<String, AnnotatedImage>> categoryMap = new HashMap<>();
    generateRasterImage(null, categoryMap, context, options, name);

    // Category map is a map from category name to a map from relative path to image.
    List<GeneratedIcon> icons = new ArrayList<>();
    categoryMap.forEach(
      (category, images) ->
        images.forEach(
          (path, image) -> {
            IconCategory iconCategory = IconCategory.REGULAR;
            Density density = pathToDensity(path);
            // Could be a Play Store image
            if (density == null) {
              density = Density.NODPI;
              iconCategory = IconCategory.PLAY_STORE;
            }
            GeneratedImageIcon icon = new GeneratedImageIcon(path, new PathString(path), iconCategory, density, image);
            icons.add(icon);
          }));
    return icons;
  }

  private static Map<Pair<Shape, Density>, Rectangle> buildTargetRectangles() {
    ImmutableMap.Builder<Pair<Shape, Density>, Rectangle> targetRects = new ImmutableMap.Builder<>();
    // None, HDPI
    targetRects.put(Pair.of(Shape.NONE, Density.HIGH), new Rectangle(4, 4, 64, 64));
    // None, MDPI
    targetRects.put(Pair.of(Shape.NONE, Density.MEDIUM), new Rectangle(3, 3, 42, 42));

    // Circle, HDPI
    targetRects.put(Pair.of(Shape.CIRCLE, Density.HIGH), new Rectangle(3, 3, 66, 66));
    // Circle, MDPI
    targetRects.put(Pair.of(Shape.CIRCLE, Density.MEDIUM), new Rectangle(2, 2, 44, 44));

    // Square, HDPI
    targetRects.put(Pair.of(Shape.SQUARE, Density.HIGH), new Rectangle(7, 7, 57, 57));
    // Square, MDPI
    targetRects.put(Pair.of(Shape.SQUARE, Density.MEDIUM), new Rectangle(5, 5, 38, 38));

    // Vertical Rectangle, HDPI
    targetRects.put(Pair.of(Shape.VRECT, Density.HIGH), new Rectangle(12, 3, 48, 66));
    // Vertical Rectangle, MDPI
    targetRects.put(Pair.of(Shape.VRECT, Density.MEDIUM), new Rectangle(8, 2, 32, 44));

    // Horizontal Rectangle, HDPI
    targetRects.put(Pair.of(Shape.HRECT, Density.HIGH), new Rectangle(3, 12, 66, 48));
    // Horizontal Rectangle, MDPI
    targetRects.put(Pair.of(Shape.HRECT, Density.MEDIUM), new Rectangle(2, 8, 44, 32));

    // Square Dog-ear, HDPI
    targetRects.put(Pair.of(Shape.SQUARE_DOG, Density.HIGH), new Rectangle(7, 21, 57, 43));
    // Square Dog-ear, MDPI
    targetRects.put(Pair.of(Shape.SQUARE_DOG, Density.MEDIUM), new Rectangle(5, 14, 38, 29));

    // Vertical Rectangle Dog-ear, HDPI
    targetRects.put(Pair.of(Shape.VRECT_DOG, Density.HIGH), new Rectangle(12, 17, 48, 52));
    // Vertical Rectangle Dog-ear, MDPI
    targetRects.put(Pair.of(Shape.VRECT_DOG, Density.MEDIUM), new Rectangle(8, 11, 32, 35));

    // Horizontal Rectangle Dog-ear, HDPI
    targetRects.put(Pair.of(Shape.HRECT_DOG, Density.HIGH), new Rectangle(3, 12, 52, 48));
    // Horizontal Rectangle Dog-ear, MDPI
    targetRects.put(Pair.of(Shape.HRECT_DOG, Density.MEDIUM), new Rectangle(2, 8, 35, 32));
    return targetRects.build();
  }

  /** Options specific to generating launcher icons */
  public static class LauncherLegacyOptions extends Options {
    /**
     * Whether to use the foreground color. If we are using images as the source asset for our icons,
     * you shouldn't apply the foreground color, which would paint over it and obscure the image.
     */
    public boolean useForegroundColor = true;

    /** Foreground color, as an RRGGBB packed integer. */
    public int foregroundColor;

    /** Background color, as an RRGGBB packed integer. */
    public int backgroundColor;

    /** Whether the image should be cropped or not. */
    public boolean crop = true;

    /** The shape to use for the background. */
    public Shape shape = Shape.SQUARE;

    /** The effects to apply to the foreground. */
    public Style style = Style.SIMPLE;

    /** Whether or not to use the dog-ear effect. */
    public boolean isDogEar;

    /**
     * Whether to render the background shape. If false, the shape is still used to
     * scale the image to the target rectangle associated to the shape.
     */
    public boolean renderShape = true;

    public LauncherLegacyOptions(boolean forPreview) {
      super(forPreview);
      iconFolderKind = IconFolderKind.MIPMAP;
    }
  }
}
