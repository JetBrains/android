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
 * Generate icons for tabs
 */
public class TabIconGenerator extends GraphicGenerator {
    /** Creates a new {@link TabIconGenerator} */
    public TabIconGenerator() {
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
        Rectangle iconSizeMdpi = new Rectangle(0, 0, 32, 32);
        Rectangle targetRectMdpi = new Rectangle(2, 2, 28, 28);
        final float scaleFactor = GraphicGenerator.getMdpiScaleFactor(options.density);
        Rectangle imageRect = AssetUtil.scaleRectangle(iconSizeMdpi, scaleFactor);
        Rectangle targetRect = AssetUtil.scaleRectangle(targetRectMdpi, scaleFactor);
        BufferedImage outImage = AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
        Graphics2D g = (Graphics2D) outImage.getGraphics();

        BufferedImage tempImage = AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
        Graphics2D g2 = (Graphics2D) tempImage.getGraphics();
        AssetUtil.drawCenterInside(g2, sourceImage, targetRect);

        TabOptions tabOptions = (TabOptions) options;
        if (tabOptions.selected) {
            if (tabOptions.oldStyle) {
                AssetUtil.drawEffects(g, tempImage, 0, 0, new Effect[]{new FillEffect(
                  new GradientPaint(0, 0, new Color(0xa3a3a3), 0, imageRect.height,
                                    new Color(0x787878))),
                  new ShadowEffect(0, 2 * scaleFactor, 2 * scaleFactor, Color.BLACK, 0.2, true),
                  new ShadowEffect(0, 1, 0, Color.BLACK, 0.35, true),
                  new ShadowEffect(0, -1, 0, Color.WHITE, 0.35, true),});
            } else {
                AssetUtil.drawEffects(g, tempImage, 0, 0, new Effect[]{new FillEffect(Color.WHITE),
                  new ShadowEffect(0, 0, 3 * scaleFactor, Color.BLACK, 0.25, false),});
            }
        } else {
            // Unselected
            if (tabOptions.oldStyle) {
                AssetUtil.drawEffects(g, tempImage, 0, 0, new Effect[]{new FillEffect(
                  new GradientPaint(0, 0.25f * imageRect.height, new Color(0xf9f9f9), 0,
                                    imageRect.height, new Color(0xdfdfdf))),
                  new ShadowEffect(0, 2 * scaleFactor, 2 * scaleFactor, Color.BLACK, 0.1, true),
                  new ShadowEffect(0, 1, 0, Color.BLACK, 0.35, true),
                  new ShadowEffect(0, -1, 0, Color.WHITE, 0.35, true),});
            } else {
                AssetUtil.drawEffects(g, tempImage, 0, 0,
                                      new Effect[]{new FillEffect(new Color(0x808080)),});
            }
        }

        g.dispose();
        g2.dispose();

        return outImage;
    }

    @Override
    public void generate(String category, Map<String, Map<String, BufferedImage>> categoryMap,
            GraphicGeneratorContext context, Options baseOptions, String name) {
        TabOptions options = (TabOptions) baseOptions;
        // Generate all permutations of tabOptions.selected and tabOptions.oldStyle
        options.selected = true;
        options.oldStyle = false;

        String selectedLabelV5 = "Selected (v5+)";
        String unselectedLabelV5 = "Unselected (v5+)";
        String selectedLabel = "Selected";
        String unselectedLabel = "Unselected";

        boolean generateOldStyle = options.minSdk < 5;
        if (generateOldStyle) {
            options.oldStyle = true;
            options.selected = true;
            super.generate(selectedLabel, categoryMap, context, options, name);
            options.selected = false;
            super.generate(unselectedLabel, categoryMap, context, options, name);
        }

        options.oldStyle = false;
        options.selected = true;
        super.generate(generateOldStyle ? unselectedLabelV5 : unselectedLabel,
                categoryMap, context, options, name);
        options.selected = false;
        super.generate(generateOldStyle ? selectedLabelV5 : selectedLabel,
                categoryMap, context, options, name);
    }

    @NonNull
    @Override
    protected String getIconFolder(@NonNull Options options) {
        String folder = super.getIconFolder(options);

        TabOptions tabOptions = (TabOptions) options;
        if (tabOptions.oldStyle || options.minSdk >= 5) {
            return folder;
        } else {
            return folder + "-v5"; //$NON-NLS-1$
        }
    }

    @NonNull
    @Override
    protected String getIconName(@NonNull Options options, @NonNull String name) {
        TabOptions tabOptions = (TabOptions) options;
        if (tabOptions.selected) {
            return name + "_selected.png"; //$NON-NLS-1$
        } else {
            return name + "_unselected.png"; //$NON-NLS-1$
        }
    }

    /** Options specific to generating tab icons */
    public static class TabOptions extends GraphicGenerator.Options {
        /** Generate icon in the style used prior to v5 */
        public boolean oldStyle;
        /** Generate "selected" icon if true, and "unselected" icon if false */
        public boolean selected = true;
    }
}
