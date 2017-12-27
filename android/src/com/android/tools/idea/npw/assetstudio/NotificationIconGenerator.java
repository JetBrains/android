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
import com.android.ide.common.util.AssetUtil;
import com.android.ide.common.util.AssetUtil.Effect;
import com.android.ide.common.util.AssetUtil.FillEffect;
import com.android.ide.common.util.AssetUtil.ShadowEffect;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Map;

/**
 * Generate icons for the notifications bar
 */
public class NotificationIconGenerator extends GraphicGenerator {
    /** Creates a new {@link NotificationIconGenerator} */
    public NotificationIconGenerator() {
    }

    @NonNull
    @Override
    public BufferedImage generate(@NonNull GraphicGeneratorContext context, @NonNull Options options) {
        if (options.usePlaceholders) {
            return PLACEHOLDER_IMAGE;
        }

        BufferedImage sourceImage = getTrimmedAndPaddedImage(options);
        if (sourceImage == null) {
            sourceImage = AssetStudioUtils.createDummyImage();
        }
        Rectangle iconSizeMdpi;
        Rectangle targetRectMdpi;
        NotificationOptions notificationOptions = (NotificationOptions) options;
        if (notificationOptions.version == Version.OLDER) {
            iconSizeMdpi = new Rectangle(0, 0, 25, 25);
            targetRectMdpi = new Rectangle(4, 4, 17, 17);
        } else if (notificationOptions.version == Version.V11) {
            iconSizeMdpi = new Rectangle(0, 0, 24, 24);
            targetRectMdpi = new Rectangle(1, 1, 22, 22);
        } else {
            assert notificationOptions.version == Version.V9;
            iconSizeMdpi = new Rectangle(0, 0, 16, 25);
            targetRectMdpi = new Rectangle(0, 5, 16, 16);
        }

        final float scaleFactor = GraphicGenerator.getMdpiScaleFactor(options.density);
        Rectangle imageRect = AssetUtil.scaleRectangle(iconSizeMdpi, scaleFactor);
        Rectangle targetRect = AssetUtil.scaleRectangle(targetRectMdpi, scaleFactor);

        BufferedImage outImage = AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
        Graphics2D g = (Graphics2D) outImage.getGraphics();

        BufferedImage tempImage = AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
        Graphics2D g2 = (Graphics2D) tempImage.getGraphics();

        if (notificationOptions.version == Version.OLDER) {
            BufferedImage backImage = context.loadImageResource(
                    "/images/notification_stencil/"
                            + notificationOptions.density.getResourceValue()
                            + ".png");
            g.drawImage(backImage, 0, 0, null);
            BufferedImage top = sourceImage;
            BufferedImage filled = AssetUtil.filledImage(top, Color.WHITE);
            AssetUtil.drawCenterInside(g, filled, targetRect);
        } else if (notificationOptions.version == Version.V11) {
            AssetUtil.drawCenterInside(g2, sourceImage, targetRect);
            AssetUtil.drawEffects(g, tempImage, 0, 0, new Effect[]{new FillEffect(Color.WHITE),});
        } else {
            assert notificationOptions.version == Version.V9;
            AssetUtil.drawCenterInside(g2, sourceImage, targetRect);
            AssetUtil.drawEffects(g, tempImage, 0, 0, new Effect[]{new FillEffect(
              new GradientPaint(0, 0, new Color(0x919191), 0, imageRect.height,
                                new Color(0x828282))),
              new ShadowEffect(0, 1, 0, Color.WHITE, 0.10, true),});
        }

        g.dispose();
        g2.dispose();

        return outImage;
    }

    @Override
    public void generate(String category, Map<String, Map<String, BufferedImage>> categoryMap,
            GraphicGeneratorContext context, Options baseOptions, String name) {
        NotificationOptions options = (NotificationOptions) baseOptions;
        if (options.minSdk < 9) {
            options.version = Version.OLDER;
            super.generate(options.version.getDisplayName(), categoryMap, context, options, name);
        }
        if (options.minSdk < 11) {
            options.version = Version.V9;
            super.generate(options.version.getDisplayName(), categoryMap, context, options, name);
        }
        options.version = Version.V11;
        super.generate(options.minSdk < 11 ? options.version.getDisplayName() : null,
                categoryMap, context, options, name);
    }


    @NonNull
    @Override
    protected String getIconFolder(@NonNull Options options) {
        String folder = super.getIconFolder(options);
        Version version = ((NotificationOptions) options).version;
        if (version == Version.V11 && options.minSdk < 11) {
            return folder + "-v11"; //$NON-NLS-1$
        } else if (version == Version.V9 && options.minSdk < 9) {
            return folder + "-v9"; //$NON-NLS-1$
        } else {
            return folder;
        }
    }

    /**
     * Options specific to generating notification icons
     */
    public static class NotificationOptions extends GraphicGenerator.Options {
        /**
         * The version of the icon to generate - different styles are used for different
         * versions of Android
         */
        public Version version = Version.V9;
    }

    /**
     * The version of the icon to generate - different styles are used for different
     * versions of Android
     */
    public enum Version {
        /** Icon style used for -v9 and -v10 */
        V9("V9"),

        /** Icon style used for -v11 (Honeycomb) and later */
        V11("V11"),

        /** Icon style used for versions older than v9 */
        OLDER("Other");

        private final String mDisplayName;

        Version(String displayName) {
            mDisplayName = displayName;
        }

        /**
         * Returns the display name for this version, typically shown as a
         * category
         *
         * @return the display name, never null
         */
        public String getDisplayName() {
            return mDisplayName;
        }
    }
}
