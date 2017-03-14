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
package com.android.tools.idea.rendering;

import com.android.tools.adtui.imagediff.ImageDiffUtil;
import org.jetbrains.android.AndroidTestCase;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;

import static com.google.common.truth.Truth.assertThat;

public class GutterIconCacheTest extends AndroidTestCase {

  public void testCreateBitmapIcon_bigEnough() throws Exception {
    BufferedImage input = ImageIO.read(new File(getTestDataPath(), "render/imageutils/actual.png"));
    // Sanity check.
    assertThat(input.getHeight()).isGreaterThan(GutterIconCache.MAX_HEIGHT);
    assertThat(input.getWidth()).isGreaterThan(GutterIconCache.MAX_WIDTH);

    Icon icon = GutterIconCache.createBitmapIcon(input);
    assertThat(icon).isNotNull();
    assertThat(icon.getIconWidth()).isAtMost(GutterIconCache.MAX_WIDTH);
    assertThat(icon.getIconHeight()).isAtMost(GutterIconCache.MAX_HEIGHT);
  }

  public void testCreateBitmapIcon_smallAlready() throws Exception {
    BufferedImage input = ImageIO.read(new File(getTestDataPath(), "annotator/ic_tick_thumbnail.png"));
    // Sanity check.
    assertThat(input.getHeight()).isAtMost(GutterIconCache.MAX_HEIGHT);
    assertThat(input.getWidth()).isAtMost(GutterIconCache.MAX_WIDTH);

    Icon icon = GutterIconCache.createBitmapIcon(input);
    assertThat(icon).isNotNull();
    BufferedImage output = TestRenderingUtils.getImageFromIcon(icon);

    // Input and output should be identical.
    ImageDiffUtil.assertImageSimilar(getName(), input, output, 0);
  }
}
