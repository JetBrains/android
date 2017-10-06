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

/**
 * A {@link GraphicGenerator} that generates Android "menu" icons.
 */
public class MenuIconGenerator extends GraphicGenerator {
    /** Creates a menu icon generator */
    public MenuIconGenerator() {
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
        Rectangle imageSizeHdpi = new Rectangle(0, 0, 48, 48);
        Rectangle targetRectHdpi = new Rectangle(8, 8, 32, 32);
        float scaleFactor = GraphicGenerator.getMdpiScaleFactor(options.density);
        Rectangle imageRect = AssetUtil.scaleRectangle(imageSizeHdpi, scaleFactor);
        Rectangle targetRect = AssetUtil.scaleRectangle(targetRectHdpi, scaleFactor);

        BufferedImage outImage = AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
        Graphics2D g = (Graphics2D) outImage.getGraphics();

        BufferedImage tempImage = AssetUtil.newArgbBufferedImage(imageRect.width, imageRect.height);
        Graphics2D g2 = (Graphics2D) tempImage.getGraphics();
        AssetUtil.drawCenterInside(g2, sourceImage, targetRect);

        AssetUtil.drawEffects(g, tempImage, 0, 0, new Effect[]{new FillEffect(
          new GradientPaint(0, 0, new Color(0xa3a3a3), 0, imageRect.height, new Color(0x787878))),
          new ShadowEffect(0, 2 * scaleFactor, 2 * scaleFactor, Color.BLACK, 0.2, true),
          new ShadowEffect(0, 1, 0, Color.BLACK, 0.35, true),
          new ShadowEffect(0, -1, 0, Color.WHITE, 0.35, true),});

        g.dispose();
        g2.dispose();

        return outImage;
    }
}
