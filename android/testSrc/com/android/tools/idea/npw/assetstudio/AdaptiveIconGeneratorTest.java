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
import com.android.resources.Density;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.jetbrains.android.AndroidTestBase.getTestDataPath;

@RunWith(JUnit4.class)
public class AdaptiveIconGeneratorTest {

  @SuppressWarnings("SameParameterValue")
  private static void checkGraphic(
      int imageCount,
      @NonNull String baseName,
      @NonNull GraphicGenerator.Shape legacyShape,
      boolean generateLegacy,
      @Nullable String backgroundImageResourceName,
      int background,
      boolean generateWebIcon)
      throws IOException {
    AdaptiveIconGenerator.AdaptiveIconOptions options = new AdaptiveIconGenerator.AdaptiveIconOptions();
    options.previewShape = AdaptiveIconGenerator.PreviewShape.NONE;
    options.generateLegacyIcon = generateLegacy;
    options.backgroundColor = background;
    options.generatePreviewIcons = generateWebIcon;
    options.generateOutputIcons = generateWebIcon;
    options.generateWebIcon = generateWebIcon;
    options.previewDensity = Density.XHIGH;
    options.legacyIconShape = legacyShape;
    options.foregroundLayerName = baseName + "_fore";
    options.backgroundLayerName = baseName + "_back";
    if (backgroundImageResourceName != null) {
      try (InputStream is = new FileInputStream(new File(getTestDataPath(), backgroundImageResourceName))) {
        options.backgroundImage = ImageIO.read(is);
      }
    }

    AdaptiveIconGenerator generator = new AdaptiveIconGenerator();
    BitmapGeneratorTests.checkGraphic(
        imageCount,
        "adaptive" + File.separator + baseName,
        baseName,
        generator,
        options,
        0.6f);
  }

  @Test
  public void testAdaptive_simpleCircle() throws Exception {
    int PREVIEW_IMAGE_COUNT = 7;
    int DENSITY_COUNT = 5;
    int IMAGES_PER_DENSITY = 3;
    checkGraphic(
        IMAGES_PER_DENSITY * DENSITY_COUNT + PREVIEW_IMAGE_COUNT + 2 /*web*/,
        "red_simple_circle",
        GraphicGenerator.Shape.CIRCLE,
        true,
        null,
        0xFF0000,
        true);
  }

  @Test
  public void testAdaptive_simpleBackground() throws Exception {
    int PREVIEW_IMAGE_COUNT = 7;
    int DENSITY_COUNT = 5;
    int IMAGES_PER_DENSITY = 4;
    checkGraphic(
        IMAGES_PER_DENSITY * DENSITY_COUNT + PREVIEW_IMAGE_COUNT + 2 /*web*/,
        "simple_background",
        GraphicGenerator.Shape.SQUARE,
        true,
        "images/adaptive/input_assets/ic_image_back.png",
        0xFF0000,
        true);
  }
}
