/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.util.AssetUtil;
import com.android.resources.Density;
import com.android.utils.Pair;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link GraphicGenerator} that generates Android "launcher" icons.
 */
public class LauncherIconGenerator extends GraphicGenerator {
    private static final Rectangle IMAGE_SIZE_WEB = new Rectangle(0, 0, 512, 512);
    private static final Rectangle IMAGE_SIZE_MDPI = new Rectangle(0, 0, 48, 48);

    private static final Map<Pair<Shape, Density>, Rectangle> TARGET_RECTS = new HashMap<>();

    static {
        // None, Web
        TARGET_RECTS.put(Pair.of(Shape.NONE, null), new Rectangle(32, 32, 448, 448));
        // None, HDPI
        TARGET_RECTS.put(Pair.of(Shape.NONE, Density.HIGH), new Rectangle(4, 4, 64, 64));
        // None, MDPI
        TARGET_RECTS.put(Pair.of(Shape.NONE, Density.MEDIUM), new Rectangle(3, 3, 42, 42));

        // Circle, Web
        TARGET_RECTS.put(Pair.of(Shape.CIRCLE, null), new Rectangle(21, 21, 470, 470));
        // Circle, HDPI
        TARGET_RECTS.put(Pair.of(Shape.CIRCLE, Density.HIGH), new Rectangle(3, 3, 66, 66));
        // Circle, MDPI
        TARGET_RECTS.put(Pair.of(Shape.CIRCLE, Density.MEDIUM), new Rectangle(2, 2, 44, 44));

        // Square, Web
        TARGET_RECTS.put(Pair.of(Shape.SQUARE, null), new Rectangle(53, 53, 406, 406));
        // Square, HDPI
        TARGET_RECTS.put(Pair.of(Shape.SQUARE, Density.HIGH), new Rectangle(7, 7, 57, 57));
        // Square, MDPI
        TARGET_RECTS.put(Pair.of(Shape.SQUARE, Density.MEDIUM), new Rectangle(5, 5, 38, 38));

        // Vertical Rectangle, Web
        TARGET_RECTS.put(Pair.of(Shape.VRECT, null), new Rectangle(85, 21, 342, 470));
        // Vertical Rectangle, HDPI
        TARGET_RECTS.put(Pair.of(Shape.VRECT, Density.HIGH), new Rectangle(12, 3, 48, 66));
        // Vertical Rectangle, MDPI
        TARGET_RECTS.put(Pair.of(Shape.VRECT, Density.MEDIUM), new Rectangle(8, 2, 32, 44));

        // Horizontal Rectangle, Web
        TARGET_RECTS.put(Pair.of(Shape.HRECT, null), new Rectangle(21, 85, 470, 342));
        // Horizontal Rectangle, HDPI
        TARGET_RECTS.put(Pair.of(Shape.HRECT, Density.HIGH), new Rectangle(3, 12, 66, 48));
        // Horizontal Rectangle, MDPI
        TARGET_RECTS.put(Pair.of(Shape.HRECT, Density.MEDIUM), new Rectangle(2, 8, 44, 32));

        // Square Dog-ear, Web
        TARGET_RECTS.put(Pair.of(Shape.SQUARE_DOG, null), new Rectangle(53, 149, 406, 312));
        // Square Dog-ear, HDPI
        TARGET_RECTS.put(Pair.of(Shape.SQUARE_DOG, Density.HIGH), new Rectangle(7, 21, 57, 43));
        // Square Dog-ear, MDPI
        TARGET_RECTS.put(Pair.of(Shape.SQUARE_DOG, Density.MEDIUM), new Rectangle(5, 14, 38, 29));

        // Vertical Rectangle Dog-ear, Web
        TARGET_RECTS.put(Pair.of(Shape.VRECT_DOG, null), new Rectangle(85, 117, 342, 374));
        // Vertical Rectangle Dog-ear, HDPI
        TARGET_RECTS.put(Pair.of(Shape.VRECT_DOG, Density.HIGH), new Rectangle(12, 17, 48, 52));
        // Vertical Rectangle Dog-ear, MDPI
        TARGET_RECTS.put(Pair.of(Shape.VRECT_DOG, Density.MEDIUM), new Rectangle(8, 11, 32, 35));

        // Horizontal Rectangle Dog-ear, Web
        TARGET_RECTS.put(Pair.of(Shape.HRECT_DOG, null), new Rectangle(21, 85, 374, 342));
        // Horizontal Rectangle Dog-ear, HDPI
        TARGET_RECTS.put(Pair.of(Shape.HRECT_DOG, Density.HIGH), new Rectangle(3, 12, 52, 48));
        // Horizontal Rectangle Dog-ear, MDPI
        TARGET_RECTS.put(Pair.of(Shape.HRECT_DOG, Density.MEDIUM), new Rectangle(2, 8, 35, 32));
    }

    /**
     * Modifies the value of the option to take into account the dog-ear effect. This effect only
     * applies to Square, Hrect and Vrect shapes.
     *
     * @param shape Shape of the icon before applying dog-ear effect
     * @return Shape with dog-ear effect on
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
     * Load a pref-defined image file given a {@link Shape}, {@link Density} and fileName.
     *
     * <p>Pass a <code>null</code> {@link Density} to get the {@link Rectangle} corresponding to the
     * "Web" image size.
     */
    @Nullable
    public static BufferedImage loadImage(
            @NonNull GraphicGeneratorContext context,
            @NonNull Shape shape,
            @Nullable Density density,
            @NonNull String fileName) {
        String densityValue = (density == null ? "web" : density.getResourceValue());
        String name =
                String.format(
                        "/images/launcher_stencil/%s/%s/%s.png", shape.id, densityValue, fileName);
        return context.loadImageResource(name);
    }

    /**
     * Load a pref-defined mask image file given a {@link Shape}, {@link Density} and fileName.
     *
     * <p>Pass a <code>null</code> {@link Density} to get the {@link Rectangle} corresponding to the
     * "Web" image size.
     */
    @Nullable
    public static BufferedImage loadMaskImage(
            @NonNull GraphicGeneratorContext context,
            @NonNull Shape shape,
            @Nullable Density density) {
        return loadImage(context, shape, density, "mask");
    }

    /**
     * Load a pref-defined background image file given a {@link Shape}, {@link Density} and
     * fileName.
     *
     * <p>Pass a <code>null</code> {@link Density} to get the {@link Rectangle} corresponding to the
     * "Web" image size.
     */
    @Nullable
    public static BufferedImage loadBackImage(
            @NonNull GraphicGeneratorContext context,
            @NonNull Shape shape,
            @Nullable Density density) {
        return loadImage(context, shape, density, "back");
    }

    /**
     * Load a pref-defined style image file given a {@link Shape}, {@link Density} and fileName.
     *
     * <p>Pass a <code>null</code> {@link Density} to get the {@link Rectangle} corresponding to the
     * "Web" image size.
     */
    @Nullable
    public static BufferedImage loadStyleImage(
            @NonNull GraphicGeneratorContext context,
            @NonNull Shape shape,
            @Nullable Density density,
            @NonNull Style style) {
        return loadImage(context, shape, density, style.id);
    }

    /**
     * Return the {@link Rectangle} (in pixels) where the foreground image of a legacy icon should
     * be rendered. The {@link Rectangle} value depends on the {@link Shape} of the background, as
     * different shapes have different sizes.
     *
     * <p>Pass a <code>null</code> {@link Density} to get the {@link Rectangle} corresponding to the
     * "Web" image size.
     */
    @NonNull
    public static Rectangle getTargetRect(@Nullable Shape shape, @Nullable Density density) {
        Rectangle targetRect = TARGET_RECTS.get(Pair.of(shape, density));
        if (targetRect == null) {
            // Scale up from MDPI if no density-specific target rectangle is defined.
            targetRect =
                    AssetUtil.scaleRectangle(
                            TARGET_RECTS.get(Pair.of(shape, Density.MEDIUM)),
                            GraphicGenerator.getMdpiScaleFactor(density));
        }
        return targetRect;
    }

    @NonNull
    @SuppressWarnings("UseJBColor")
    @Override
    public BufferedImage generate(@NonNull GraphicGeneratorContext context, @NonNull Options options) {
        if (options.usePlaceholders) {
            return PLACEHOLDER_IMAGE;
        }

        BufferedImage sourceImage = getTrimmedAndPaddedImage(options);
        if (sourceImage == null) {
            sourceImage = AssetStudioUtils.createDummyImage();
        }
        LauncherOptions launcherOptions = (LauncherOptions) options;

        if (launcherOptions.isDogEar) {
            launcherOptions.shape = applyDog(launcherOptions.shape);
        }

        BufferedImage shapeImageBack = null, shapeImageFore = null, shapeImageMask = null;
        if (launcherOptions.shape != Shape.NONE
                && launcherOptions.shape != null
                && launcherOptions.renderShape) {
            Density loadImageDensity =
                    (launcherOptions.isWebGraphic ? null : launcherOptions.density);
            shapeImageBack = loadBackImage(context, launcherOptions.shape, loadImageDensity);
            shapeImageFore =
                    loadStyleImage(
                            context,
                            launcherOptions.shape,
                            loadImageDensity,
                            launcherOptions.style);
            shapeImageMask = loadMaskImage(context, launcherOptions.shape, loadImageDensity);
        }

        Rectangle imageRect = IMAGE_SIZE_WEB;
        if (!launcherOptions.isWebGraphic) {
            imageRect =
                    AssetUtil.scaleRectangle(
                            IMAGE_SIZE_MDPI,
                            GraphicGenerator.getMdpiScaleFactor(launcherOptions.density));
        }

        Rectangle targetRect = getTargetRect(launcherOptions.shape, launcherOptions.density);

        // outImage will be our final image. Many intermediate textures will be rendered, in
        // layers, onto this image
        BufferedImage outImage = AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
        Graphics2D gOut = (Graphics2D) outImage.getGraphics();
        if (shapeImageBack != null) {
            gOut.drawImage(shapeImageBack, 0, 0, null);
        }

        // Render the background shape into an intermediate buffer. This lets us set a fill color.
        BufferedImage tempImage = AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
        Graphics2D gTemp = (Graphics2D) tempImage.getGraphics();
        if (shapeImageMask != null) {
            gTemp.drawImage(shapeImageMask, 0, 0, null);
            gTemp.setComposite(AlphaComposite.SrcAtop);
            gTemp.setPaint(new Color(launcherOptions.backgroundColor));
            gTemp.fillRect(0, 0, imageRect.width, imageRect.height);
        }

        // Render the foreground icon onto an intermediate buffer and then render over the
        // background shape. This lets us override the color of the icon.
        BufferedImage iconImage = AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
        Graphics2D gIcon = (Graphics2D) iconImage.getGraphics();
        if (launcherOptions.crop) {
            AssetUtil.drawCenterCrop(gIcon, sourceImage, targetRect);
        } else {
            AssetUtil.drawCenterInside(gIcon, sourceImage, targetRect);
        }
        AssetUtil.Effect[] effects;
        if (launcherOptions.useForegroundColor) {
            effects =
                    new AssetUtil.Effect[] {
                        new AssetUtil.FillEffect(new Color(launcherOptions.foregroundColor), 1.0)
                    };
        } else {
            effects = new AssetUtil.Effect[0];
        }
        AssetUtil.drawEffects(gTemp, iconImage, 0, 0, effects);

        // Finally, render all layers to the output image
        gOut.drawImage(tempImage, 0, 0, null);
        if (shapeImageFore != null) {
            // Useful for some shape effects, like dogear (e.g. folded top right corner)
            gOut.drawImage(shapeImageFore, 0, 0, null);
        }

        gOut.dispose();
        gTemp.dispose();
        gIcon.dispose();

        return outImage;
    }

    @Override
    protected boolean includeDensity(@NonNull Density density) {
        // Launcher icons should include xxxhdpi as well
        return super.includeDensity(density) || density == Density.XXXHIGH;
    }

    @Override
    public void generate(String category, Map<String, Map<String, BufferedImage>> categoryMap,
            GraphicGeneratorContext context, Options options, String name) {
        LauncherOptions launcherOptions = (LauncherOptions) options;
        boolean generateWebImage = launcherOptions.isWebGraphic;
        launcherOptions.isWebGraphic = false;
        super.generate(category, categoryMap, context, options, name);

        if (generateWebImage) {
            launcherOptions.isWebGraphic = true;
            launcherOptions.density = null;
            BufferedImage image = generate(context, options);
            Map<String, BufferedImage> imageMap = new HashMap<>();
            categoryMap.put("Web", imageMap);
            imageMap.put(getIconPath(options, name), image);
        }
    }

    @NonNull
    @Override
    protected String getIconPath(@NonNull Options options, @NonNull String name) {
        if (((LauncherOptions) options).isWebGraphic) {
            return name + "-web.png"; // Store at the root of the project
        }

        return super.getIconPath(options, name);
    }

    /** Options specific to generating launcher icons */
    public static class LauncherOptions extends GraphicGenerator.Options {
        public LauncherOptions() {
            iconFolderKind = IconFolderKind.MIPMAP;
        }

        /**
         * Whether to use the foreground color. If we are using images as the source asset for our icons,
         * you shouldn't apply the foreground color, which would paint over it and obscure the image.
         */
        public boolean useForegroundColor = true;

        /** Foreground color, as an RRGGBB packed integer */
        public int foregroundColor = 0;

        /** Background color, as an RRGGBB packed integer */
        public int backgroundColor = 0;

        /** Whether the image should be cropped or not */
        public boolean crop = true;

        /** The shape to use for the background */
        public Shape shape = Shape.SQUARE;

        /** The effects to apply to the foreground */
        public Style style = Style.SIMPLE;

        /** Whether or not to use the Dog-ear effect */
        public boolean isDogEar = false;

        /**
         * Whether to render the background shape. If <code>false</code>, the shape is still used to
         * scale the image to the target rectangle associated to the shape.
         */
        public boolean renderShape = true;

        /**
         * Whether a web graphic should be generated (will ignore normal density setting). The
         * {@link #generate(GraphicGeneratorContext, Options)} method will use this to decide
         * whether to generate a normal density icon or a high res web image. The {@link
         * GraphicGenerator#generate(String, Map, GraphicGeneratorContext, Options, String)} method
         * will use this flag to determine whether it should include a web graphic in its iteration.
         */
        public boolean isWebGraphic;
    }
}
