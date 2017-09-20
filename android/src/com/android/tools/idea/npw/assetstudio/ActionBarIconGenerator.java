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

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Generate icons for the action bar
 */
public class ActionBarIconGenerator extends GraphicGenerator {

    /** Creates a new {@link ActionBarIconGenerator}. */
    public ActionBarIconGenerator() {
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
        ActionBarOptions actionBarOptions = (ActionBarOptions) options;
        Rectangle iconSizeMdpi = new Rectangle(0, 0, 32, 32);
        Rectangle targetRectMdpi = actionBarOptions.sourceIsClipart
                ? new Rectangle(0, 0, 32, 32)
                : new Rectangle(4, 4, 24, 24);
        final float scaleFactor = GraphicGenerator.getMdpiScaleFactor(options.density);
        Rectangle imageRect = AssetUtil.scaleRectangle(iconSizeMdpi, scaleFactor);
        Rectangle targetRect = AssetUtil.scaleRectangle(targetRectMdpi, scaleFactor);
        BufferedImage outImage = AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
        Graphics2D g = (Graphics2D) outImage.getGraphics();

        BufferedImage tempImage = AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
        Graphics2D g2 = (Graphics2D)tempImage.getGraphics();
        AssetUtil.drawCenterInside(g2, sourceImage, targetRect);

        if (actionBarOptions.theme == Theme.CUSTOM) {
            AssetUtil.drawEffects(g, tempImage, 0, 0, new Effect[]{
              new FillEffect(new Color(actionBarOptions.customThemeColor), 0.8),});
        } else if (actionBarOptions.theme == Theme.HOLO_LIGHT) {
            AssetUtil.drawEffects(g, tempImage, 0, 0,
                                  new Effect[]{new FillEffect(new Color(0x333333), 0.6),});
        } else {
            assert actionBarOptions.theme == Theme.HOLO_DARK;
            AssetUtil.drawEffects(g, tempImage, 0, 0,
                                  new Effect[]{new FillEffect(new Color(0xFFFFFF), 0.8)});
        }

        g.dispose();
        g2.dispose();

        return outImage;
    }

    /** Options specific to generating action bar icons */
    public static class ActionBarOptions extends GraphicGenerator.Options {
        /** The theme to generate icons for */
        public Theme theme = Theme.HOLO_LIGHT;

        /** Whether or not the source image is a clipart source */
        public boolean sourceIsClipart = false;

        /** Custom color for use with the custom theme */
        public int customThemeColor = 0;
    }

    /** The themes to generate action bar icons for */
    public enum Theme {
        /** Theme.Holo - a dark (and default) version of the Honeycomb theme */
        HOLO_DARK,

        /** Theme.HoloLight - a light version of the Honeycomb theme */
        HOLO_LIGHT,

        /** Theme.Custom - custom colors */
        CUSTOM
    }
}
